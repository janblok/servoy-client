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
import org.sablo.websocket.utils.DataConversion;
import org.sablo.websocket.utils.JSONUtils;

import com.servoy.j2db.FlattenedSolution;
import com.servoy.j2db.server.ngclient.FormElement;
import com.servoy.j2db.server.ngclient.IServoyDataConverterContext;
import com.servoy.j2db.server.ngclient.WebFormComponent;
import com.servoy.j2db.server.ngclient.property.types.IRecordAwareType;
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
	ISabloComponentToRhino<ComponentTypeSabloValue>
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
				fe.getName() + (uniqueId++), fe.getDataConverterContext(), propertyPath);

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
			recordBasedProperties = findRecordAwareProperties(element, flattenedSolution);
		} // else viewPortData and apisOnAll are not relevant
		return new ComponentTypeFormElementValue(element, apisOnAll, recordBasedProperties, propertyPath.currentPathCopy());
	}

	public String forFoundsetTypedPropertyName(PropertyDescription pd)
	{
		return pd.getConfig() instanceof ComponentTypeConfig ? ((ComponentTypeConfig)pd.getConfig()).forFoundsetTypedProperty : null;
	}

	protected List<String> findRecordAwareProperties(FormElement formElement, FlattenedSolution flattenedSolution)
	{
		List<String> m = new ArrayList<>();

		// tagstrings, valuelists, tab seq, ... must be implemented separately and provided as a
		// viewport containing these values as part of 'components' property
		Set<Entry<String, PropertyDescription>> propertyDescriptors = formElement.getWebComponentSpec().getProperties().entrySet();
		for (Entry<String, PropertyDescription> propertyDescriptorEntry : propertyDescriptors)
		{
			if (propertyDescriptorEntry.getValue().getType() instanceof IRecordAwareType)
			{
				IRecordAwareType type = (IRecordAwareType< ? >)propertyDescriptorEntry.getValue().getType();
				if (type.isLinkedToRecord(formElement.getPropertyValue(propertyDescriptorEntry.getKey()), propertyDescriptorEntry.getValue(),
					flattenedSolution, formElement))
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
	public JSONWriter toTemplateJSONValue(JSONWriter writer, String key, ComponentTypeFormElementValue formElementValue, PropertyDescription pd,
		DataConversion conversionMarkers, IServoyDataConverterContext servoyDataConverterContext) throws JSONException
	{
		if (conversionMarkers != null) conversionMarkers.convert(ComponentPropertyType.TYPE_NAME); // so that the client knows it must use the custom client side JS for what JSON it gets

		// create children of component as specified by this property
		FormElement fe = formElementValue.element;
		JSONUtils.addKeyIfPresent(writer, key);
		writer.object();

		writer.key("componentDirectiveName").value(fe.getTypeName());
		writer.key("name").value(fe.getName());
		writer.key("model");
		fe.propertiesAsTemplateJSON(writer); // full to json always uses design values
		writer.key("handlers").object();
		for (String handleMethodName : fe.getHandlers())
		{
			writer.key(handleMethodName);
			JSONObject handlerInfo = new JSONObject();
			handlerInfo.put("formName", fe.getForm().getName());
			handlerInfo.put("beanName", fe.getName());
			writer.value(handlerInfo);
		}
		writer.endObject();

		if (forFoundsetTypedPropertyName(pd) != null)
		{
			writer.key(MODEL_VIEWPORT_KEY).array().endArray(); // this will contain record based properties for the foundset's viewPort
			writer.key("forFoundset").object();
			if (formElementValue.recordBasedProperties != null)
			{
				writer.key("recordBasedProperties").array();
				for (String propertyName : formElementValue.recordBasedProperties)
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

		writer.endObject();

		return writer;
	}

	@Override
	public ComponentTypeSabloValue toSabloComponentValue(ComponentTypeFormElementValue formElementValue, PropertyDescription pd, FormElement formElement,
		WebFormComponent component)
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
	public JSONWriter toJSON(JSONWriter writer, String key, ComponentTypeSabloValue sabloValue, DataConversion clientConversion) throws JSONException
	{
		if (sabloValue != null)
		{
			JSONUtils.addKeyIfPresent(writer, key);
			sabloValue.changesToJSON(writer, clientConversion, this);
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
		String tmp = config.optString("forFoundsetTypedProperty");
		return tmp == null ? null : new ComponentTypeConfig(tmp);
	}

}
