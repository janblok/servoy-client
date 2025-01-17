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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;
import org.sablo.BaseWebObject;
import org.sablo.IChangeListener;
import org.sablo.WebComponent;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.WebComponentSpecification.PushToServerEnum;
import org.sablo.specification.property.BrowserConverterContext;
import org.sablo.specification.property.CustomJSONPropertyType;
import org.sablo.specification.property.IBrowserConverterContext;
import org.sablo.specification.property.IConvertedPropertyType;
import org.sablo.specification.property.types.TypesRegistry;
import org.sablo.util.ValueReference;
import org.sablo.websocket.utils.DataConversion;
import org.sablo.websocket.utils.JSONUtils;
import org.sablo.websocket.utils.JSONUtils.FullValueToJSONConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.servoy.j2db.component.ComponentFormat;
import com.servoy.j2db.dataprocessing.IFoundSetInternal;
import com.servoy.j2db.dataprocessing.IFoundSetManagerInternal;
import com.servoy.j2db.dataprocessing.IRecordInternal;
import com.servoy.j2db.dataprocessing.ISwingFoundSet;
import com.servoy.j2db.dataprocessing.PrototypeState;
import com.servoy.j2db.persistence.Table;
import com.servoy.j2db.scripting.JSEvent;
import com.servoy.j2db.server.ngclient.DataAdapterList;
import com.servoy.j2db.server.ngclient.INGApplication;
import com.servoy.j2db.server.ngclient.IWebFormController;
import com.servoy.j2db.server.ngclient.IWebFormUI;
import com.servoy.j2db.server.ngclient.property.types.FormatPropertyType;
import com.servoy.j2db.server.ngclient.property.types.IDataLinkedType.TargetDataLinks;
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
public class FoundsetTypeSabloValue implements IDataLinkedPropertyValue
{

	protected static final Logger log = LoggerFactory.getLogger(CustomJSONPropertyType.class.getCanonicalName());
	public static final String FOUNDSET_SELECTOR = "foundsetSelector";
	protected static final String PUSH_TO_SERVER = "w";

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

	public static final String SEND_SELECTION_RESPONSE = "selectionResponse";
	public static final String SEND_SELECTION_REQUESTID = "selectionRequestID";

	public static final String MULTI_SELECT = "multiSelect";
	public static final String VIEW_PORT = "viewPort";
	public static final String COLUMN_FORMATS = "columnFormats";
	public static final String START_INDEX = "startIndex";
	public static final String SIZE = "size";
	public static final String PREFERRED_VIEWPORT_SIZE = "preferredViewportSize";
	public static final String ROWS = "rows";
	public static final String NO_OP = "n";

	public static final String CONVERSIONS = "conversions";

	// END keys and values used in JSON

	protected FoundsetTypeViewport viewPort;
	private IFoundSetInternal foundset;
	protected final Object designJSONValue;

	protected Map<String, String> dataproviders = new HashMap<>();
	protected Map<String, ComponentFormat> columnFormats;

	protected String foundsetSelector;
	protected FoundsetDataAdapterList dataAdapterList;
	protected String propertyName;

	protected FoundsetTypeChangeMonitor changeMonitor;
	protected FoundsetPropertySelectionListener listSelectionListener;

	protected FoundsetTypeRowDataProvider rowDataProvider;

	// child components can be foundset linked (forFoundset: ...); in this case foundset prop. API can sort by child component name if it's told
	// which child component maps to which foundset column...
	protected final Map<String, String> linkedChildComponentToColumn;

	protected final DataAdapterList parentDAL;

	protected BaseWebObject webObject;

	protected int selectionRequestMsgid;
	protected final FoundsetPropertyTypeConfig specConfig;

