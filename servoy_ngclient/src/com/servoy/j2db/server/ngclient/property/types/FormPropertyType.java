/*
 * Copyright (C) 2014 Servoy BV
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.servoy.j2db.server.ngclient.property.types;

import java.util.Iterator;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;
import org.mozilla.javascript.Scriptable;
import org.sablo.BaseWebObject;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.property.IBrowserConverterContext;
import org.sablo.specification.property.IConvertedPropertyType;
import org.sablo.specification.property.types.DefaultPropertyType;
import org.sablo.util.ValueReference;
import org.sablo.websocket.utils.DataConversion;

import com.servoy.j2db.FlattenedSolution;
import com.servoy.j2db.FormController;
import com.servoy.j2db.persistence.Form;
import com.servoy.j2db.scripting.FormScope;
import com.servoy.j2db.server.ngclient.FormElementContext;
import com.servoy.j2db.server.ngclient.IContextProvider;
import com.servoy.j2db.server.ngclient.property.types.NGConversions.IFormElementToTemplateJSON;
import com.servoy.j2db.server.ngclient.property.types.NGConversions.ISabloComponentToRhino;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.UUID;
import com.servoy.j2db.util.Utils;

/**
 * @author jcompagner
 */
public class FormPropertyType extends DefaultPropertyType<Object>
	implements IConvertedPropertyType<Object>, ISabloComponentToRhino<Object>, IFormElementToTemplateJSON<Object, Object>
{
	public static final FormPropertyType INSTANCE = new FormPropertyType();
	public static final String TYPE_NAME = "form";

	protected FormPropertyType()
	{
	}

	@Override
	public String getName()
	{
		return TYPE_NAME;
	}

	@Override
	public Object parseConfig(JSONObject json)
	{
		return json;
	}

	@Override
	public Object fromJSON(Object newJSONValue, Object previousSabloValue, PropertyDescription pd, IBrowserConverterContext dataConverterContext,
		ValueReference<Boolean> returnValueAdjustedIncommingValue)
	{
		if (newJSONValue instanceof JSONObject)
		{
			Iterator<String> it = ((JSONObject)newJSONValue).keys();
			if (it.hasNext())
			{
				String key = it.next();
				try
				{
					return ((JSONObject)newJSONValue).get(key);
				}
				catch (JSONException e)
				{
					Debug.error(e);
				}
			}
		}
		return newJSONValue;
	}

	@Override
	public JSONWriter toJSON(JSONWriter writer, String key, Object sabloValue, PropertyDescription pd, DataConversion clientConversion,
		IBrowserConverterContext dataConverterContext) throws JSONException
	{
		if (key != null)
		{
			writer.key(key);
		}
		if (sabloValue instanceof String)
		{
			// form name
			writer.value(sabloValue);
		}
		else if (sabloValue instanceof CharSequence)
		{
			writer.value(((CharSequence)sabloValue).toString());
		}
		else if (sabloValue instanceof Form)
		{
			writer.value(((Form)sabloValue).getName());
		}
		else if (sabloValue instanceof FormController)
		{
			writer.value(((FormController)sabloValue).getName());
		}
		else if (sabloValue instanceof FormScope)
		{
			writer.value(((FormScope)sabloValue).getFormController().getName());
		}
		else
		{
			Debug.error("Cannot handle unknown value for Form type: " + sabloValue);
			writer.value(null);
		}
		return writer;
	}

	@Override
	public boolean isValueAvailableInRhino(Object webComponentValue, PropertyDescription pd, BaseWebObject componentOrService)
	{
		return true;
	}

	@Override
	public Object toRhinoValue(Object webComponentValue, PropertyDescription pd, BaseWebObject componentOrService, Scriptable startScriptable)
	{
		if (webComponentValue instanceof Form)
		{
			return ((Form)webComponentValue).getName();
		}
		else
		{
			// form is stored as uuid on disk
			UUID uuid = Utils.getAsUUID(webComponentValue, false);
			if (uuid != null && componentOrService instanceof IContextProvider)
			{
				Form form = (Form)((IContextProvider)componentOrService).getDataConverterContext().getSolution().searchPersist(uuid);
				if (form != null)
				{
					return form.getName();
				}
			}
		}
		return webComponentValue;
	}

	@Override
	public String defaultValue(PropertyDescription pd)
	{
		return null;
	}

	@Override
	public JSONWriter toTemplateJSONValue(JSONWriter writer, String key, Object formElementValue, PropertyDescription pd,
		DataConversion browserConversionMarkers, FormElementContext formElementContext) throws JSONException
	{
		FlattenedSolution fs = formElementContext.getFlattenedSolution();

		Form form = null;
		if (formElementValue instanceof Integer)
		{
			form = fs.getForm(((Integer)formElementValue).intValue());
		}
		else if (formElementValue instanceof String)
		{

			UUID uuid = Utils.getAsUUID(formElementValue, false);
			if (uuid != null) form = (Form)fs.searchPersist(uuid);
			else form = fs.getForm((String)formElementValue);
		}
		if (form != null)
		{
			writer.key(key);
			writer.value(form.getName());
		}
		return writer;
	}
}
