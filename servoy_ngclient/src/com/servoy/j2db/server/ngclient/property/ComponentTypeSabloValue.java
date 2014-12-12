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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;
import org.sablo.IChangeListener;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.property.ISmartPropertyValue;
import org.sablo.specification.property.types.AggregatedPropertyType;
import org.sablo.websocket.TypedData;
import org.sablo.websocket.utils.DataConversion;
import org.sablo.websocket.utils.JSONUtils;
import org.sablo.websocket.utils.JSONUtils.FullValueToJSONConverter;

import com.servoy.j2db.dataprocessing.IFoundSetInternal;
import com.servoy.j2db.server.ngclient.ComponentContext;
import com.servoy.j2db.server.ngclient.ComponentFactory;
import com.servoy.j2db.server.ngclient.FormElement;
import com.servoy.j2db.server.ngclient.IDataAdapterList;
import com.servoy.j2db.server.ngclient.IDirtyPropertyListener;
import com.servoy.j2db.server.ngclient.IWebFormUI;
import com.servoy.j2db.server.ngclient.WebFormComponent;
import com.servoy.j2db.server.ngclient.property.FoundsetTypeChangeMonitor.RowData;
import com.servoy.j2db.server.ngclient.property.types.NGConversions.InitialToJSONConverter;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.Pair;

/**
 * Value used at runtime in Sablo component.
 *
 * @author acostescu
 */
@SuppressWarnings("nls")
public class ComponentTypeSabloValue implements ISmartPropertyValue
{

	public static final String NO_OP = "n";

	protected WebFormComponent childComponent;

	protected boolean componentIsCreated = false;

	protected String forFoundsetTypedPropertyName;
	protected PropertyChangeListener forFoundsetPropertyListener;
	protected ViewportDataChangeMonitor viewPortChangeMonitor;

	protected WebFormComponent parentComponent;
	protected IChangeListener monitor;
	protected PropertyDescription componentPropertyDescription;

	protected final ComponentTypeFormElementValue formElementValue;


	public ComponentTypeSabloValue(ComponentTypeFormElementValue formElementValue, PropertyDescription componentPropertyDescription,
		String forFoundsetTypedPropertyName)
	{
		this.formElementValue = formElementValue;
		this.forFoundsetTypedPropertyName = forFoundsetTypedPropertyName;
		this.componentPropertyDescription = componentPropertyDescription;
	}

	@Override
	public void attachToBaseObject(IChangeListener changeMonitor, org.sablo.BaseWebObject parentComponent)
	{
		componentIsCreated = false;
		this.parentComponent = (WebFormComponent)parentComponent;
		this.monitor = changeMonitor;

		if (childComponent != null)
		{
			childComponent.dispose();
		}
		createComponentIfNeededAndPossible();
		if (forFoundsetTypedPropertyName != null)
		{
			this.parentComponent.addPropertyChangeListener(forFoundsetTypedPropertyName, forFoundsetPropertyListener = new PropertyChangeListener()
			{
				@Override
				public void propertyChange(PropertyChangeEvent evt)
				{
					if (evt.getNewValue() != null) createComponentIfNeededAndPossible();
				}
			});
		}
	}

	@Override
	public void detach()
	{
		if (forFoundsetPropertyListener != null)
		{
			parentComponent.removePropertyChangeListener(forFoundsetTypedPropertyName, forFoundsetPropertyListener);

			FoundsetTypeSabloValue foundsetPropValue = getFoundsetValue();
			if (foundsetPropValue != null && viewPortChangeMonitor != null)
			{
				foundsetPropValue.removeViewportDataChangeMonitor(viewPortChangeMonitor);
			}
		}
	}

	private FoundsetTypeSabloValue getFoundsetValue()
	{
		if (parentComponent != null)
		{
			if (forFoundsetTypedPropertyName != null)
			{
				return (FoundsetTypeSabloValue)parentComponent.getProperty(forFoundsetTypedPropertyName);
			}
		}
		return null;
	}