	public FoundsetTypeSabloValue(Object designJSONValue, String propertyName, DataAdapterList parentDAL, FoundsetPropertyTypeConfig specConfig)
	{
		this.designJSONValue = designJSONValue;
		this.propertyName = propertyName;
		this.parentDAL = parentDAL;
		this.specConfig = specConfig;

		rowDataProvider = new FoundsetTypeRowDataProvider(this);
		changeMonitor = new FoundsetTypeChangeMonitor(this, rowDataProvider);
		viewPort = new FoundsetTypeViewport(changeMonitor);
		// nothing to do here; foundset is not initialized until it's attached to a component
		linkedChildComponentToColumn = new HashMap<String, String>();
		// foundsetSelector as defined in component design XML.
		foundsetSelector = ((JSONObject)designJSONValue).optString(FOUNDSET_SELECTOR);
		initializeDataproviders(((JSONObject)designJSONValue).optJSONObject("dataproviders"));
	}

	public void initializeDataproviders(JSONObject dataProvidersJSON)
	{
		if (dataProvidersJSON != null)
		{
			Iterator keys = dataProvidersJSON.keys();
			if (keys.hasNext()) dataproviders.clear();
			while (keys.hasNext())
			{
				String key = (String)keys.next();
				dataproviders.put(key, dataProvidersJSON.optString(key));
			}
		}
	}

	protected void notifyDataProvidersUpdated()
	{
		refreshColumnFormats();

		if (getFoundset() != null)
		{
			if (viewPort.getSize() > 0) changeMonitor.viewPortCompletelyChanged();
		}
	}

	protected void refreshColumnFormats()
	{
		boolean formatsChanged = (columnFormats != null);
		columnFormats = null;
		formatsChanged = updateColumnFormatsIfNeeded() || formatsChanged;
		if (formatsChanged) changeMonitor.columnFormatsUpdated();
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
		updateFoundset((IRecordInternal)null);
		JSONObject spec = (JSONObject)designJSONValue;
		JSONObject dataProvidersJSON = spec.optJSONObject("dataproviders");
		if (dataProvidersJSON != null)
		{
			changeMonitor.dataProvidersChanged();
		}

		// register parent record changed listener
		if (parentDAL != null) parentDAL.addDataLinkedProperty(this, TargetDataLinks.LINKED_TO_ALL);
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
	protected void updateFoundset(IRecordInternal record)
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

		if (newFoundset == null && foundsetSelector != null && !"".equals(foundsetSelector))
		{
			try
			{
				// if we want to use this type on services as well we need extra code here to get the application
				newFoundset = (IFoundSetInternal)getFoundSetManager().getFoundSet(foundsetSelector);
			}
			catch (ServoyException e)
			{
				if (record != null && !(record instanceof PrototypeState)) Debug.error(e);
			}
		}
		updateFoundset(newFoundset);

	}

	protected IFoundSetManagerInternal getFoundSetManager()
	{
		return getFormUI().getDataConverterContext().getApplication().getFoundSetManager();
	}

	protected INGApplication getApplication()
	{
		return getFormUI().getDataConverterContext().getApplication();
	}

	public void updateFoundset(IFoundSetInternal newFoundset)
	{
		if (newFoundset != foundset)
		{
			int oldServerSize = (foundset != null ? foundset.getSize() : 0);
			int newServerSize = (newFoundset != null ? newFoundset.getSize() : 0);
			if (foundset instanceof ISwingFoundSet) ((ISwingFoundSet)foundset).getSelectionModel().removeListSelectionListener(getListSelectionListener());
			foundset = newFoundset;
			viewPort.setFoundset(foundset);
			if (oldServerSize != newServerSize) changeMonitor.newFoundsetSize();
			changeMonitor.selectionChanged();

			if (updateColumnFormatsIfNeeded()) changeMonitor.columnFormatsUpdated();

			if (foundset instanceof ISwingFoundSet) ((ISwingFoundSet)foundset).getSelectionModel().addListSelectionListener(getListSelectionListener());
			if (foundset != null && getDataAdapterList() != null) getDataAdapterList().setFindMode(foundset.isInFindMode());
		}
	}

