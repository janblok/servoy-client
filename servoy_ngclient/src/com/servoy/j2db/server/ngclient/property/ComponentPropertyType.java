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
import java.util.Map.Entry;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;
import org.mozilla.javascript.Scriptable;
import org.sablo.BaseWebObject;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.WebComponentApiDefinition;
import org.sablo.specification.property.CustomJSONPropertyType;
import org.sablo.specification.property.IConvertedPropertyType;
import org.sablo.specification.property.IDataConverterContext;
import org.sablo.specification.property.ISupportsGranularUpdates;
import org.sablo.websocket.TypedData;
import org.sablo.websocket.utils.DataConversion;
import org.sablo.websocket.utils.JSONUtils;

import com.servoy.j2db.FlattenedSolution;
import com.servoy.j2db.server.ngclient.DataAdapterList;
import com.servoy.j2db.server.ngclient.FormElement;
import com.servoy.j2db.server.ngclient.WebFormComponent;
import com.servoy.j2db.server.ngclient.property.types.IDataLinkedType;
import com.servoy.j2db.server.ngclient.property.types.IDataLinkedType.TargetDataLinks;
import com.servoy.j2db.server.ngclient.property.types.ITemplateValueUpdaterType;
import com.servoy.j2db.server.ngclient.property.types.NGConversions.FormElementToJSON;
import com.servoy.j2db.server.ngclient.property.types.NGConversions.IDesignToFormElement;
import com.servoy.j2db.server.ngclient.property.types.NGConversions.IFormElementToSabloComponent;
import com.servoy.j2db.server.ngclient.property.types.NGConversions.IFormElementToTemplateJSON;
import com.servoy.j2db.server.ngclient.property.types.NGConversions.ISabloComponentToRhino;
import com.servoy.j2db.server.ngclient.property.types.PropertyPath;
import com.servoy.j2db.util.Debug;

/**
 * Implementation for the complex custom type "component".
 *
 * @author acostescu
 */
