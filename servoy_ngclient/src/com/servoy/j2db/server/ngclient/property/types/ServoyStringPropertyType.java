/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2015 Servoy BV

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

import org.json.JSONException;
import org.json.JSONWriter;
import org.sablo.specification.property.IConvertedPropertyType;
import org.sablo.specification.property.IDataConverterContext;
import org.sablo.specification.property.types.StringPropertyType;
import org.sablo.websocket.utils.DataConversion;
import org.sablo.websocket.utils.JSONUtils;

import com.servoy.j2db.server.ngclient.IContextProvider;

/**
 * @author lvostinar
 *
 */
public class ServoyStringPropertyType extends StringPropertyType implements IConvertedPropertyType<String>
{

	public static final ServoyStringPropertyType INSTANCE = new ServoyStringPropertyType();

	@Override
	public String fromJSON(Object newJSONValue, String previousSabloValue, IDataConverterContext dataConverterContext)
	{
		return (String)newJSONValue;
	}

	@Override
	public JSONWriter toJSON(JSONWriter writer, String key, String sabloValue, DataConversion clientConversion, IDataConverterContext dataConverterContext)
		throws JSONException
	{
		JSONUtils.addKeyIfPresent(writer, key);
		String value = sabloValue;
		if (dataConverterContext.getWebObject() instanceof IContextProvider)
		{
			value = ((IContextProvider)dataConverterContext.getWebObject()).getDataConverterContext().getApplication().getI18NMessageIfPrefixed(value);
		}
		writer.value(value);
		return writer;
	}

}