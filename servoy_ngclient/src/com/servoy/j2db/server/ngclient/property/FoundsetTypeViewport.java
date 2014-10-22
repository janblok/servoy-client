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

import com.servoy.j2db.dataprocessing.FoundSetEvent;
import com.servoy.j2db.dataprocessing.IFoundSetEventListener;
import com.servoy.j2db.dataprocessing.IFoundSetInternal;

/**
 * Holds the client used viewport info for this foundset.
 *
 * @author acostescu
 */
public class FoundsetTypeViewport
{

	protected int startIndex = 0;
	protected int size = 0;
	protected FoundsetTypeChangeMonitor changeMonitor;
	protected IFoundSetInternal foundset;
	protected IFoundSetEventListener foundsetEventListener;

	/**
	 * Creates a new viewport object.
	 * @param changeMonitor change monitor can be used to announce changes in viewport (bounds).
	 */
	public FoundsetTypeViewport(FoundsetTypeChangeMonitor changeMonitor)
	{
		this.changeMonitor = changeMonitor;
	}

	protected void setFoundset(IFoundSetInternal newFoundset)
	{
		if (foundset != null) foundset.removeFoundSetEventListener(getFoundsetEventListener());
		if (newFoundset != null) newFoundset.addFoundSetEventListener(getFoundsetEventListener());
		this.foundset = newFoundset;

		// just start fresh - 0 / 0
		// should we try to keep current viewPort indexes on the new fooundset here instead? (so just call correct)
		setBounds(0, 0);
	}

	public int getStartIndex()
	{
		return startIndex;
	}

	public int getSize()
	{
		return size;
	}

	/**
	 * The viewPort needs to change to the new startIndex/size.
	 */
	public void setBounds(int startIndex, int size)
	{
		int oldStartIndex = this.startIndex;
		int oldSize = this.size;

		this.startIndex = startIndex;
		this.size = size;

		correctViewportBoundsIfNeededInternal();

		if (oldStartIndex != startIndex || oldSize != size) changeMonitor.viewPortCompletelyChanged();
	}


	/**
	 * Extends the viewport - useful for sending more records to client without re-sending the whole viewport.
	 *
	 * @param positiveOrNegativeRecordNo the number of records to extend the viewPort with. A positive value
	 * will append records at the end of the viewPort and a negative one will prepend (add to the beginning).
	 */
	public void loadExtraRecords(int positiveOrNegativeRecordNo)
	{
		int oldStartIndex = this.startIndex;
		int oldSize = this.size;

		correctViewportBoundsIfNeededInternal();
		if (positiveOrNegativeRecordNo >= 0)
		{
			this.size = Math.min(size + positiveOrNegativeRecordNo, foundset.getSize());
			changeMonitor.recordsInserted(this.startIndex + oldSize, this.startIndex + this.size - 1, this, true);
		}
		else
		{
			this.startIndex = Math.max(positiveOrNegativeRecordNo + startIndex, 0);
			changeMonitor.recordsInserted(this.startIndex, oldStartIndex - 1, this, true);
		}


		if (oldStartIndex != startIndex || oldSize != size) changeMonitor.viewPortBoundsOnlyChanged();
	}

//
//	/**
//	 * If client requested invalid bounds or due to foundset changes the previous bounds
//	 * are no longer valid, correct them.
//	 */
//	public void correctViewportBoundsIfNeeded()
//	{
//		int oldStartIndex = startIndex;
//		int oldSize = size;
//
//		correctViewportBoundsIfNeededInternal();
//
//		if (oldStartIndex != startIndex || oldSize != size) changeMonitor.viewPortCompletelyChanged();
//	}

	/**
	 * Corrects bounds without firing any change notifications.
	 */
	protected void correctViewportBoundsIfNeededInternal()
	{
		if (foundset != null)
		{
			size = Math.max(0, Math.min(size, foundset.getSize() - startIndex));
			startIndex = Math.max(0, Math.min(startIndex, foundset.getSize() - 1));
		}
		else
		{
			startIndex = 0;
			size = 0;
		}
	}

	protected IFoundSetEventListener getFoundsetEventListener()
	{
		if (foundsetEventListener == null)
		{
			foundsetEventListener = new IFoundSetEventListener()
			{
				@Override
				public void foundSetChanged(FoundSetEvent event)
				{
					if (event.getType() == FoundSetEvent.FIND_MODE_CHANGE) changeMonitor.findModeChanged();
					else if (event.getType() == FoundSetEvent.FOUNDSET_INVALIDATED) changeMonitor.foundsetInvalidated();
					else if (event.getType() == FoundSetEvent.CONTENTS_CHANGED)
					{
						// partial change only push the changes.
						if (event.getChangeType() == FoundSetEvent.CHANGE_DELETE)
						{
							changeMonitor.recordsDeleted(event.getFirstRow(), event.getLastRow(), FoundsetTypeViewport.this);
						}
						else if (event.getChangeType() == FoundSetEvent.CHANGE_INSERT)
						{
							changeMonitor.recordsInserted(event.getFirstRow(), event.getLastRow(), FoundsetTypeViewport.this, false); // true - slide if first so that viewPort follows the first record
						}
						else if (event.getChangeType() == FoundSetEvent.CHANGE_UPDATE)
						{
							changeMonitor.recordsUpdated(event.getFirstRow(), event.getLastRow(), foundset.getSize(), FoundsetTypeViewport.this);
						}
					}
				}
			};
		}
		return foundsetEventListener;
	}

	protected void dispose()
	{
		if (foundset != null) foundset.removeFoundSetEventListener(getFoundsetEventListener());
	}

	/**
	 * Slides the viewPort (startIndex) to higher or lower values and then corrects viewPort bounds (if they became invalid due to foundset changes).<br/>
	 * Call this only when the viewPort data remains the same or when viewPort data will be updated through granular add/remove operations.
	 *
	 * @param delta can be a positive or negative value.
	 */
	protected void slideAndCorrect(int delta)
	{
		int oldStartIndex = startIndex;
		int oldSize = size;

		startIndex += delta;
		correctViewportBoundsIfNeededInternal();

		if (oldStartIndex != startIndex || oldSize != size) changeMonitor.viewPortBoundsOnlyChanged();
	}

}
