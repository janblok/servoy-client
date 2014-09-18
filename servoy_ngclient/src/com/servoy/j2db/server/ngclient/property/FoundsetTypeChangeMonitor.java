/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2014 Servoy BV

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU Affero General Public License as published by the Free
 Software Foundation; either version 3 of the License, or (at your option) any
 later version.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License along
 with this program; if not, see http://www.gnu.org/licenses or write to the Free
 Software Foundation,Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301
 */

package com.servoy.j2db.server.ngclient.property;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.sablo.IChangeListener;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.property.types.AggregatedPropertyType;
import org.sablo.websocket.TypedData;

import com.servoy.j2db.server.ngclient.WebGridFormUI.RowData;

/**
 * This class is responsible for keeping track of what changes need to be sent to the client (whole thing, selection changes, viewport idx/size change, row data changes...)
 *
 * @author acostescu
 */
public class FoundsetTypeChangeMonitor
{
	/**
	 * The whole foundset property needs to get sent to the client.
	 */
	protected static final int SEND_ALL = 0b00001;
	/**
	 * Only the bounds of the viewPort changed, data is the same; for example records were added/removed before startIndex of viewPort,
	 * or even inside the viewPort but will be combined by incremental updates (adds/deletes).
	 */
	protected static final int SEND_VIEWPORT_BOUNDS = 0b00010;
	/**
	 * ViewPort bounds and data changed; for example client requested completely new viewPort bounds.
	 */
	protected static final int SEND_WHOLE_VIEWPORT = 0b00100;
	/**
	 * Foundset size changed (add/remove of records).
	 */
	protected static final int SEND_FOUNDSET_SIZE = 0b01000;

	protected static final int SEND_SELECTED_INDEXES = 0b10000;

	protected IChangeListener changeNotifier;
	protected int changeFlags = 0;
	protected List<RowData> viewPortChanges = new ArrayList<>();
	protected FoundsetTypeSabloValue propertyValue; // TODO when we implement merging foundset events based on indexes, data will no longer be needed and this member can be removed

	public FoundsetTypeChangeMonitor(FoundsetTypeSabloValue propertyValue)
	{
		this.propertyValue = propertyValue;
	}

	/**
	 * Called when the foundSet selection needs to be re-sent to client.
	 */
	public void selectionChanged()
	{
		if (!shouldSendAll())
		{
			int oldChangeFlags = changeFlags;
			changeFlags = changeFlags | SEND_SELECTED_INDEXES;
			if (oldChangeFlags != changeFlags) notifyChange();
		}
	}

	/**
	 * The foundset's size changed.
	 * This doesn't notify changes as this is probably part of a larger check which could result in more changes. Notification must be handled by caller.
	 */
	protected void foundSetSizeChanged()
	{
		if (!shouldSendAll())
		{
			changeFlags = changeFlags | SEND_FOUNDSET_SIZE;
		}
	}

	/**
	 * Called when the viewPort bounds need to be re-sent to client.<br/>
	 * Only the bounds of the viewPort changed, data is the same; for example records were added/removed before startIndex of viewPort.<br/><br/>
	 *
	 * This doesn't notify changes as this is probably part of a larger check which could result in more changes. Notification must be handled by caller.
	 */
	protected void viewPortBoundsOnlyChanged()
	{
		if (!shouldSendWholeViewPort() && !shouldSendAll())
		{
			changeFlags = changeFlags | SEND_VIEWPORT_BOUNDS;
		}
	}

	/**
	 * Called when viewPort bounds and data changed; for example client requested completely new viewport bounds.
	 */
	public void viewPortCompletelyChanged()
	{
		if (!shouldSendAll())
		{
			int oldChangeFlags = changeFlags;
			changeFlags = changeFlags | SEND_WHOLE_VIEWPORT;
			if (oldChangeFlags != changeFlags)
			{
				// clear all more granular changes as whole viewport will be sent
				changeFlags = changeFlags & (~SEND_VIEWPORT_BOUNDS); // clear flag
				viewPortChanges.clear();
				notifyChange();
			}
		}
	}