	protected void createComponentIfNeededAndPossible()
	{
		// this method should get called only after init() got called on all properties from this component (including this one)
		// so now we should be able to find a potentially linked foundset property value
		if (componentIsCreated || parentComponent == null) return;

		final FoundsetTypeSabloValue foundsetPropValue = getFoundsetValue();

		if (foundsetPropValue == null && forFoundsetTypedPropertyName != null) return;

		componentIsCreated = true;
		IWebFormUI formUI = parentComponent.findParent(IWebFormUI.class);
		final IDataAdapterList dal = (foundsetPropValue != null ? foundsetPropValue.getDataAdapterList() : formUI.getDataAdapterList());

		childComponent = ComponentFactory.createComponent(dal.getApplication(), dal, formElementValue.element, parentComponent);
		childComponent.setDirtyPropertyListener(new IDirtyPropertyListener()
		{
			@Override
			public void propertyFlaggedAsDirty(String propertyName)
			{
				// this gets called whenever a property is flagged as dirty/changed/to be sent to browser
				if (forFoundsetTypedPropertyName != null && formElementValue.recordBasedProperties.contains(propertyName))
				{
					if (!((FoundsetDataAdapterList)dal).isQuietRecordChangeInProgress()) // if forFoundsetTypedPropertyName != null we are using a foundset DAL, so just cast
					{
						// for example valuelist properties can get filtered based on client sent filter in which case the property does change without
						// any actual change in the record; in this case we need to mark it correctly in viewport as a change
						int idx = foundsetPropValue.getFoundset().getRecordIndex(dal.getRecord());
						int relativeIdx = idx - foundsetPropValue.getViewPort().getStartIndex();
						viewPortChangeMonitor.queueCellChange(relativeIdx, idx, propertyName, foundsetPropValue.getFoundset());
					} // else this change was probably determined by the fact that we reuse components, changing the record in the DAL to get data for a specific row
				}
				else
				{
					// non-record related prop. changed...
					monitor.valueChanged();
				}
			}
		});
		childComponent.setComponentContext(new ComponentContext(formElementValue.propertyPath));
		formUI.contributeComponentToElementsScope(formElementValue.element, formElementValue.element.getWebComponentSpec(), childComponent);
		for (String handler : childComponent.getFormElement().getHandlers())
		{
			Object value = childComponent.getFormElement().getPropertyValue(handler);
			if (value != null)
			{
				childComponent.add(handler, (Integer)value);
			}
		}

		if (foundsetPropValue != null)
		{
			viewPortChangeMonitor = new ViewportDataChangeMonitor(monitor, new ComponentViewportRowDataProvider((FoundsetDataAdapterList)dal, childComponent,
				formElementValue.recordBasedProperties, this));
			foundsetPropValue.addViewportDataChangeMonitor(viewPortChangeMonitor);
		}
		if (childComponent.hasChanges()) monitor.valueChanged();
	}

	/**
	 * Writes a diff update between the value it has in the template and the initial data requested after runtime components were created or during a page refresh.
	 */
	public JSONWriter initialToJSON(JSONWriter destinationJSON, DataConversion conversionMarkers) throws JSONException
	{
		if (conversionMarkers != null) conversionMarkers.convert(ComponentPropertyType.TYPE_NAME); // so that the client knows it must use the custom client side JS for what JSON it gets

		destinationJSON.object();
		destinationJSON.key(ComponentPropertyType.PROPERTY_UPDATES_KEY);
		destinationJSON.object();

		// model content
		TypedData<Map<String, Object>> allProps = childComponent.getProperties();
		allProps.content = new HashMap<>(allProps.content);
		removeRecordDependentProperties(allProps);
		destinationJSON.key(ComponentPropertyType.MODEL_KEY);
		destinationJSON.object();
		JSONUtils.writeDataWithConversions(InitialToJSONConverter.INSTANCE, destinationJSON, allProps.content, allProps.contentType, childComponent);
		destinationJSON.endObject();

		// viewport content
		writeWholeViewportToJSON(destinationJSON);

		destinationJSON.endObject();
		destinationJSON.endObject();

		return destinationJSON;
	}

