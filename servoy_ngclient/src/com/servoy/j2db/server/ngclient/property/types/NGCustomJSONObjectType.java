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

package com.servoy.j2db.server.ngclient.property.types;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;
import org.mozilla.javascript.Scriptable;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.property.CustomJSONObjectType;
import org.sablo.websocket.utils.DataConversion;
import org.sablo.websocket.utils.JSONUtils;

import com.servoy.j2db.FlattenedSolution;
import com.servoy.j2db.server.ngclient.FormElement;
import com.servoy.j2db.server.ngclient.WebFormComponent;
import com.servoy.j2db.server.ngclient.component.RhinoMapOrArrayWrapper;
import com.servoy.j2db.server.ngclient.property.types.NGConversions.IDesignToFormElement;
import com.servoy.j2db.server.ngclient.property.types.NGConversions.IFormElementToSabloComponent;
import com.servoy.j2db.server.ngclient.property.types.NGConversions.IFormElementToTemplateJSON;
import com.servoy.j2db.server.ngclient.property.types.NGConversions.IRhinoToSabloComponent;
import com.servoy.j2db.server.ngclient.property.types.NGConversions.ISabloComponentToRhino;
import com.servoy.j2db.util.Debug;

/**
 * A JSON array type that is Servoy NG client aware as well.
 * So it adds all conversions from {@link NGConversions}.
 *
 * @author acostescu
 */
