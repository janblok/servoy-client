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

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;
import org.sablo.BaseWebObject;
import org.sablo.IChangeListener;
import org.sablo.WebComponent;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.property.DataConverterContext;
import org.sablo.specification.property.IWrapperType;
import org.sablo.specification.property.types.AggregatedPropertyType;
import org.sablo.websocket.TypedData;
import org.sablo.websocket.utils.DataConversion;
import org.sablo.websocket.utils.JSONUtils;

import com.servoy.j2db.dataprocessing.IFoundSetInternal;
import com.servoy.j2db.dataprocessing.IRecordInternal;
import com.servoy.j2db.dataprocessing.ISwingFoundSet;
import com.servoy.j2db.dataprocessing.PrototypeState;
import com.servoy.j2db.server.ngclient.DataAdapterList;
import com.servoy.j2db.server.ngclient.IDataAdapterList;
import com.servoy.j2db.server.ngclient.IWebFormUI;
import com.servoy.j2db.server.ngclient.utils.NGUtils;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.Pair;
import com.servoy.j2db.util.ServoyException;
import com.servoy.j2db.util.Utils;

/**
 * Value used at runtime as foundset type value proxy for multiple interested parties (browser, designtime, scripting).
 *
 * @author acostescu
 */
@SuppressWarnings("nls")
public class FoundsetTypeSabloValue implements IServoyAwarePropertyValue
{

	/**
	 * Column that is always automatically sent for each record in a foundset's viewport. It's value
	 * uniquely identifies that record.
	 */
	public static final String ROW_ID_COL_KEY = "_svyRowId";

	public static final String DATAPROVIDER_KEY = "dp";
	public static final String VALUE_KEY = "value";

	// START keys and values used in JSON
	public static final String UPDATE_PREFIX = "upd_"; // prefixes keys when only partial updates are send for them

	public static final String SERVER_SIZE = "serverSize";
	public static final String SELECTED_ROW_INDEXES = "selectedRowIndexes";
	public static final String MULTI_SELECT = "multiSelect";
	public static final String VIEW_PORT = "viewPort";
	public static final String START_INDEX = "startIndex";
	public static final String SIZE = "size";
	public static final String ROWS = "rows";
	public static final String NO_OP = "noOP";

	public static final String CONVERSIONS = "conversions";

	// END keys and values used in JSON

	protected FoundsetTypeViewport viewPort;
	protected IFoundSetInternal foundset;
	protected final Object designJSONValue;
	protected BaseWebObject webObject; // (the component)
	protected Set<String> dataProviders = new HashSet<>();
	protected String foundsetSelector;
	protected IDataAdapterList dataAdapterList;
	protected String propertyName;

	protected FoundsetTypeChangeMonitor changeMonitor;
	protected FoundsetPropertySelectionListener listSelectionListener;

	protected ViewportRowDataProvider rowDataProvider;


	public FoundsetTypeSabloValue(Object designJSONValue, String propertyName)
	{
		this.designJSONValue = designJSONValue;
		this.propertyName = propertyName;

		rowDataProvider = new ViewportRowDataProvider()
		{

			@Override
			protected void populateRowData(IRecordInternal record, Map<String, Object> data, PropertyDescription dataTypes)
			{
				FoundsetTypeSabloValue.this.populateRowData(record, data, dataTypes);
			}
		};
		changeMonitor = new FoundsetTypeChangeMonitor(this, rowDataProvider);
		viewPort = new FoundsetTypeViewport(changeMonitor);
		// nothing to do here; foundset is not initialized until it's attached to a component
	}

	public FoundsetTypeViewport getViewPort()
	{
		return viewPort;
	}

	public IFoundSetInternal getFoundset()
	{
		return foundset;
	}

	@Override
	public void attachToBaseObject(IChangeListener changeNotifier, BaseWebObject webObject)
	{
		this.webObject = webObject;
		dataAdapterList = null;
		changeMonitor.setChangeNotifier(changeNotifier);

		// get the foundset identifier, then the foundset itself
//		foundset: {
//			foundsetSelector: 'string',
//			dataProviders: 'dataprovider[]'
//		}
		try
		{
			JSONObject spec = (JSONObject)designJSONValue;

			// foundsetSelector as defined in component design XML.
			foundsetSelector = spec.optString("foundsetSelector");
			updateFoundset(null);

			JSONArray dataProvidersJSON = spec.optJSONArray("dataProviders");
			if (dataProvidersJSON != null)
			{
				Set<String> dataProvidersSet = new HashSet<>(dataProvidersJSON.length());
				for (int i = 0; i < dataProvidersJSON.length(); i++)
				{
					dataProvidersSet.add(dataProvidersJSON.getString(i));
				}
				includeDataProviders(dataProvidersSet);
			}
		}
		catch (JSONException e)
		{
			Debug.error(e);
		}
	}

