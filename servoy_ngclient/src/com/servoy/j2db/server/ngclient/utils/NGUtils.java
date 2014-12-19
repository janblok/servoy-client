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

package com.servoy.j2db.server.ngclient.utils;

import org.json.JSONException;
import org.json.JSONStringer;
import org.sablo.Container;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.property.types.DatePropertyType;
import org.sablo.specification.property.types.DoublePropertyType;
import org.sablo.specification.property.types.LongPropertyType;
import org.sablo.specification.property.types.TypesRegistry;
import org.sablo.websocket.utils.DataConversion;
import org.sablo.websocket.utils.JSONUtils;
import org.sablo.websocket.utils.JSONUtils.IToJSONConverter;

import com.servoy.j2db.FlattenedSolution;
import com.servoy.j2db.FormAndTableDataProviderLookup;
import com.servoy.j2db.persistence.Form;
import com.servoy.j2db.persistence.IColumnTypes;
import com.servoy.j2db.persistence.IDataProvider;
import com.servoy.j2db.persistence.ITable;
import com.servoy.j2db.persistence.RepositoryException;
import com.servoy.j2db.server.ngclient.IWebFormUI;
import com.servoy.j2db.server.ngclient.property.types.ByteArrayResourcePropertyType;
import com.servoy.j2db.server.ngclient.property.types.HTMLStringPropertyType;
import com.servoy.j2db.util.Debug;

/**
 * Utility methods for NGClient.
 *
 * @author acostescu
 */
public abstract class NGUtils
{

	public static final PropertyDescription DATE_DATAPROVIDER_CACHED_PD = new PropertyDescription("Dataprovider (date)",
		TypesRegistry.getType(DatePropertyType.TYPE_NAME));
	public static final PropertyDescription MEDIA_DATAPROVIDER_CACHED_PD = new PropertyDescription("Dataprovider (media)",
		TypesRegistry.getType(ByteArrayResourcePropertyType.TYPE_NAME));
	public static final PropertyDescription INTEGER_DATAPROVIDER_CACHED_PD = new PropertyDescription("Dataprovider (int)",
		TypesRegistry.getType(LongPropertyType.TYPE_NAME));
	public static final PropertyDescription NUMBER_DATAPROVIDER_CACHED_PD = new PropertyDescription("Dataprovider (number)",
		TypesRegistry.getType(DoublePropertyType.TYPE_NAME));
	public static final PropertyDescription TEXT_PARSEHTML_DATAPROVIDER_CACHED_PD = new PropertyDescription("Dataprovider (text/ph)",
		TypesRegistry.getType(HTMLStringPropertyType.TYPE_NAME), Boolean.TRUE);
	public static final PropertyDescription TEXT_NO_PARSEHTML_DATAPROVIDER_CACHED_PD = new PropertyDescription("Dataprovider (text/nph)",
		TypesRegistry.getType(HTMLStringPropertyType.TYPE_NAME), Boolean.FALSE);

	public static PropertyDescription getDataProviderPropertyDescription(String dataProviderName, ITable table, boolean parseHTML)
	{
		if (table == null || dataProviderName == null) return null;
		return getDataProviderPropertyDescription(table.getColumnType(dataProviderName), parseHTML);
	}

	public static PropertyDescription getDataProviderPropertyDescription(String dataProviderName, FlattenedSolution flattenedSolution, Form form, ITable table,
		boolean parseHTMLIfString)
	{
		FormAndTableDataProviderLookup dpLookup = new FormAndTableDataProviderLookup(flattenedSolution, form, table);
		IDataProvider dp = null;
		try
		{
			dp = dpLookup.getDataProvider(dataProviderName);
		}
		catch (RepositoryException e)
		{
			Debug.error(e);
		}
		if (dp != null) return getDataProviderPropertyDescription(dp.getDataProviderType(), parseHTMLIfString);
		return null;
	}

	public static PropertyDescription getDataProviderPropertyDescription(int type, boolean parseHTMLIfString)
	{
		PropertyDescription typePD = null;
		switch (type)
		{
			case IColumnTypes.DATETIME :
				typePD = DATE_DATAPROVIDER_CACHED_PD;
				break;
			case IColumnTypes.MEDIA :
				typePD = MEDIA_DATAPROVIDER_CACHED_PD;
				break;
			case IColumnTypes.INTEGER :
				typePD = INTEGER_DATAPROVIDER_CACHED_PD;
				break;
			case IColumnTypes.NUMBER :
				typePD = NUMBER_DATAPROVIDER_CACHED_PD;
				break;
			case IColumnTypes.TEXT :
			{
				if (parseHTMLIfString) typePD = TEXT_PARSEHTML_DATAPROVIDER_CACHED_PD;
				else typePD = TEXT_NO_PARSEHTML_DATAPROVIDER_CACHED_PD;
				break;
			}
			default :
				break;
		}

		return typePD;
	}

	public static String formChangesToString(Container formUI, IToJSONConverter converter) throws JSONException
	{
		JSONStringer w = new JSONStringer();
		DataConversion conversions = new DataConversion();
		w.object();
		formUI.writeAllComponentsChanges(w, "changes", converter, conversions);
		JSONUtils.writeClientConversions(w, conversions);
		w.endObject();
		return w.toString();
	}

	public static String formComponentPropetriesToString(IWebFormUI formUI, IToJSONConverter converter) throws JSONException
	{
		JSONStringer w = new JSONStringer();
		w.object();
		formUI.writeAllComponentsProperties(w, converter);
		w.endObject();
		return w.toString();
	}


}