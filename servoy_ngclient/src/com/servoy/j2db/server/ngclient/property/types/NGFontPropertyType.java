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

import java.awt.Font;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;
import org.json.JSONWriter;
import org.mozilla.javascript.Scriptable;
import org.sablo.BaseWebObject;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.property.types.FontPropertyType;
import org.sablo.websocket.utils.DataConversion;

import com.servoy.j2db.FlattenedSolution;
import com.servoy.j2db.server.ngclient.FormElementContext;
import com.servoy.j2db.server.ngclient.INGFormElement;
import com.servoy.j2db.server.ngclient.property.types.NGConversions.IDesignToFormElement;
import com.servoy.j2db.server.ngclient.property.types.NGConversions.IFormElementToTemplateJSON;
import com.servoy.j2db.server.ngclient.property.types.NGConversions.IRhinoToSabloComponent;
import com.servoy.j2db.server.ngclient.property.types.NGConversions.ISabloComponentToRhino;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.IRhinoDesignConverter;
import com.servoy.j2db.util.PersistHelper;

/**
 * @author acostescu
 */
public class NGFontPropertyType extends FontPropertyType implements IDesignToFormElement<JSONObject, Font, Font>, IFormElementToTemplateJSON<Font, Font>,
	IRhinoDesignConverter, IRhinoToSabloComponent<Font>, ISabloComponentToRhino<Font>
{

	public final static NGFontPropertyType NG_INSTANCE = new NGFontPropertyType();

	@Override
	public Font toFormElementValue(JSONObject designValue, PropertyDescription pd, FlattenedSolution flattenedSolution, INGFormElement formElement,
		PropertyPath propertyPath)
	{
		return fromJSON(designValue, null, pd, null, null);
	}

	@Override
	public JSONWriter toTemplateJSONValue(JSONWriter writer, String key, Font formElementValue, PropertyDescription pd,
		DataConversion browserConversionMarkers, FormElementContext formElementContext) throws JSONException
	{
		return toJSON(writer, key, formElementValue, pd, browserConversionMarkers, null);
	}

	@Override
	public Object toDesignValue(Object value, PropertyDescription pd)
	{
		if (value instanceof String)
		{
			Font font = PersistHelper.createFont(value.toString());
			JSONStringer writer = new JSONStringer();
			try
			{
				writer.object();
				toJSON(writer, pd.getName(), font, pd, new DataConversion(), null);
				writer.endObject();
				return new JSONObject(writer.toString()).get(pd.getName());
			}
			catch (JSONException e)
			{
				Debug.error(e);
			}
		}
		return value;
	}

	@Override
	public Object toRhinoValue(Object value, PropertyDescription pd)
	{
		Font font = fromJSON(value, null, pd, null, null);
		return PersistHelper.createFontString(font);
	}

	@Override
	public Font toSabloComponentValue(Object rhinoValue, Font previousComponentValue, PropertyDescription pd, BaseWebObject componentOrService)
	{
		if (rhinoValue instanceof String)
		{
			return PersistHelper.createFont((String)rhinoValue);
		}
		return (Font)(rhinoValue instanceof Font ? rhinoValue : null);
	}

	@Override
	public Object toRhinoValue(Font webComponentValue, PropertyDescription pd, BaseWebObject componentOrService, Scriptable startScriptable)
	{
		return PersistHelper.createFontString(webComponentValue);
	}

	@Override
	public boolean isValueAvailableInRhino(Font webComponentValue, PropertyDescription pd, BaseWebObject componentOrService)
	{
		return true;
	}


}