	/**
	 * Find the foundset to be used based on the design value of "foundsetSelector".\n
	 * It can be either:
	 * <ul>
	 * 	<li>a related foundset based on the component's current record (as one would access it in scripting). Example: "customers_to_orders";</li>
	 * 	<li>the component's foundset (as if in scripting you would say 'foundset') - if foundsetSelector is not specified at design time or null;</li>
	 * 	<li>a new foundset based on the given datasource (as if you would do DatabaseManager.getFoundset(datasource) in scripting). Example: "db:/example_data/customers".</li>
	 * </ul>
	 *
	 * @param record the record this component is attached to; can be null. (form not linked to table or no records for example)
	 *
	 * @return true if the foundset was update, false otherwise.
	 */
	protected boolean updateFoundset(IRecordInternal record)
	{
		IFoundSetInternal newFoundset = null;
		if (record != null)
		{
			if ("".equals(foundsetSelector))
			{
				newFoundset = record.getParentFoundSet();
			}
			else
			{
				Object o = record.getValue(foundsetSelector);
				if (o instanceof IFoundSetInternal)
				{
					newFoundset = (IFoundSetInternal)o;
				}
			}
		}

		if (newFoundset == null && foundsetSelector != null)
		{
			try
			{
				// if we want to use this type on services as well we need extra code here to get the application
				newFoundset = (IFoundSetInternal)((WebComponent)webObject).findParent(IWebFormUI.class).getDataConverterContext().getApplication().getFoundSetManager().getFoundSet(
					foundsetSelector);
			}
			catch (ServoyException e)
			{
				if (record != null && !(record instanceof PrototypeState)) Debug.error(e);
			}
		}

		if (newFoundset != foundset)
		{
			int oldServerSize = (foundset != null ? foundset.getSize() : 0);
			int newServerSize = (newFoundset != null ? newFoundset.getSize() : 0);
			if (foundset instanceof ISwingFoundSet) ((ISwingFoundSet)foundset).getSelectionModel().removeListSelectionListener(getListSelectionListener());
			foundset = newFoundset;
			viewPort.setFoundset(foundset);
			if (oldServerSize != newServerSize) changeMonitor.newFoundsetSize();
			if (foundset instanceof ISwingFoundSet) ((ISwingFoundSet)foundset).getSelectionModel().addListSelectionListener(getListSelectionListener());

			return true;
		}
		return false;
	}

	protected FoundsetPropertySelectionListener getListSelectionListener()
	{
		if (listSelectionListener == null)
		{
			listSelectionListener = new FoundsetPropertySelectionListener(changeMonitor);
		}
		return listSelectionListener;
	}

	@Override
	public boolean pushRecord(IRecordInternal record)
	{
		return updateFoundset(record);
	}

	@Override
	public void detach()
	{
		viewPort.dispose();
		if (foundset instanceof ISwingFoundSet) ((ISwingFoundSet)foundset).getSelectionModel().removeListSelectionListener(getListSelectionListener());
	}

	public JSONWriter toJSON(JSONWriter destinationJSON, DataConversion conversionMarkers) throws JSONException
	{
		// TODO conversion markers should never be null I think, but it did happen (due to JSONUtils.toJSONValue(JSONWriter writer, Object value, IForJsonConverter forJsonConverter, ConversionLocation toDestinationType); will create a case for that
		if (conversionMarkers != null) conversionMarkers.convert(FoundsetPropertyType.TYPE_NAME); // so that the client knows it must use the custom client side JS for what JSON it gets

		destinationJSON.object();
		destinationJSON.key(SERVER_SIZE).value(foundset != null ? foundset.getSize() : 0);
		destinationJSON.key(SELECTED_ROW_INDEXES);
		addSelectedIndexes(destinationJSON);
		destinationJSON.key(MULTI_SELECT).value(foundset != null ? foundset.isMultiSelect() : false); // TODO listener and granular changes for this as well?

		// viewPort
		destinationJSON.key(VIEW_PORT);
		addViewPort(destinationJSON);
		// end viewPort

		destinationJSON.endObject();
		changeMonitor.clearChanges();
		return destinationJSON;
	}