	public JSONWriter changesToJSON(JSONWriter destinationJSON, DataConversion conversionMarkers) throws JSONException
	{
		if (conversionMarkers != null) conversionMarkers.convert(ComponentPropertyType.TYPE_NAME); // so that the client knows it must use the custom client side JS for what JSON it gets

		TypedData<Map<String, Object>> changes = childComponent.getAndClearChanges();

		removeRecordDependentProperties(changes);

		boolean modelChanged = (changes.content.size() > 0);
		boolean viewPortChanged = (forFoundsetTypedPropertyName != null && (viewPortChangeMonitor.shouldSendWholeViewport() || viewPortChangeMonitor.getViewPortChanges().size() > 0));

		destinationJSON.object();
		if (modelChanged || viewPortChanged)
		{
			destinationJSON.key(ComponentPropertyType.PROPERTY_UPDATES_KEY);
			destinationJSON.object();
		}

		if (modelChanged)
		{
			destinationJSON.key(ComponentPropertyType.MODEL_KEY);
			destinationJSON.object();
			// send component model (when linked to foundset only props that are not record related)
			JSONUtils.writeDataWithConversions(destinationJSON, changes.content, changes.contentType, childComponent);
			destinationJSON.endObject();
		}

		if (viewPortChanged)
		{
			// something in the viewport containing per-record component property values changed - send updates
			if (viewPortChangeMonitor.shouldSendWholeViewport())
			{
				writeWholeViewportToJSON(destinationJSON);
			}
			else
			// viewPortChanges.size() > 0
			{
				List<RowData> viewPortChanges = viewPortChangeMonitor.getViewPortChanges();
				Map<String, Object>[] changesArray = new Map[viewPortChanges.size()];
				DataConversion clientConversionInfo = new DataConversion();

				clientConversionInfo.pushNode(ComponentPropertyType.MODEL_VIEWPORT_CHANGES_KEY);
				destinationJSON.key(ComponentPropertyType.MODEL_VIEWPORT_CHANGES_KEY).array();

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

			}
			viewPortChangeMonitor.clearChanges();
		}

		if (modelChanged || viewPortChanged)
		{
			destinationJSON.endObject();
		}
		else
		{
			// no change yet we are still asked to send changes (so not full value); send a dummy NO_OP
			destinationJSON.key(NO_OP).value(true);
		}
		destinationJSON.endObject();

		return destinationJSON;
	}

	protected void writeWholeViewportToJSON(JSONWriter destinationJSON) throws JSONException
	{
		if (forFoundsetTypedPropertyName != null)
		{
			FoundsetTypeViewport foundsetPropertyViewPort = getFoundsetValue().getViewPort();

			DataConversion clientConversionInfo = new DataConversion();

			destinationJSON.key(ComponentPropertyType.MODEL_VIEWPORT_KEY);
			clientConversionInfo.pushNode(ComponentPropertyType.MODEL_VIEWPORT_KEY);
			viewPortChangeMonitor.getRowDataProvider().writeRowData(foundsetPropertyViewPort.getStartIndex(),
				foundsetPropertyViewPort.getStartIndex() + foundsetPropertyViewPort.getSize() - 1, getFoundsetValue().getFoundset(), destinationJSON,
				clientConversionInfo);
			clientConversionInfo.popNode();

			// conversion info for websocket traffic (for example Date objects will turn into long)
			JSONUtils.writeClientConversions(destinationJSON, clientConversionInfo);
		}
	}

	/**
	 * Writes the entire value of this property as JSON. This includes the template values, not just the runtime component properties.
	 * This is currently needed and can get called if the property is nested inside other complex properties (json object/array) that sometimes
	 * might want/need to send again the entire content.
	 */
	public JSONWriter fullToJSON(JSONWriter writer, DataConversion conversionMarkers, ComponentPropertyType componentPropertyType) throws JSONException
	{
		if (conversionMarkers != null) conversionMarkers.convert(ComponentPropertyType.TYPE_NAME); // so that the client knows it must use the custom client side JS for what JSON it gets

		// create children of component as specified by this property
		FormElement fe = formElementValue.element;

		writer.object();

		// get template values
		TypedData<Map<String, Object>> modelProperties = fe.propertiesForTemplateJSON();
		// update them with runtime values
		TypedData<Map<String, Object>> changes = childComponent.getAndClearChanges();
		removeRecordDependentProperties(changes);
		for (Entry<String, Object> changeEntry : changes.content.entrySet())
		{
			modelProperties.content.put(changeEntry.getKey(), changeEntry.getValue());
			if (changes.contentType != null)
			{
				PropertyDescription type = changes.contentType.getProperty(changeEntry.getKey());
				if (type != null)
				{
					if (modelProperties.contentType == null) modelProperties.contentType = AggregatedPropertyType.newAggregatedProperty();
					modelProperties.contentType.putProperty(changeEntry.getKey(), type);
				}
			}
		}

		componentPropertyType.writeTemplateJSONContent(writer, formElementValue, forFoundsetTypedPropertyName, fe, modelProperties);

		writeWholeViewportToJSON(writer);

		writer.endObject();

		return writer;
	}