	/**
	 * Called when all foundset info needs to be resent to client.
	 */
	public void allChanged()
	{
		int oldChangeFlags = changeFlags;
		changeFlags = SEND_ALL; // clears all others as well
		if (oldChangeFlags != changeFlags)
		{
			viewPortChanges.clear();
			notifyChange();
		}
	}

	/**
	 * Called for example when the used foundset instance changes (for example due to use of related foundset).
	 * In that case viewPort is set to 0, 0 (so that might have already triggered a notification), but also the server size can change then.
	 */
	public void newFoundsetSize()
	{
		int oldChangeFlags = changeFlags;
		foundSetSizeChanged();
		if (oldChangeFlags != changeFlags) notifyChange();
	}

	/**
	 * Called when the find mode changes on this foundset.
	 */
	public void findModeChanged()
	{
		allChanged();
	}

	/**
	 * Called when the foundset is invalidated.
	 */
	public void foundsetInvalidated()
	{
		allChanged();
	}

	/**
	 * Called when the dataProviders that this foundset type provides changed.
	 */
	public void dataProvidersChanged()
	{
		// this normally happens only before initial send of initial form data so it isn't very useful; will we allow dataProviders to change later on?
		if (propertyValue.viewPort.size > 0) allChanged();
	}

	public void recordsDeleted(int firstRow, int lastRow, FoundsetTypeViewport viewPort)
	{
		int oldChangeFlags = changeFlags;
		boolean viewPortRecordChangesUpdated = false;

		if (lastRow - firstRow >= 0) foundSetSizeChanged();
		if (!shouldSendAll() && !shouldSendWholeViewPort())
		{
			int viewPortEndIdx = viewPort.getStartIndex() + viewPort.getSize() - 1;

			int slideBy;
			if (firstRow < viewPort.getStartIndex())
			{
				// this will adjust the viewPort startIndex (and size if needed)
				slideBy = firstRow - Math.min(viewPort.getStartIndex(), lastRow + 1);
			}
			else
			{
				// this will adjust the viewPort size if needed (not enough records to insert in the viewPort to replace deleted ones)
				slideBy = 0;
			}

			if (belongsToInterval(firstRow, viewPort.getStartIndex(), viewPortEndIdx) || belongsToInterval(lastRow, viewPort.getStartIndex(), viewPortEndIdx))
			{
				// first row to be deleted inside current viewPort
				int firstRowDeletedInViewport = Math.max(viewPort.getStartIndex(), firstRow);
				int lastRowDeletedInViewport = Math.min(viewPortEndIdx, lastRow);
				int relativeFirstRow = firstRowDeletedInViewport - viewPort.getStartIndex();
				// number of deletes from current viewPort
				int relativeLastRow = lastRowDeletedInViewport - viewPort.getStartIndex();
				int numberOfDeletes = lastRowDeletedInViewport - firstRowDeletedInViewport + 1;

				// adjust viewPort bounds if necessary
//				int oldViewPortStart = viewPort.getStartIndex();
				int oldViewPortSize = viewPort.getSize();

				// TODO merge changes with previous ones without keeping any actual data (indexes kept in a way should be enough) - implementation started below
//				// ok, viewPort bounds are updated; update existing recordChange data if needed; we are working here a lot with viewPort relative indexes (both client side and server side ones)
//				ListIterator<RecordChangeDescriptor> iterator = viewPortRecordChanges.listIterator();
//				int browserViewPortIdxDelta = 0; // delta between the current client side viewPort data relative "i" index and the old server viewPort relative "i" index
//				int toBeDeleted = relativeFirstRow;
//				while (iterator.hasNext())
//				{
//					RecordChangeDescriptor recordChange = iterator.next();
//					while (toBeDeleted <= relativeLastRow && toBeDeleted + browserViewPortIdxDelta < recordChange.relativeIndex)
//					{
//						// record deleted before previous Add/Remove/Update operation; add before
//						iterator.add(new RecordChangeDescriptor(RecordChangeDescriptor.Types.REMOVE_FROM_VIEWPORT, browserViewPortIdxDelta + toBeDeleted));
//						if (toBeDeleted + browserViewPortIdxDelta >= viewPort.getSize())
//						{
//
//						}
//						toBeDeleted++;
//					}
//
//					switch (recordChange.type)
//					{
//						case REMOVE_FROM_VIEWPORT :
//							browserViewPortIdxDelta++;
//							break;
//						case ADD_TO_VIEWPORT :
//							// TODO
//							break;
//						case CHANGE :
//							// TODO
//							break;
//					}
//				}
//				while (toBeDeleted <= relativeLastRow && )
//				{
//					// record deleted before previous Add/Remove/Update operation; add before
//					iterator.add(new RecordChangeDescriptor(RecordChangeDescriptor.Types.REMOVE_FROM_VIEWPORT, browserViewPortIdxDelta + toBeDeleted));
//					toBeDeleted++;
//				}

				viewPort.slideAndCorrect(slideBy);
				viewPortEndIdx = viewPort.getStartIndex() + viewPort.getSize() - 1; // update

				// add new records if available
				// we need to replace same amount of records in current viewPort; append rows if available
				viewPortChanges.add(new RowData(getRowData(viewPort.getStartIndex() + oldViewPortSize - numberOfDeletes, viewPortEndIdx), relativeFirstRow,
					relativeLastRow, RowData.DELETE));
				viewPortRecordChangesUpdated = true;
			}
			else if (slideBy != 0)
			{
				viewPort.slideAndCorrect(slideBy);
			}
		}
		if (oldChangeFlags != changeFlags || viewPortRecordChangesUpdated) notifyChange();
	}