	protected void addViewPort(JSONWriter destinationJSON) throws JSONException
	{
		destinationJSON.object();
		addViewPortBounds(destinationJSON);
//		rows: [
//	         	{ _svyRowId: 'someRowIdHASH1', nameColumn: "Bubu" },
//	         	{ _svyRowId: 'someRowIdHASH2', nameColumn: "Yogy" },
//				(...)
//	    ]
		if (foundset != null)
		{
			TypedData<List<Map<String, Object>>> rowsArray = rowDataProvider.getRowData(viewPort.getStartIndex(), viewPort.getStartIndex() +
				viewPort.getSize() - 1, foundset);

			Map<String, Object> rows = new HashMap<>();
			PropertyDescription rowTypes = null;
			rows.put(ROWS, rowsArray.content);
			if (rowsArray.contentType != null && rowsArray.contentType.hasChildProperties())
			{
				rowTypes = AggregatedPropertyType.newAggregatedProperty();
				rowTypes.putProperty(ROWS, rowsArray.contentType);
			}
			// convert for websocket traffic (for example Date objects will turn into long)
			JSONUtils.writeDataWithConversions(destinationJSON, rows, rowTypes);
		}
		else
		{
			destinationJSON.key(ROWS).array().endArray();
		}
		destinationJSON.endObject();
	}

	/**
	 * Dumps selected indexes to JSON.
	 */
	protected void addSelectedIndexes(JSONWriter destinationJSON) throws JSONException
	{
		destinationJSON.array();
		if (foundset != null)
		{
			for (int idx : foundset.getSelectedIndexes())
			{
				destinationJSON.value(idx);
			}
		}
		destinationJSON.endArray();
	}

	protected void addViewPortBounds(JSONWriter destinationJSON) throws JSONException
	{
		destinationJSON.key(START_INDEX).value(viewPort.getStartIndex()).key(SIZE).value(viewPort.getSize());

	}

	public JSONWriter changesToJSON(JSONWriter destinationJSON, DataConversion conversionMarkers) throws JSONException
	{
		if (changeMonitor.shouldSendAll()) return toJSON(destinationJSON, conversionMarkers);
		else
		{
			if (conversionMarkers != null) conversionMarkers.convert(FoundsetPropertyType.TYPE_NAME); // so that the client knows it must use the custom client side JS for what JSON it gets

			boolean somethingChanged = false;
			// change monitor already takes care not to report duplicates here (like whole viewport + viewport bounds)
			if (changeMonitor.shouldSendFoundsetSize())
			{
				destinationJSON.object();
				destinationJSON.key(UPDATE_PREFIX + SERVER_SIZE).value(foundset != null ? foundset.getSize() : 0);
				somethingChanged = true;
			}
			if (changeMonitor.shouldSendSelectedIndexes())
			{
				if (!somethingChanged) destinationJSON.object();
				destinationJSON.key(UPDATE_PREFIX + SELECTED_ROW_INDEXES);
				addSelectedIndexes(destinationJSON);
				somethingChanged = true;
			}
			if (changeMonitor.shouldSendWholeViewPort())
			{
				if (!somethingChanged) destinationJSON.object();
				destinationJSON.key(UPDATE_PREFIX + VIEW_PORT);
				addViewPort(destinationJSON);
				somethingChanged = true;
			}
			else
			{
				boolean viewPortUpdateAdded = false;
				if (changeMonitor.shouldSendViewPortBounds())
				{
					if (!somethingChanged) destinationJSON.object();
					destinationJSON.key(UPDATE_PREFIX + VIEW_PORT).object();
					viewPortUpdateAdded = true;
					addViewPortBounds(destinationJSON);
					somethingChanged = true;
				}
				List<com.servoy.j2db.server.ngclient.property.FoundsetTypeChangeMonitor.RowData> viewPortChanges = changeMonitor.getViewPortChanges();
				if (viewPortChanges.size() > 0)
				{
					if (!somethingChanged) destinationJSON.object();
					if (!viewPortUpdateAdded)
					{
						destinationJSON.key(UPDATE_PREFIX + VIEW_PORT).object();
						viewPortUpdateAdded = true;
					}
					Map<String, Object> changes = new HashMap<>();
					PropertyDescription changeTypes = null;
					Map<String, Object>[] changesArray = new Map[viewPortChanges.size()];

					changes.put(UPDATE_PREFIX + ROWS, changesArray);

					PropertyDescription changeArrayTypes = AggregatedPropertyType.newAggregatedProperty();
					for (int i = viewPortChanges.size() - 1; i >= 0; i--)
					{
						TypedData<Map<String, Object>> rowTypedData = viewPortChanges.get(i).toMap();
						changesArray[i] = rowTypedData.content;
						if (rowTypedData.contentType != null) changeArrayTypes.putProperty(String.valueOf(i), rowTypedData.contentType);
					}

					if (changeArrayTypes.hasChildProperties())
					{
						changeTypes = AggregatedPropertyType.newAggregatedProperty();
						changeTypes.putProperty(UPDATE_PREFIX + ROWS, changeArrayTypes);
					}

					// convert for websocket traffic (for example Date objects will turn into long)
					JSONUtils.writeDataWithConversions(destinationJSON, changes, changeTypes);
					somethingChanged = true;
				}
				if (viewPortUpdateAdded) destinationJSON.endObject();
			}

			if (somethingChanged) destinationJSON.endObject();
			else
			{
				// no change yet we are still asked to send changes; we could send all or just nothing useful
				//destinationJSON.key(NO_OP).value(0);
				// TODO send all for now - when the separate tagging interface for granular updates vs full updates is added we can send NO_OP again
				toJSON(destinationJSON, conversionMarkers);
			}

			changeMonitor.clearChanges();
			return destinationJSON;
		}
	}

