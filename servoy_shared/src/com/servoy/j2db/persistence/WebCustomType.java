/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2010 Servoy BV

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
package com.servoy.j2db.persistence;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.ServoyJSONObject;
import com.servoy.j2db.util.UUID;

/**
 *
 * @author gboros
 */
public class WebCustomType extends Bean implements IWebObject
{
	private final Bean parentBean;
	private final String jsonKey;
	private final String typeName;
	private final int index;

	/**
	 * @param newBean
	 * @param b
	 * @param parent
	 * @param element_id
	 * @param uuid
	 */
	public WebCustomType(Bean parentBean, String jsonKey, String typeName, int index, boolean isArray, boolean isNew)
	{
		//we just tell the GhostBean that it has a parent, we do not tell the parent that it contains a GhostBean
		super(IRepository.WEBCUSTOMTYPES, parentBean.getParent(), 0, UUID.randomUUID());
		this.parentBean = parentBean;
		this.jsonKey = jsonKey;
		this.typeName = typeName;
		this.index = index;

		try
		{
			ServoyJSONObject entireModel = parentBean.getBeanXML() != null ? new ServoyJSONObject(parentBean.getBeanXML(), false) : new ServoyJSONObject();
			if (entireModel.has(jsonKey))
			{
				Object v = entireModel.get(jsonKey);
				if (v instanceof JSONArray)
				{
					setJson(((JSONArray)v).getJSONObject(index));
				}
				else
				{
					setJson(entireModel.getJSONObject(jsonKey));
				}
			}
			else
			{
				setJson(new ServoyJSONObject());
			}
		}
		catch (JSONException e)
		{
			Debug.error(e);
		}
	}

	public int getIndex()
	{
		return index;
	}

	@Override
	public void setBeanXML(String arg)
	{
		try
		{
			setJson(new ServoyJSONObject(arg, false));
		}
		catch (JSONException ex)
		{
			Debug.error(ex);
		}
	}

	@Override
	public String getBeanXML()
	{
		return getJson() != null ? getJson().toString() : null;
	}

	@Override
	public void setBeanClassName(String arg)
	{
		setTypeName(arg);
	}

	@Override
	public String getBeanClassName()
	{
		return getTypeName();
	}

	public void setTypeName(String arg)
	{
		setTypedProperty(StaticContentSpecLoader.PROPERTY_TYPENAME, arg);
	}

	public String getTypeName()
	{
		return getTypedProperty(StaticContentSpecLoader.PROPERTY_TYPENAME);
	}

	public void setJson(JSONObject arg)
	{
		setTypedProperty(StaticContentSpecLoader.PROPERTY_JSON, arg);
	}

	public JSONObject getJson()
	{
		return getTypedProperty(StaticContentSpecLoader.PROPERTY_JSON);
	}

	@Override
	public boolean hasProperty(String propertyName)
	{
		if ("anchors".equals(propertyName) || "size".equals(propertyName) || "location".equals(propertyName))
		{
			return false;
		}
		return super.hasProperty(propertyName);
	}

	public Bean getParentBean()
	{
		return parentBean;
	}

	/**
	 * @return
	 */
	public String getUUIDString()
	{
		String addIndex = "";
		if (index >= 0) addIndex = "[" + index + "]";
		return parentBean.getUUID() + "_" + jsonKey + addIndex + "_" + typeName;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.servoy.j2db.persistence.AbstractBase#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj)
	{
		if (obj instanceof WebCustomType)
		{
			return ((WebCustomType)obj).getUUIDString().equals(this.getUUIDString());
		}
		return super.equals(obj);
	}

	@Override
	public int getExtendsID()
	{
		return 0;
	}
}