	/**
	 * Deals with new records being inserted into the foundset.
	 * @param firstRow the first row of the insertion.
	 * @param lastRow the last row of the insertion.
	 * @param viewPort the current viewPort.
	 * @param viewPortExpandOnly if false, and records were inserted exactly at the viewPort start index, it will just slide the viewport;
	 * if false, and records were requested for (so not actually inserted in the foundset, just a viewportExpand is happening) exactly
	 * at the viewPort start index, it will actually insert the new records in the view port.
	 */
	public void recordsInserted(int firstRow, int lastRow, FoundsetTypeViewport viewPort, boolean viewPortExpandOnly)
	{
		int oldChangeFlags = changeFlags;
		boolean viewPortRecordChangesUpdated = false;

		if (!viewPortExpandOnly && lastRow - firstRow >= 0) foundSetSizeChanged();
		if (!shouldSendAll() && !shouldSendWholeViewPort())
		{
			int viewPortEndIdx = viewPort.getStartIndex() + viewPort.getSize() - 1;
			if (viewPort.getStartIndex() < (firstRow + (viewPortExpandOnly ? 1 : 0)) && firstRow <= viewPortEndIdx)
			{
				int lastViewPortInsert = Math.min(lastRow, viewPortEndIdx);

				// add records that were inserted in viewPort
				viewPortChanges.add(new RowData(getRowData(firstRow, lastViewPortInsert), firstRow - viewPort.getStartIndex(), lastViewPortInsert -
					viewPort.getStartIndex(), RowData.INSERT));
				viewPortRecordChangesUpdated = true;
			}
			else if (viewPort.getStartIndex() >= firstRow)
			{
				viewPort.slideAndCorrect(lastRow - firstRow + 1);
			}
		}

		if (oldChangeFlags != changeFlags || viewPortRecordChangesUpdated) notifyChange();
	}

	public void recordsUpdated(int firstRow, int lastRow, int foundSetSize, FoundsetTypeViewport viewPort)
	{
		if (firstRow == 0 && lastRow == foundSetSize - 1)
		{
			if (viewPort.getSize() > 0) viewPortCompletelyChanged();
		}
		else
		{
			int oldChangeFlags = changeFlags;
			boolean viewPortRecordChangesUpdated = false;
			if (!shouldSendAll() && !shouldSendWholeViewPort())
			{
				// get the rows that are changed.
				int firstViewPortIndex = Math.max(viewPort.getStartIndex(), firstRow);
				int lastViewPortIndex = Math.min(viewPort.getStartIndex() + viewPort.getSize() - 1, lastRow);
				if (firstViewPortIndex <= lastViewPortIndex)
				{
					viewPortChanges.add(new RowData(getRowData(firstViewPortIndex, lastViewPortIndex), firstViewPortIndex - viewPort.getStartIndex(),
						lastViewPortIndex - viewPort.getStartIndex(), RowData.CHANGE));
					viewPortRecordChangesUpdated = true;
				}
			}
			if (oldChangeFlags != changeFlags || viewPortRecordChangesUpdated) notifyChange();
		}
	}