	protected void removeRecordDependentProperties(TypedData<Map<String, Object>> changes)
	{
		// if the components property type is not linked to a foundset then the dataproviders/tagstring must also be sent when needed
		// but if it is linked to a foundset those should only be sent through the viewport
		if (forFoundsetTypedPropertyName != null)
		{
			// remove properties that are per record basis from the "per all model"
			for (String propertyName : formElementValue.recordBasedProperties)
			{
				changes.content.remove(propertyName);
				if (changes.contentType != null) changes.contentType.putProperty(propertyName, null);
			}
		}
	}

	public void browserUpdatesReceived(Object jsonValue)
	{
		if (childComponent == null) return;

		try
		{
			JSONArray updates = (JSONArray)jsonValue;
			for (int i = 0; i < updates.length(); i++)
			{
				JSONObject update = (JSONObject)updates.get(i);
				if (update.has("handlerExec"))
				{
					// { handlerExec: {
					// 		eventType: ...,
					// 		args: ...,
					// 		rowId : ...
					// }});
					update = update.getJSONObject("handlerExec");
					if (update.has("eventType"))
					{
						boolean selectionOk = true;
						if (update.has("rowId"))
						{
							String rowId = update.getString("rowId");
							FoundsetTypeSabloValue foundsetValue = getFoundsetValue();
							if (foundsetValue != null)
							{
								if (!foundsetValue.setEditingRowByPkHash(rowId))
								{
									Debug.error("Cannot select row when event was fired; row identifier: " + rowId);
									selectionOk = false;
								}
							}
						}
						if (selectionOk)
						{
							String eventType = update.getString("eventType");
//						String beanName = update.getString("beanName");
							JSONArray jsargs = update.getJSONArray("args");
							Object[] args = new Object[jsargs == null ? 0 : jsargs.length()];
							for (int j = 0; jsargs != null && j < jsargs.length(); j++)
							{
								args[j] = jsargs.get(j);
							}

							childComponent.executeEvent(eventType, args); // TODO HANDLE RETURN VALUE
						}
					}
				}
				else if (update.has("propertyChanges"))
				{
					// { propertyChanges : {
					// 		prop1: ...,
					// 		prop2: ...
					// }}

					JSONObject changes = update.getJSONObject("propertyChanges");

					Iterator<String> keys = changes.keys();
					while (keys.hasNext())
					{
						String key = keys.next();
						Object object = changes.get(key);
						childComponent.putBrowserProperty(key, object);
					}
				}
				else if (update.has(ViewportDataChangeMonitor.VIEWPORT_CHANGED))
				{
					// component is linked to a foundset and the value of a property that depends on the record changed client side;
					// in this case update DataAdapterList with the correct record and then set the value on the component
					FoundsetTypeSabloValue foundsetPropertyValue = getFoundsetValue();
					if (foundsetPropertyValue != null && foundsetPropertyValue.getFoundset() != null)
					{
						JSONObject change = update.getJSONObject(ViewportDataChangeMonitor.VIEWPORT_CHANGED);

						String rowIDValue = change.getString(FoundsetTypeSabloValue.ROW_ID_COL_KEY);
						String propertyName = change.getString(FoundsetTypeSabloValue.DATAPROVIDER_KEY);
						Object value = change.get(FoundsetTypeSabloValue.VALUE_KEY);

						updatePropertyValueForRecord(foundsetPropertyValue, rowIDValue, propertyName, value);
					}
					else
					{
						Debug.error("Component updates received for record linked property, but component is not linked to a foundset: " +
							update.get(ViewportDataChangeMonitor.VIEWPORT_CHANGED));
					}
				}
				else if (update.has("svyApply"))
				{
					// { svyApply: {
					// 		rowId: rowId, // only when linked to foundset
					// 		propertyName: property,
					// 		propertyValue: propertyValue
					// }}
					JSONObject changeAndApply = update.getJSONObject("svyApply");

					String propertyName = changeAndApply.getString(ComponentPropertyType.PROPERTY_NAME_KEY);
					Object value = changeAndApply.get(ComponentPropertyType.VALUE_KEY);

					IDataAdapterList dal;
					if (forFoundsetTypedPropertyName != null && formElementValue.recordBasedProperties.contains(propertyName))
					{
						// changes component record and sets value
						String rowIDValue = changeAndApply.getString(FoundsetTypeSabloValue.ROW_ID_COL_KEY);
						updatePropertyValueForRecord(getFoundsetValue(), rowIDValue, propertyName, value);
						dal = getFoundsetValue().getDataAdapterList();
					}
					else
					{
						childComponent.putBrowserProperty(propertyName, value);
						IWebFormUI formUI = parentComponent.findParent(IWebFormUI.class);
						dal = formUI.getDataAdapterList();
					}

					// apply change to record/dp
					dal.pushChanges(childComponent, propertyName);
				}
				else if (update.has("svyStartEdit"))
				{
					// { svyStartEdit: {
					//   rowId: rowId, // only if linked to foundset
					//   propertyName: property
					// }}
					JSONObject startEditData = update.getJSONObject("svyStartEdit");

					String propertyName = startEditData.getString(ComponentPropertyType.PROPERTY_NAME_KEY);

					IDataAdapterList dal;
					if (forFoundsetTypedPropertyName != null && formElementValue.recordBasedProperties.contains(propertyName))
					{
						String rowIDValue = startEditData.getString(FoundsetTypeSabloValue.ROW_ID_COL_KEY);
						IFoundSetInternal foundset = getFoundsetValue().getFoundset();

						Pair<String, Integer> splitHashAndIndex = FoundsetTypeSabloValue.splitPKHashAndIndex(rowIDValue);
						int recordIndex = foundset.getRecordIndex(splitHashAndIndex.getLeft(), splitHashAndIndex.getRight().intValue());

						dal = getFoundsetValue().getDataAdapterList();
						if (recordIndex != -1)
						{
							((FoundsetDataAdapterList)dal).setRecordQuietly(foundset.getRecord(recordIndex));
						}
						else
						{
							Debug.error("Cannot find record for foundset linked record dependent component property - startEdit (" + rowIDValue +
								"); property '" + propertyName, new RuntimeException());
						}
					}
					else
					{
						IWebFormUI formUI = parentComponent.findParent(IWebFormUI.class);
						dal = formUI.getDataAdapterList();
					}

					dal.startEdit(childComponent, propertyName);
				}
			}
		}
		catch (Exception ex)
		{
			Debug.error(ex);
		}
	}