public class ComponentPropertyType extends CustomJSONPropertyType<ComponentTypeSabloValue> implements
	IDesignToFormElement<JSONObject, ComponentTypeFormElementValue, ComponentTypeSabloValue>,
	IFormElementToTemplateJSON<ComponentTypeFormElementValue, ComponentTypeSabloValue>,
	IFormElementToSabloComponent<ComponentTypeFormElementValue, ComponentTypeSabloValue>, IConvertedPropertyType<ComponentTypeSabloValue>,
	ISabloComponentToRhino<ComponentTypeSabloValue>, ISupportsGranularUpdates<ComponentTypeSabloValue>, ITemplateValueUpdaterType<ComponentTypeSabloValue>
{

	public static final ComponentPropertyType INSTANCE = new ComponentPropertyType(null);

	public static final String TYPE_NAME = "component";

	// START keys and values used in JSON
	public final static String TYPE_NAME_KEY = "typeName";
	public final static String DEFINITION_KEY = "definition";
	public final static String API_CALL_TYPES_KEY = "apiCallTypes";
	public final static String FUNCTION_NAME_KEY = "functionName";

	public final static String CALL_ON_KEY = "callOn";
	public final static int CALL_ON_SELECTED_RECORD = 0;
	public final static int CALL_ON_ALL_RECORDS = 1;

	protected static final String PROPERTY_UPDATES_KEY = "propertyUpdates";
	protected static final String MODEL_KEY = "model";
	protected static final String MODEL_VIEWPORT_KEY = "model_vp";
	protected static final String MODEL_VIEWPORT_CHANGES_KEY = "model_vp_ch";
	protected static final String FOUNDSET_CONFIG_PROPERTY_NAME = "foundsetConfig";
	protected static final String RECORD_BASED_PROPERTIES = "recordBasedProperties";

	public static final String PROPERTY_NAME_KEY = "pn";
	public static final String VALUE_KEY = "v";
	// END keys and values used in JSON

	protected int uniqueId = 1;

	public ComponentPropertyType(PropertyDescription definition)
	{
		super(TYPE_NAME, definition);
	}

	@Override
	public ComponentTypeFormElementValue toFormElementValue(JSONObject designValue, PropertyDescription pd, FlattenedSolution flattenedSolution,
		FormElement fe, PropertyPath propertyPath)
	{
		try
		{
			FormElement element = new FormElement((String)designValue.get(TYPE_NAME_KEY), (JSONObject)designValue.get(DEFINITION_KEY), fe.getForm(),
				fe.getName() + (uniqueId++), flattenedSolution, propertyPath, fe.getDesignId() != null);

			return getFormElementValue(designValue.optJSONArray(API_CALL_TYPES_KEY), pd, propertyPath, element, flattenedSolution);
		}
		catch (JSONException e)
		{
			Debug.error(e);
			return null;
		}
	}

	public ComponentTypeFormElementValue getFormElementValue(JSONArray callTypes, PropertyDescription pd, PropertyPath propertyPath, FormElement element,
		FlattenedSolution flattenedSolution) throws JSONException
	{
		List<String> apisOnAll = null;
		List<String> recordBasedProperties = null;
		if (forFoundsetTypedPropertyName(pd) != null)
		{
			if (callTypes == null) apisOnAll = findCallTypesInApiSpecDefinition(element.getWebComponentSpec().getApiFunctions());
			else
			{
				apisOnAll = new ArrayList<String>();
				for (int j = 0; j < callTypes.length(); j++)
				{
					JSONObject o = callTypes.getJSONObject(j);
					if (o.getInt(CALL_ON_KEY) == CALL_ON_ALL_RECORDS) apisOnAll.add(o.getString(FUNCTION_NAME_KEY));
				}
			}
			recordBasedProperties = findRecordAwareRootProperties(element, flattenedSolution);
		} // else viewPortData and apisOnAll are not relevant
		return new ComponentTypeFormElementValue(element, apisOnAll, recordBasedProperties, propertyPath.currentPathCopy());
	}

	public String forFoundsetTypedPropertyName(PropertyDescription pd)
	{
		return pd.getConfig() instanceof ComponentTypeConfig ? ((ComponentTypeConfig)pd.getConfig()).forFoundset : null;
	}

	protected List<String> findRecordAwareRootProperties(FormElement formElement, FlattenedSolution flattenedSolution)
	{
		List<String> m = new ArrayList<>();

		// tagstrings, valuelists, tab seq, ... must be implemented separately and provided as a
		// viewport containing these values as part of 'components' property
		Set<Entry<String, PropertyDescription>> propertyDescriptors = formElement.getWebComponentSpec().getProperties().entrySet();
		for (Entry<String, PropertyDescription> propertyDescriptorEntry : propertyDescriptors)
		{
			if (propertyDescriptorEntry.getValue().getType() instanceof IDataLinkedType)
			{
				// as these are root-level component properties, their TargetDataLinks will always be cached (only array element values are not cached)
				TargetDataLinks dataLinks = (TargetDataLinks)formElement.getPreprocessedPropertyInfo(IDataLinkedType.class, propertyDescriptorEntry.getValue());
				if (dataLinks != TargetDataLinks.NOT_LINKED_TO_DATA && dataLinks != null && dataLinks.recordLinked)
				{
					m.add(propertyDescriptorEntry.getKey());
				}
			}
		}

		return m;
	}

	protected List<String> findCallTypesInApiSpecDefinition(Map<String, WebComponentApiDefinition> apis)
	{
		List<String> arr = null;
		if (apis != null)
		{
			arr = new ArrayList<String>();
			for (Entry<String, WebComponentApiDefinition> apiMethod : apis.entrySet())
			{
				JSONObject apiConfigOptions = apiMethod.getValue().getCustomConfigOptions();
				if (apiConfigOptions != null && apiConfigOptions.optInt(CALL_ON_KEY, CALL_ON_SELECTED_RECORD) == CALL_ON_ALL_RECORDS)
				{
					arr.add(apiMethod.getKey());
				}
			}
			if (arr.size() == 0) arr = null;
		}
		return arr;
	}

	@Override
	public JSONWriter toTemplateJSONValue(final JSONWriter writer, String key, ComponentTypeFormElementValue formElementValue, PropertyDescription pd,
		DataConversion conversionMarkers, FlattenedSolution fs, FormElement formElement) throws JSONException
	{
		if (conversionMarkers != null) conversionMarkers.convert(ComponentPropertyType.TYPE_NAME); // so that the client knows it must use the custom client side JS for what JSON it gets

		// create children of component as specified by this property
		final FormElement fe = formElementValue.element;
		JSONUtils.addKeyIfPresent(writer, key);

		writer.object();

		writeTemplateJSONContent(writer, formElementValue, forFoundsetTypedPropertyName(pd), fe, new IModelWriter()
		{

			@Override
			public void writeComponentModel() throws JSONException
			{
				// TODO here we could remove record based props from fe.propertiesForTemplateJSON(); but normally record based props will not write any value in template anyway
				TypedData<Map<String, Object>> modelProperties = fe.propertiesForTemplateJSON();
				writer.object();
				JSONUtils.writeDataWithConversions(new FormElementToJSON(fe.getFlattendSolution()), writer, modelProperties.content,
					modelProperties.contentType, fe);
				writer.endObject();
			}

		}, formElementValue.recordBasedProperties);

		writer.endObject();

		return writer;
	}

	protected <ContextT> void writeTemplateJSONContent(JSONWriter writer, ComponentTypeFormElementValue formElementValue, String forFoundsetPropertyType,
		FormElement componentFormElement, IModelWriter modelWriter, List<String> recordBasedProperties) throws JSONException
	{
		if (forFoundsetPropertyType != null) writer.key(FoundsetLinkedPropertyType.FOR_FOUNDSET_PROPERTY_NAME).value(forFoundsetPropertyType);
		writer.key("componentDirectiveName").value(componentFormElement.getTypeName());
		writer.key("name").value(componentFormElement.getName());
		writer.key("model");

		try
		{
			modelWriter.writeComponentModel();
		}
		catch (JSONException | IllegalArgumentException e)
		{
			Debug.error("Problem detected when handling a component's (" + componentFormElement.getTagname() + ") properties / events.", e);
			throw e;
		}

		writer.key("handlers").object();
		for (String handleMethodName : componentFormElement.getHandlers())
		{
			writer.key(handleMethodName);
			JSONObject handlerInfo = new JSONObject();
			handlerInfo.put("formName", componentFormElement.getForm().getName());
			handlerInfo.put("beanName", componentFormElement.getName());
			writer.value(handlerInfo);
		}
		writer.endObject();

		if (forFoundsetPropertyType != null)
		{
			writer.key(MODEL_VIEWPORT_KEY).array().endArray(); // this will contain record based properties for the foundset's viewPort
			writer.key(FOUNDSET_CONFIG_PROPERTY_NAME).object();
			if (recordBasedProperties != null)
			{
				writer.key(RECORD_BASED_PROPERTIES).array();
				for (String propertyName : recordBasedProperties)
				{
					writer.value(propertyName);
				}
				writer.endArray();
			}
			if (formElementValue.apisOnAll != null)
			{
				writer.key(API_CALL_TYPES_KEY).array();
				for (String methodName : formElementValue.apisOnAll)
				{
					writer.object().key(methodName).value(CALL_ON_ALL_RECORDS).endObject();
				}
				writer.endArray();
			}
			writer.endObject();
		}
	}

	@Override
	public ComponentTypeSabloValue toSabloComponentValue(ComponentTypeFormElementValue formElementValue, PropertyDescription pd, FormElement formElement,
		WebFormComponent component, DataAdapterList dal)
	{
		return new ComponentTypeSabloValue(formElementValue, pd, forFoundsetTypedPropertyName(pd));
	}

	@Override
	public ComponentTypeSabloValue fromJSON(Object newJSONValue, ComponentTypeSabloValue previousSabloValue, IDataConverterContext dataConverterContext)
	{
		if (previousSabloValue != null)
		{
			previousSabloValue.browserUpdatesReceived(newJSONValue);
		}
		// else there's nothing to do here / this type can't receive browser updates when server has no value for it

		return previousSabloValue;
	}

	@Override
	public JSONWriter initialToJSON(JSONWriter writer, String key, ComponentTypeSabloValue sabloValue, DataConversion clientConversion,
		IDataConverterContext dataConverterContext) throws JSONException
	{
		// this sends a diff update between the value it has in the template and the initial data requested after runtime components were created or during a page refresh.
		if (sabloValue != null)
		{
			JSONUtils.addKeyIfPresent(writer, key);
			sabloValue.initialToJSON(writer, clientConversion, this);
		}
		return writer;
	}

	@Override
	public JSONWriter changesToJSON(JSONWriter writer, String key, ComponentTypeSabloValue sabloValue, DataConversion clientConversion,
		IDataConverterContext dataConverterContext) throws JSONException
	{
		if (sabloValue != null)
		{
			JSONUtils.addKeyIfPresent(writer, key);
			sabloValue.changesToJSON(writer, clientConversion, this);
		}
		return writer;
	}

	@Override
	public JSONWriter toJSON(JSONWriter writer, String key, ComponentTypeSabloValue sabloValue, DataConversion clientConversion,
		IDataConverterContext dataConverterContext) throws JSONException
	{
		if (sabloValue != null)
		{
			JSONUtils.addKeyIfPresent(writer, key);
			sabloValue.fullToJSON(writer, clientConversion, this);
		}
		return writer;
	}

	@Override
	public boolean isValueAvailableInRhino(ComponentTypeSabloValue webComponentValue, PropertyDescription pd, BaseWebObject componentOrService)
	{
		return false;
	}

	@Override
	public Object toRhinoValue(ComponentTypeSabloValue webComponentValue, PropertyDescription pd, BaseWebObject componentOrService, Scriptable startScriptable)
	{
		return Scriptable.NOT_FOUND;
	}

	@Override
	public Object parseConfig(JSONObject config)
	{
		String tmp = config.optString("forFoundset");
		return tmp == null || tmp.length() == 0 ? null : new ComponentTypeConfig(tmp);
	}


	protected interface IModelWriter
	{
		void writeComponentModel() throws JSONException;
	}

}