	protected void populateRowData(IRecordInternal record, Map<String, Object> data, PropertyDescription dataTypes)
	{
		Iterator<String> it = dataProviders.iterator();
		while (it.hasNext())
		{
			String dataProvider = it.next();
			Object value = record.getValue(dataProvider);
			PropertyDescription pd = NGUtils.getDataProviderPropertyDescription(dataProvider, foundset.getTable());

			if (pd != null)
			{
				dataTypes.putProperty(dataProvider, pd);
				if (pd.getType() instanceof IWrapperType< ? , ? >) value = ((IWrapperType)pd.getType()).wrap(value, null, new DataConverterContext(pd,
					webObject));
			}
			data.put(dataProvider, value);
		}
	}

	public void browserUpdatesReceived(Object jsonValue)
	{
		if (foundset == null) return;
		try
		{
			if (jsonValue instanceof JSONArray)
			{
				JSONArray arr = (JSONArray)jsonValue;
				for (int i = 0; i < arr.length(); i++)
				{
					JSONObject update = (JSONObject)arr.get(i);
					// {newViewPort: {startIndex : startIndex, size : size}}
					if (update.has("newViewPort"))
					{
						JSONObject newViewport = update.getJSONObject("newViewPort");
						viewPort.setBounds(newViewport.getInt(START_INDEX), newViewport.getInt(SIZE));
					}
					// {loadExtraRecords: negativeOrPositiveCount}
					else if (update.has("loadExtraRecords"))
					{
						viewPort.loadExtraRecords(update.getInt("loadExtraRecords"));
					}
					// {newClientSelection: newSelectedIndexesArray}
					else if (update.has("newClientSelection"))
					{
						JSONArray jsonSelectedIndexes = update.getJSONArray("newClientSelection");
						int[] newSelectedIndexes = new int[jsonSelectedIndexes.length()];
						for (int j = newSelectedIndexes.length - 1; j >= 0; j--)
						{
							newSelectedIndexes[j] = jsonSelectedIndexes.getInt(j);
						}

						// this !Arrays.equals check in conjunction with pause()/resume() is needed to avoid an effect on the client that server always sends back changed selection in which case
						// if the user quickly changes selection multiple times and the connection is slow, selection will jump all over
						// the place until it stabilizes correctly
						getListSelectionListener().pause();
						try
						{
							foundset.setSelectedIndexes(newSelectedIndexes);
						}
						finally
						{
							getListSelectionListener().resume();
							// if server denies the new selection as invalid and doesn't change selection, send it to the client so that it doesn't keep invalid selection
							if (!Arrays.equals(foundset.getSelectedIndexes(), newSelectedIndexes))
							{
								changeMonitor.selectionChanged();
							}
						}
					}
					else if (update.has("viewportDataChanged"))
					{
						// {dataChanged: { ROW_ID_COL_KEY: rowIDValue, dataproviderName: value }}
						JSONObject dataChangeJSON = (JSONObject)update.get("dataChanged");
						String rowIDValue = dataChangeJSON.getString(ROW_ID_COL_KEY);
						String dataProviderName = dataChangeJSON.getString(DATAPROVIDER_KEY);
						Object value = dataChangeJSON.get(VALUE_KEY);

						if (foundset != null)
						{
							Pair<String, Integer> splitHashAndIndex = splitPKHashAndIndex(rowIDValue);
							int recordIndex = foundset.getRecordIndex(splitHashAndIndex.getLeft(), splitHashAndIndex.getRight().intValue());

							if (recordIndex != -1)
							{
								IWebFormUI formUI = ((WebComponent)webObject).findParent(IWebFormUI.class); // this will no longer be needed once 'component' type handles the global/form variables
								IRecordInternal record = foundset.getRecord(recordIndex);
								// convert Dates where it's needed

								PropertyDescription dataProviderPropDesc = NGUtils.getDataProviderPropertyDescription(dataProviderName, foundset.getTable()); // this should be enough for when only foundset dataproviders are used
								value = JSONUtils.fromJSONUnwrapped(null, value, dataProviderPropDesc, null);

								changeMonitor.pauseRowUpdateListener(splitHashAndIndex.getLeft());
								try
								{
									if (record.startEditing())
									{
										try
										{
											record.setValue(dataProviderName, value);
										}
										catch (IllegalArgumentException e)
										{
											// TODO handle the validaton errors.
											formUI.getController().getApplication().reportError(
												"Validation for " + dataProviderName + " for value: " + value + " failed.", e);
										}
									}
									// else cannot start editing; finally block will deal with it (send old value back to client as new one can't be pushed)
								}
								finally
								{
									changeMonitor.resumeRowUpdateListener();
									// if server denies the new value as invalid and doesn't change it, send it to the client so that it doesn't keep invalid value
									if (!Utils.equalObjects(record.getValue(dataProviderName), value))
									{
										changeMonitor.recordsUpdated(recordIndex, recordIndex, foundset.getSize(), viewPort);
									}
								}
							}
							else
							{
								Debug.error("Cannot set foundset record (" + rowIDValue + ") dataprovider '" + dataProviderName + "' to value '" + value +
									". Record not found.");
							}
						}
					}
				}
			}
		}
		catch (JSONException e)
		{
			Debug.error("Error when getting browser updates for property (" + this.toString() + ")", e);
		}
	}