	protected boolean updateColumnFormatsIfNeeded()
	{
		if (specConfig.sendDefaultFormats && columnFormats == null && getFoundset() != null && webObject != null)
		{
			columnFormats = new HashMap<>();
			for (Entry<String, String> dp : dataproviders.entrySet())
			{
				columnFormats.put(dp.getKey(),
					ComponentFormat.getComponentFormat(null, ((Table)getFoundset().getTable()).getColumn(dp.getValue()), getApplication(), true));
			}

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
	public void dataProviderOrRecordChanged(IRecordInternal record, String dataProvider, boolean isFormDP, boolean isGlobalDP, boolean fireChangeEvent)
	{
		updateFoundset(record);
	}

	@Override
	public void detach()
	{
		viewPort.dispose();
		if (foundset instanceof ISwingFoundSet) ((ISwingFoundSet)foundset).getSelectionModel().removeListSelectionListener(getListSelectionListener());
	}

	public JSONWriter toJSON(JSONWriter destinationJSON, DataConversion conversionMarkers, IBrowserConverterContext dataConverterContext) throws JSONException
	{
		// TODO conversion markers should never be null I think, but it did happen (due to JSONUtils.toJSONValue(JSONWriter writer, Object value, IForJsonConverter forJsonConverter, ConversionLocation toDestinationType); will create a case for that
		if (conversionMarkers != null) conversionMarkers.convert(FoundsetPropertyType.TYPE_NAME); // so that the client knows it must use the custom client side JS for what JSON it gets

		rowDataProvider.initializeIfNeeded(dataConverterContext);

		destinationJSON.object();

		PushToServerEnum pushToServer = BrowserConverterContext.getPushToServerValue(dataConverterContext);
		if (pushToServer == PushToServerEnum.shallow || pushToServer == PushToServerEnum.deep)
		{
			destinationJSON.key(PUSH_TO_SERVER).value(pushToServer == PushToServerEnum.shallow ? false : true);
		}

		destinationJSON.key(SERVER_SIZE).value(getFoundset() != null ? getFoundset().getSize() : 0);
		destinationJSON.key(SELECTED_ROW_INDEXES);
		addSelectedIndexes(destinationJSON);
		destinationJSON.key(MULTI_SELECT).value(getFoundset() != null ? getFoundset().isMultiSelect() : false); // TODO listener and granular changes for this as well?

		writeColumnFormatsIfNeededAndAvailable(destinationJSON, dataConverterContext, false);

		// viewPort
		destinationJSON.key(VIEW_PORT);
		addViewPort(destinationJSON);
		// end viewPort

		destinationJSON.endObject();
		changeMonitor.clearChanges();
		return destinationJSON;
	}

	protected void writeColumnFormatsIfNeededAndAvailable(JSONWriter destinationJSON, IBrowserConverterContext dataConverterContext, boolean update)
		throws JSONException
	{
		if (specConfig.sendDefaultFormats)
		{
			destinationJSON.key((update ? UPDATE_PREFIX : "") + COLUMN_FORMATS).object();
			if (columnFormats != null)
			{
				IConvertedPropertyType<Object> formatPropertyType = (IConvertedPropertyType<Object>)TypesRegistry.getType(FormatPropertyType.TYPE_NAME); // just get it nicely in case it's overridden in designer for example

				for (Entry<String, ComponentFormat> columnFormat : columnFormats.entrySet())
				{
					formatPropertyType.toJSON(destinationJSON, columnFormat.getKey(), columnFormat.getValue(), null, null, dataConverterContext);
				}
			} // else just an empty object if fine (but we do write it because when changing dataproviders from scripting it could change from something to null and the client should know about it)
			destinationJSON.endObject();
		}
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
		if (getFoundset() != null)
		{
			DataConversion clientConversionInfo = new DataConversion();

			destinationJSON.key(ROWS);
			clientConversionInfo.pushNode(ROWS);
			rowDataProvider.writeRowData(viewPort.getStartIndex(), viewPort.getStartIndex() + viewPort.getSize() - 1, getFoundset(), destinationJSON,
				clientConversionInfo);
			clientConversionInfo.popNode();

			// conversion info for websocket traffic (for example Date objects will turn into long)
			JSONUtils.writeClientConversions(destinationJSON, clientConversionInfo);
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
		if (getFoundset() != null)
		{
			for (int idx : getFoundset().getSelectedIndexes())
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

	public JSONWriter changesToJSON(JSONWriter destinationJSON, DataConversion conversionMarkers, IBrowserConverterContext dataConverterContext)
		throws JSONException
	{
		if (changeMonitor.shouldSendAll()) return toJSON(destinationJSON, conversionMarkers, dataConverterContext);
		else
		{
			if (conversionMarkers != null) conversionMarkers.convert(FoundsetPropertyType.TYPE_NAME); // so that the client knows it must use the custom client side JS for what JSON it gets

			rowDataProvider.initializeIfNeeded(dataConverterContext);

			boolean somethingChanged = false;
			// change monitor already takes care not to report duplicates here (like whole viewport + viewport bounds)
			if (changeMonitor.shouldSendFoundsetSize())
			{
				destinationJSON.object();
				destinationJSON.key(UPDATE_PREFIX + SERVER_SIZE).value(getFoundset() != null ? getFoundset().getSize() : 0);
				somethingChanged = true;
			}
			if (changeMonitor.shouldSendSelectedIndexes())
			{
				if (!somethingChanged) destinationJSON.object();
				destinationJSON.key(UPDATE_PREFIX + SELECTED_ROW_INDEXES);
				addSelectedIndexes(destinationJSON);
				somethingChanged = true;
			}
			if (changeMonitor.shouldSendSelectionDenied())
			{
				if (!somethingChanged) destinationJSON.object();
				destinationJSON.key(UPDATE_PREFIX + SEND_SELECTION_REQUESTID);
				destinationJSON.value(this.selectionRequestMsgid);
				destinationJSON.key(UPDATE_PREFIX + SEND_SELECTION_RESPONSE);
				destinationJSON.value(false);
				somethingChanged = true;
			}
			if (changeMonitor.shouldSendSelectionAccepted())
			{
				if (!somethingChanged) destinationJSON.object();
				destinationJSON.key(UPDATE_PREFIX + SEND_SELECTION_REQUESTID);
				destinationJSON.value(this.selectionRequestMsgid);
				destinationJSON.key(UPDATE_PREFIX + SEND_SELECTION_RESPONSE);
				destinationJSON.value(true);
				somethingChanged = true;
			}
			if (changeMonitor.shouldSendColumnFormats())
			{
				if (!somethingChanged) destinationJSON.object();
				writeColumnFormatsIfNeededAndAvailable(destinationJSON, dataConverterContext, true);
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

					DataConversion clientConversionInfo = new DataConversion();
					clientConversionInfo.pushNode(UPDATE_PREFIX + ROWS);
					destinationJSON.key(UPDATE_PREFIX + ROWS).array();

					for (int i = 0; i < viewPortChanges.size(); i++)
					{
						clientConversionInfo.pushNode(String.valueOf(i));
						viewPortChanges.get(i).writeJSONContent(destinationJSON, null, FullValueToJSONConverter.INSTANCE, clientConversionInfo);
						clientConversionInfo.popNode();
					}
					clientConversionInfo.popNode();
					destinationJSON.endArray();

					// conversion info for websocket traffic (for example Date objects will turn into long)
					JSONUtils.writeClientConversions(destinationJSON, clientConversionInfo);
					somethingChanged = true;
				}
				if (viewPortUpdateAdded) destinationJSON.endObject();
			}

			if (somethingChanged) destinationJSON.endObject();
			else
			{
				// no change yet we are still asked to send changes (so not full value); send a dummy NO_OP
				destinationJSON.object().key(NO_OP).value(true).endObject();
			}

			changeMonitor.clearChanges();
			return destinationJSON;
		}
	}

	protected void populateRowData(IRecordInternal record, JSONWriter w, DataConversion clientConversionInfo, IBrowserConverterContext browserConverterContext)
		throws JSONException
	{
		Iterator<Entry<String, String>> it = dataproviders.entrySet().iterator();
		while (it.hasNext())
		{
			Entry<String, String> entry = it.next();
			String dataProvider = entry.getValue();
			Object value = record.getValue(dataProvider);
			PropertyDescription pd = NGUtils.getDataProviderPropertyDescription(dataProvider, foundset.getTable(), false);

			// currently all that NGUtils.getDataProviderPropertyDescription can return is IConvertedProperty type or default types; so we don't need any special value pre-processing (like IWrapperType or IServoyAwareValue or others would need)
//			if (pd != null)
//			{
//				if (pd.getType() instanceof IWrapperType< ? , ? >) value = ((IWrapperType)pd.getType()).wrap(value, null, new DataConverterContext(pd,
//					webObject));
//			}

			clientConversionInfo.pushNode(entry.getKey());
			FullValueToJSONConverter.INSTANCE.toJSONValue(w, entry.getKey(), value, pd, clientConversionInfo, browserConverterContext);
			clientConversionInfo.popNode();
		}
	}

	public void browserUpdatesReceived(Object jsonValue, PropertyDescription pd, IBrowserConverterContext dataConverterContext)
	{
		PushToServerEnum pushToServer = BrowserConverterContext.getPushToServerValue(dataConverterContext);

		if (getFoundset() == null) return;
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
					if (update.has(PREFERRED_VIEWPORT_SIZE))
					{
						viewPort.setPreferredViewportSize(update.getInt(PREFERRED_VIEWPORT_SIZE));
					}

					// {loadExtraRecords: negativeOrPositiveCount}
					else if (update.has("loadExtraRecords"))
					{
						viewPort.loadExtraRecords(update.getInt("loadExtraRecords"));
					}
					else if (update.has("sort"))
					{
						JSONArray columns = update.getJSONArray("sort");
						StringBuilder sort = new StringBuilder();
						Map<String, String> dp = dataproviders.size() > 0 ? dataproviders : linkedChildComponentToColumn;
						String dataProviderID = null;
						boolean sortAscending = true;
						for (int j = 0; j < columns.length(); j++)
						{
							JSONObject sortColumn = columns.getJSONObject(j);
							String name = sortColumn.getString("name");
							if (dp.containsKey(name))
							{
								sort.append(dp.get(name));
								sort.append(" " + sortColumn.getString("direction"));
								if (dataProviderID == null)
								{
									dataProviderID = dp.get(name);
									sortAscending = "asc".equalsIgnoreCase(sortColumn.getString("direction"));
								}
								if (j < columns.length() - 1) sort.append(",");
							}
						}
						IWebFormController fc = getFormUI().getController();
						if (fc != null && fc.getForm().getOnSortCmdMethodID() > 0 && dataProviderID != null)
						{
							// our api only supports one dataproviderid sort at a time
							JSEvent event = new JSEvent();
							event.setFormName(fc.getName());
							fc.executeFunction(
								String.valueOf(fc.getForm().getOnSortCmdMethodID()),
								Utils.arrayMerge((new Object[] { dataProviderID, Boolean.valueOf(sortAscending), event }),
									Utils.parseJSExpressions(fc.getForm().getInstanceMethodArguments("onSortCmdMethodID"))), //$NON-NLS-1$
								true, null, false, "onSortCmdMethodID"); //$NON-NLS-1$
						}
						else
						{
							try
							{
								foundset.setSort(sort.toString());
							}
							catch (ServoyException e)
							{
								Debug.error("Cannot sort foundset by " + sort.toString(), e);
							}
						}
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
							if (newSelectedIndexes.length == 1)
							{
								foundset.setSelectedIndex(newSelectedIndexes[0]);
							}
							else
							{
								foundset.setSelectedIndexes(newSelectedIndexes);
							}
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
					// {newClientSelectionRequest: newSelectedIndexesArray}
					else if (update.has("newClientSelectionRequest"))
					{
						this.selectionRequestMsgid = update.getInt("selectionRequestID");
						JSONArray jsonSelectedIndexes = update.getJSONArray("newClientSelectionRequest");
						int[] newSelectedIndexes = new int[jsonSelectedIndexes.length()];
						for (int j = newSelectedIndexes.length - 1; j >= 0; j--)
						{
							newSelectedIndexes[j] = jsonSelectedIndexes.getInt(j);
						}

						int[] oldSelection = foundset.getSelectedIndexes();
						// this !Arrays.equals check in conjunction with pause()/resume() is needed to avoid an effect on the client that server always sends back changed selection in which case
						// if the user quickly changes selection multiple times and the connection is slow, selection will jump all over
						// the place until it stabilizes correctly
						getListSelectionListener().pause();
						try
						{
							if (newSelectedIndexes.length == 1)
							{
								foundset.setSelectedIndex(newSelectedIndexes[0]);
							}
							else
							{
								foundset.setSelectedIndexes(newSelectedIndexes);
							}
						}
						finally
						{
							getListSelectionListener().resume();

							if (!Arrays.equals(oldSelection, foundset.getSelectedIndexes()))
							{// if the selection is changed, send it back to the client so that its model is also updated
								changeMonitor.selectionChanged();
								changeMonitor.selectionAccepted();
							}
							else
							{
								if (!Arrays.equals(oldSelection, newSelectedIndexes))
								{ // it was supposed to change but the server did not allow it
									changeMonitor.selectionDenied();
								}
								else changeMonitor.selectionAccepted();
							}
						}
					}
					else if (update.has(ViewportDataChangeMonitor.VIEWPORT_CHANGED))
					{
						if (PushToServerEnum.allow.compareTo(pushToServer) <= 0)
						{
							// {dataChanged: { ROW_ID_COL_KEY: rowIDValue, dataproviderName: value }}
							JSONObject dataChangeJSON = (JSONObject)update.get(ViewportDataChangeMonitor.VIEWPORT_CHANGED);
							String rowIDValue = dataChangeJSON.getString(ROW_ID_COL_KEY);
							String dataProviderName = dataproviders.get(dataChangeJSON.getString(DATAPROVIDER_KEY));
							Object value = dataChangeJSON.get(VALUE_KEY);

							if (foundset != null)
							{
								Pair<String, Integer> splitHashAndIndex = splitPKHashAndIndex(rowIDValue);
								int recordIndex = foundset.getRecordIndex(splitHashAndIndex.getLeft(), splitHashAndIndex.getRight().intValue());

								if (recordIndex != -1)
								{
									IWebFormUI formUI = getFormUI(); // this will no longer be needed once 'component' type handles the global/form variables
									IRecordInternal record = foundset.getRecord(recordIndex);
									// convert Dates where it's needed

									PropertyDescription dataProviderPropDesc = NGUtils.getDataProviderPropertyDescription(dataProviderName,
										foundset.getTable(), false); // this should be enough for when only foundset dataproviders are used
									ValueReference<Boolean> returnValueAdjustedIncommingValueForRow = new ValueReference<Boolean>(Boolean.FALSE);
									value = JSONUtils.fromJSONUnwrapped(null, value, dataProviderPropDesc, dataConverterContext,
										returnValueAdjustedIncommingValueForRow);

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
										// if server denies the new value as invalid and doesn't change it, send it to the client so that it doesn't keep invalid value; the same if for example a double was rounded to an int
										if (!Utils.equalObjects(record.getValue(dataProviderName), value) ||
											returnValueAdjustedIncommingValueForRow.value.booleanValue())
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
						else
						{
							log.error("Property (" +
								pd +
								") that doesn't define a suitable pushToServer value (allow/shallow/deep) tried to modify foundset dataprovider value serverside. Denying and sending back full viewport!");
							changeMonitor.viewPortCompletelyChanged();
						}
					}
				}
			}
		}
		catch (JSONException e)
		{
			Debug.error("Error when getting browser updates for property (" + this.toString() + ") for " + jsonValue, e);
		}
	}

	/**
	 * When this foundset is used in combination with child "components" properties, those properties will need
	 * a dataAdapterList that is being fed records from this foundset.
	 */
	public FoundsetDataAdapterList getDataAdapterList()
	{
		// this method gets called by linked component type property/properties
		// that means here we are working with components, not with services - so we can cast webObject and create a new data adapter list
		if (dataAdapterList == null && webObject != null)
		{
			dataAdapterList = new FoundsetDataAdapterList(getFormUI().getController());
		}
		return dataAdapterList;
	}

	protected IWebFormUI getFormUI()
	{
		return ((WebComponent)webObject).findParent(IWebFormUI.class);
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


	protected void setColumnDataprovider(String name, String dataprovider)
	{
		linkedChildComponentToColumn.put(name, dataprovider);
	}
}