//TODO these SabloT, SabloWT and FormElementT are improper - as for object type they can represent multiple types (a different set for each child key), but they help to avoid some bugs at compile-time
public class NGCustomJSONObjectType<SabloT, SabloWT, FormElementT> extends CustomJSONObjectType<SabloT, SabloWT> implements
	IDesignToFormElement<JSONObject, Map<String, FormElementT>, Map<String, SabloT>>,
	IFormElementToTemplateJSON<Map<String, FormElementT>, Map<String, SabloT>>, IFormElementToSabloComponent<Map<String, FormElementT>, Map<String, SabloT>>,
	ISabloComponentToRhino<Map<String, SabloT>>, IRhinoToSabloComponent<Map<String, SabloT>>, ISupportTemplateValue<Map<String, FormElementT>>
{

	public NGCustomJSONObjectType(String typeName, PropertyDescription definition)
	{
		super(typeName, definition);
	}

	@Override
	public Map<String, FormElementT> toFormElementValue(JSONObject designValue, PropertyDescription pd, FlattenedSolution flattenedSolution,
		FormElement formElement, PropertyPath propertyPath)
	{
		if (designValue != null)
		{
			Map<String, FormElementT> formElementValues = new HashMap<>(designValue.length());
			Iterator<String> keys = designValue.keys();
			while (keys.hasNext())
			{
				String key = keys.next();
				try
				{
					propertyPath.add(key);
					formElementValues.put(
						key,
						(FormElementT)NGConversions.INSTANCE.convertDesignToFormElementValue(designValue.get(key),
							getCustomJSONTypeDefinition().getProperty(key), flattenedSolution, formElement, propertyPath));
					propertyPath.backOneLevel();
				}
				catch (JSONException e)
				{
					Debug.error(e);
					formElementValues.put(key, null);
				}
			}
			return formElementValues;
		}
		return null;
	}

	@Override
	public JSONWriter toTemplateJSONValue(JSONWriter writer, String key, Map<String, FormElementT> formElementValue, PropertyDescription pd,
		DataConversion conversionMarkers) throws JSONException
	{
		JSONUtils.addKeyIfPresent(writer, key);
		if (conversionMarkers != null) conversionMarkers.convert(CustomJSONObjectType.TYPE_NAME); // so that the client knows it must use the custom client side JS for what JSON it gets

		if (formElementValue != null)
		{
			writer.object().key(CONTENT_VERSION).value(1).key(VALUE).object();
			DataConversion arrayConversionMarkers = new DataConversion();

			for (Entry<String, FormElementT> e : formElementValue.entrySet())
			{
				arrayConversionMarkers.pushNode(e.getKey());
				NGConversions.INSTANCE.convertFormElementToTemplateJSONValue(writer, e.getKey(), e.getValue(),
					getCustomJSONTypeDefinition().getProperty(e.getKey()), arrayConversionMarkers);
				arrayConversionMarkers.popNode();
			}
			writer.endObject();
			if (arrayConversionMarkers.getConversions().size() > 0)
			{
				writer.key("conversions").object();
				JSONUtils.writeConversions(writer, arrayConversionMarkers.getConversions());
				writer.endObject();
			}
			writer.endObject();
		}
		else
		{
			writer.value(JSONObject.NULL);
		}
		return writer;
	}

	@Override
	public Map<String, SabloT> toSabloComponentValue(Map<String, FormElementT> formElementValue, PropertyDescription pd, FormElement formElement,
		WebFormComponent component)
	{
		if (formElementValue != null)
		{
			Map<String, SabloT> map = new HashMap<>(formElementValue.size());
			for (Entry<String, FormElementT> e : formElementValue.entrySet())
			{
				map.put(e.getKey(), (SabloT)NGConversions.INSTANCE.convertFormElementToSabloComponentValue(e.getValue(),
					getCustomJSONTypeDefinition().getProperty(e.getKey()), formElement, component));
			}
			return map;
		}
		return null;
	}

	@Override
	public Map<String, SabloT> toSabloComponentValue(Object rhinoValue, Map<String, SabloT> previousComponentValue, PropertyDescription pd,
		WebFormComponent component)
	{
		if (rhinoValue == null || rhinoValue == Scriptable.NOT_FOUND) return null;

		if (rhinoValue instanceof RhinoMapOrArrayWrapper)
		{
			return (Map<String, SabloT>)((RhinoMapOrArrayWrapper)rhinoValue).getWrappedValue();
		}
		else
		{
			// if it's some kind of object, convert it (in depth, iterate over children)

			Map<String, Object> rhinoMap = null;

			if (rhinoValue instanceof Scriptable)
			{
				// rhinoValue which is (NativeArray) implements List
				rhinoMap = new HashMap<>();
				Scriptable scriptable = (Scriptable)rhinoValue;
				for (Object id : scriptable.getIds())
				{
					rhinoMap.put(String.valueOf(id),
						id instanceof Number ? scriptable.get(((Number)id).intValue(), scriptable) : scriptable.get((String)id, scriptable));
				}
			}

			if (rhinoMap != null)
			{
				Map<String, SabloT> convertedMap = new HashMap(rhinoMap.size());

				for (Entry<String, Object> e : rhinoMap.entrySet())
				{
					convertedMap.put(
						e.getKey(),
						NGConversions.INSTANCE.convertRhinoToSabloComponentValue(e.getValue(),
							previousComponentValue != null ? previousComponentValue.get(e.getKey()) : null,
							getCustomJSONTypeDefinition().getProperty(e.getKey()), component));
				}

				return convertedMap;
			}
		}
		return previousComponentValue; // or should we return null or throw exception here? incompatible thing was assigned
	}

	@Override
	public boolean isValueAvailableInRhino(Map<String, SabloT> webComponentValue, PropertyDescription pd, WebFormComponent component)
	{
		return true;
	}

	@Override
	public Object toRhinoValue(Map<String, SabloT> webComponentValue, PropertyDescription pd, WebFormComponent component)
	{
		return new RhinoMapOrArrayWrapper(webComponentValue, component, pd.getName(), pd, component.getDataConverterContext());
	}

	@Override
	public boolean valueInTemplate(Map<String, FormElementT> object)
	{
		if (object != null)
		{
			PropertyDescription desc = getCustomJSONTypeDefinition();
			for (Entry<String, PropertyDescription> entry : desc.getProperties().entrySet())
			{
				FormElementT value = object.get(entry.getKey());
				if (value != null && entry.getValue().getType() instanceof ISupportTemplateValue< ? >)
				{
					if (!((ISupportTemplateValue)entry.getValue().getType()).valueInTemplate(value))
					{
						return false;
					}
				}
			}
		}
		return true;
	}

}