	/**
	 * When this foundset is used in combination with child "components" properties, those properties will need
	 * a dataAdapterList that is being fed records from this foundset.
	 */
	public IDataAdapterList getDataAdapterList()
	{
		// TODO remove this or replace it with something else that can feed records to component properties
		// this method gets called by linked component type property/properties
		if (dataAdapterList == null)
		{
			dataAdapterList = new DataAdapterList(((WebComponent)webObject).findParent(IWebFormUI.class).getController());
		}
		return dataAdapterList;
	}

	public static Pair<String, Integer> splitPKHashAndIndex(String pkHashAndIndex)
	{
		int index = pkHashAndIndex.lastIndexOf("_");
		int recordIndex = Integer.parseInt(pkHashAndIndex.substring(index + 1));
		String pkHash = pkHashAndIndex.substring(0, index);
		return new Pair<>(pkHash, Integer.valueOf(recordIndex));
	}

	public void addViewportDataChangeMonitor(ViewportDataChangeMonitor viewPortChangeMonitor)
	{
		changeMonitor.addViewportDataChangeMonitor(viewPortChangeMonitor);
	}

	public void removeViewportDataChangeMonitor(ViewportDataChangeMonitor viewPortChangeMonitor)
	{
		changeMonitor.removeViewportDataChangeMonitor(viewPortChangeMonitor);
	}

	/**
	 * Register a list of dataproviders that is needed client-side.
	 * @param dataProvidersToSend a list of dataproviders that will be sent to the browser as part of the foundset property's viewport data.
	 */
	public void includeDataProviders(Set<String> dataProvidersToSend)
	{
		if (dataProviders.addAll(dataProvidersToSend)) changeMonitor.dataProvidersChanged();
	}

	public boolean setEditingRowByPkHash(String pkHashAndIndex)
	{
		Pair<String, Integer> splitHashAndIndex = splitPKHashAndIndex(pkHashAndIndex);
		int recordIndex = splitHashAndIndex.getRight().intValue();
		IRecordInternal record = foundset.getRecord(recordIndex);
		String pkHash = splitHashAndIndex.getLeft();
		if (record != null && !pkHash.equals(record.getPKHashKey()))
		{
			recordIndex = foundset.getRecordIndex(pkHash, recordIndex);
			if (recordIndex != -1)
			{
				foundset.setSelectedIndex(recordIndex);
				return true;
			}
			else return false;
		}
		else foundset.setSelectedIndex(recordIndex);
		return true;
	}

	@Override
	public String toString()
	{
		return "'" + propertyName + "' foundset type property on component " + (webObject != null ? webObject.getName() : "- not yet attached -");
	}

}
