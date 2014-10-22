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

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.property.IDataConverterContext;
import org.sablo.specification.property.IWrapperType;
import org.sablo.websocket.utils.DataConversion;
import org.sablo.websocket.utils.JSONUtils;

import com.servoy.j2db.FlattenedSolution;
import com.servoy.j2db.server.ngclient.FormElement;
import com.servoy.j2db.server.ngclient.HTMLTagsConverter;
import com.servoy.j2db.server.ngclient.IContextProvider;
import com.servoy.j2db.server.ngclient.IServoyDataConverterContext;
import com.servoy.j2db.server.ngclient.property.types.NGConversions.IFormElementToTemplateJSON;
import com.servoy.j2db.server.ngclient.property.types.TagStringPropertyType.TagStringWrapper;
import com.servoy.j2db.util.HtmlUtils;


/**
 * @author jcompagner
 *
 */
public class TagStringPropertyType implements IWrapperType<Object, TagStringWrapper>, IFormElementToTemplateJSON<Object, Object>,
	ISupportTemplateValue<String>, IRecordAwareType<String>
{

	public static final TagStringPropertyType INSTANCE = new TagStringPropertyType();
	public static final String TYPE_NAME = "tagstring";

	private TagStringPropertyType()
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
		return Boolean.valueOf(json != null && json.optBoolean("hidetags"));
	}

	@Override
	public JSONWriter toTemplateJSONValue(JSONWriter writer, String key, Object formElementValue, PropertyDescription pd,
		DataConversion browserConversionMarkers, IServoyDataConverterContext servoyDataConverterContext) throws JSONException
	{
		// TODO when type has more stuff added to it, see if this needs to be changed (what is put in form cached templates for such properties)
		if (formElementValue != null && valueInTemplate((String)formElementValue))
		{
			JSONUtils.addKeyIfPresent(writer, key);
			if (HtmlUtils.startsWithHtml(formElementValue))
			{
				// TODO - it could still return "value" if we know HTMLTagsConverter.convert() would not want to touch that (so simple HTML)
				// design-time wrap (used by FormElement); no component available
				// return empty value as we don't want to expose the actual design-time stuff that would normally get encrypted by HTMLTagsConverter.convert() or is not yet valid (blobloader without an application instance for example).
				writer.value("<html></html>");
			}
			else writer.value(formElementValue);
		}

		return writer;
	}

	@Override
	public TagStringWrapper fromJSON(Object newValue, TagStringWrapper previousValue, IDataConverterContext dataConverterContext)
	{
		return wrap(newValue, previousValue, dataConverterContext);
	}

	@Override
	public JSONWriter toJSON(JSONWriter writer, String key, TagStringWrapper object, DataConversion clientConversion) throws JSONException
	{
		if (object != null)
		{
			JSONUtils.addKeyIfPresent(writer, key);
			writer.value(object.getJsonValue());
		}
		return writer;
	}

	@Override
	public TagStringWrapper defaultValue()
	{
		return null;
	}

	@Override
	public Object unwrap(TagStringWrapper value)
	{
		return value != null ? value.value : null;
	}

	@Override
	public TagStringWrapper wrap(Object value, TagStringWrapper previousValue, IDataConverterContext dataConverterContext)
	{
		return new TagStringWrapper(value, dataConverterContext);
	}

	@Override
	public boolean valueInTemplate(String str)
	{
		return !(str.contains("%%") || str.startsWith("i18n:"));
	}

	class TagStringWrapper
	{
		final Object value;
		IServoyDataConverterContext dataConverterContext;
		Object jsonValue;

		TagStringWrapper(Object value)
		{
			this(value, null);
		}

		TagStringWrapper(Object value, IDataConverterContext dataConverterContext)
		{
			this.value = value;
			this.dataConverterContext = ((IContextProvider)dataConverterContext.getWebObject()).getDataConverterContext();
		}

		Object getJsonValue()
		{
			if (jsonValue == null)
			{
				if (HtmlUtils.startsWithHtml(value))
				{
					if (dataConverterContext != null && dataConverterContext.getSolution() != null && dataConverterContext.getApplication() != null)
					{
						jsonValue = HTMLTagsConverter.convert(value.toString(), dataConverterContext, false);
					}
				}
				else if (value != null && value.toString().startsWith("i18n:"))
				{
					if (dataConverterContext != null && dataConverterContext.getApplication() != null)
					{
						jsonValue = dataConverterContext.getApplication().getI18NMessage(value.toString().substring(5));
					}
					else return value;
				}
				else
				{
					jsonValue = value;
				}
			}

			return jsonValue;
		}
	}

	@Override
	public boolean isLinkedToRecord(String formElementValue, PropertyDescription pd, FlattenedSolution flattenedSolution, FormElement formElement)
	{
		if (formElementValue == null) return false;
		return formElementValue.contains("%%");
	}
}