	protected void updatePropertyValueForRecord(FoundsetTypeSabloValue foundsetPropertyValue, String rowIDValue, String propertyName, Object value)
	{
		IFoundSetInternal foundset = foundsetPropertyValue.getFoundset();

		Pair<String, Integer> splitHashAndIndex = FoundsetTypeSabloValue.splitPKHashAndIndex(rowIDValue);
		int recordIndex = foundset.getRecordIndex(splitHashAndIndex.getLeft(), splitHashAndIndex.getRight().intValue());

		if (recordIndex != -1)
		{
			foundsetPropertyValue.getDataAdapterList().setRecordQuietly(foundset.getRecord(recordIndex));

			viewPortChangeMonitor.pauseRowUpdateListener(splitHashAndIndex.getLeft());
			try
			{
				childComponent.putBrowserProperty(propertyName, value);
			}
			catch (JSONException e)
			{
				Debug.error("Setting value for record dependent property '" + propertyName + "' in foundset linked component to value: " + value + " failed.",
					e);
			}
			finally
			{
				viewPortChangeMonitor.resumeRowUpdateListener();
			}
		}
		else
		{
			Debug.error("Cannot set foundset linked record dependent component property for (" + rowIDValue + ") property '" + propertyName + "' to value '" +
				value + "' of component: " + childComponent + ". Record not found.", new RuntimeException());
		}
	}
}