	protected boolean belongsToInterval(int x, int intervalStartInclusive, int intervalEndInclusive)
	{
		return intervalStartInclusive <= x && x <= intervalEndInclusive;
	}

	protected TypedData<List<Map<String, Object>>> getRowData(int startIndex, int endIndex)
	{
		List<Map<String, Object>> rows = new ArrayList<>();
		PropertyDescription rowTypes = AggregatedPropertyType.newAggregatedProperty();
		for (int i = startIndex; i <= endIndex; i++)
		{
			TypedData<Map<String, Object>> tmp = propertyValue.getRowData(i);
			rows.add(tmp.content);
			if (tmp.contentType != null) rowTypes.putProperty(String.valueOf(rows.size() - 1), tmp.contentType);
		}
		if (!rowTypes.hasChildProperties()) rowTypes = null;
		return new TypedData<>(rows, rowTypes);
	}

	public boolean shouldSendAll()
	{
		return (changeFlags & SEND_ALL) != 0;
	}

	public boolean shouldSendSelectedIndexes()
	{
		return (changeFlags & SEND_SELECTED_INDEXES) != 0;
	}

	public boolean shouldSendFoundsetSize()
	{
		return (changeFlags & SEND_FOUNDSET_SIZE) != 0;
	}

	public boolean shouldSendViewPortBounds()
	{
		return (changeFlags & SEND_VIEWPORT_BOUNDS) != 0;
	}

	public boolean shouldSendWholeViewPort()
	{
		return (changeFlags & SEND_WHOLE_VIEWPORT) != 0;
	}

	public List<RowData> getViewPortChanges()
	{
		return viewPortChanges;
	}

	/**
	 * Registers the change notifier; this notifier is to be used to fire property change notifications.
	 * @param changeNotifier the object that should be notified when this property needs to send updates to client.
	 */
	public void setChangeNotifier(IChangeListener changeNotifier)
	{
		this.changeNotifier = changeNotifier;
		if (hasChanges()) changeNotifier.valueChanged();
	}

	public boolean hasChanges()
	{
		return shouldSendAll() || shouldSendFoundsetSize() || shouldSendSelectedIndexes() || shouldSendViewPortBounds() || shouldSendWholeViewPort() ||
			getViewPortChanges().size() > 0;
	}

	public void clearChanges()
	{
		changeFlags = 0;
		viewPortChanges.clear();
	}

	protected void notifyChange()
	{
		if (changeNotifier != null) changeNotifier.valueChanged();
	}

//	protected static class RecordChangeDescriptor implements JSONWritable
//	{
//
//		public static enum Types
//		{
//			CHANGE(0), ADD_TO_VIEWPORT(1), REMOVE_FROM_VIEWPORT(2);
//
//			public final int v;
//
//			private Types(int v)
//			{
//				this.v = v;
//			}
//		};
//
//		private final int relativeIndex;
//		private final Types type;
//
//		/**
//		 * @param type one of {@link #CHANGE}, {@link #ADD_TO_VIEWPORT} or {@link #REMOVE_FROM_VIEWPORT}
//		 * @param relativeIndex viewPort relative index of the change.
//		 */
//		public RecordChangeDescriptor(Types type, int relativeIndex)
//		{
//			this.type = type;
//			this.relativeIndex = relativeIndex;
//		}
//
//		public Map<String, Object> toMap()
//		{
//			Map<String, Object> retValue = new HashMap<>();
//			retValue.put("relativeIndex", Integer.valueOf(relativeIndex));
//			retValue.put("type", Integer.valueOf(type.v));
//			return retValue;
//		}
//	}

}
