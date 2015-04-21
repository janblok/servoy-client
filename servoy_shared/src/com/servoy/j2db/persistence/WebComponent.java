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


import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.sablo.specification.WebComponentSpecProvider;
import org.sablo.specification.WebComponentSpecification;
import org.sablo.specification.property.IPropertyType;

import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.ServoyJSONObject;
import com.servoy.j2db.util.UUID;

/**
 * @author gboros
 */
public class WebComponent extends Bean implements IWebObject
{
	/**
	 * Constructor I
	 */
	protected WebComponent(ISupportChilds parent, int element_id, UUID uuid)
	{
		super(IRepository.WEBCOMPONENTS, parent, element_id, uuid);
	}

	@Override
	public boolean isChanged()
	{
		boolean changed = super.isChanged();

		if (!changed)
		{
			for (IPersist p : getAllCustomProperties())
			{
				if (p.isChanged())
				{
					changed = true;
					updateCustomProperties();
					break;
				}
			}
		}

		return changed;
	}

	private void updateCustomProperties()
	{
		if (isCustomTypePropertiesLoaded && getCustomTypeProperties().size() > 0)
		{
			String beanXML = getBeanXML();
			try
			{
				ServoyJSONObject entireModel = beanXML != null ? new ServoyJSONObject(beanXML, false) : new ServoyJSONObject();

				for (Map.Entry<String, Object> wo : getCustomTypeProperties().entrySet())
				{
					if (wo.getValue() instanceof WebCustomType)
					{
						entireModel.put(wo.getKey(), ((WebCustomType)wo.getValue()).getJson());
					}
					else
					{
						JSONArray jsonArray = new JSONArray();
						for (WebCustomType wo1 : (WebCustomType[])wo.getValue())
						{
							jsonArray.put(wo1.getJson());
						}
						entireModel.put(wo.getKey(), jsonArray);
					}
				}

				beanXML = entireModel.toString();
			}
			catch (JSONException ex)
			{
				Debug.error(ex);
			}
			setBeanXML(beanXML);
		}
	}

	@Override
	public void setProperty(String propertyName, Object val)
	{
		if (val instanceof WebCustomType || val instanceof WebCustomType[])
		{
			Map<String, Object> ctp = getCustomTypeProperties();
			ctp.put(propertyName, val);
		}
		else super.setProperty(propertyName, val);
	}

	@Override
	public Object getProperty(String propertyName)
	{
		if (!"beanXML".equals(propertyName) && !"beanClassName".equals(propertyName) && !"json".equals(propertyName) && !"typeName".equals(propertyName)) //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		{
			Map<String, Object> ctp = getCustomTypeProperties();
			if (ctp.containsKey(propertyName)) return ctp.get(propertyName);
		}
		return super.getProperty(propertyName);
	}

	public List<IPersist> getAllCustomProperties()
	{
		ArrayList<IPersist> allCustomProperties = new ArrayList<IPersist>();
		for (Object wo : getCustomTypeProperties().values())
		{
			if (wo instanceof WebCustomType[])
			{
				allCustomProperties.addAll(Arrays.asList((WebCustomType[])wo));
			}
			else
			{
				allCustomProperties.add((WebCustomType)wo);
			}
		}

		return allCustomProperties;
	}

	private final Map<String, Object> customTypeProperties = new HashMap<String, Object>();
	private boolean isCustomTypePropertiesLoaded = false;

	private Map<String, Object> getCustomTypeProperties()
	{
		if (!isCustomTypePropertiesLoaded)
		{
			String beanClassName = getBeanClassName();
			WebComponentSpecification spec = WebComponentSpecProvider.getInstance() != null
				? WebComponentSpecProvider.getInstance().getWebComponentSpecification(beanClassName) : null;

			if (beanClassName != null && spec != null)
			{
				String beanXML = getBeanXML();
				if (beanXML != null)
				{
					Map<String, IPropertyType< ? >> foundTypes = spec.getFoundTypes();
					try
					{
						JSONObject beanJSON = new JSONObject(beanXML);
						for (String beanJSONKey : JSONObject.getNames(beanJSON))
						{
							Object object = beanJSON.get(beanJSONKey);
							if (object != null)
							{
								IPropertyType< ? > propertyType = spec.getProperty(beanJSONKey).getType();
								String simpleTypeName = propertyType.getName().replaceFirst(spec.getName() + ".", ""); //$NON-NLS-1$//$NON-NLS-2$
								if (foundTypes.containsKey(simpleTypeName))
								{
									boolean arrayReturnType = spec.isArrayReturnType(beanJSONKey);
									if (!arrayReturnType)
									{
										WebCustomType webObject = new WebCustomType(this, beanJSONKey, simpleTypeName, -1, arrayReturnType, false);
										webObject.setTypeName(simpleTypeName);
										customTypeProperties.put(beanJSONKey, webObject);
									}
									else if (object instanceof JSONArray)
									{
										ArrayList<WebCustomType> webObjects = new ArrayList<WebCustomType>();
										for (int i = 0; i < ((JSONArray)object).length(); i++)
										{
											WebCustomType webObject = new WebCustomType(this, beanJSONKey, simpleTypeName, i, arrayReturnType, false);
											webObject.setTypeName(simpleTypeName);
											webObjects.add(webObject);
										}
										customTypeProperties.put(beanJSONKey, webObjects.toArray(new WebCustomType[webObjects.size()]));
									}
								}
							}
						}
					}
					catch (JSONException e)
					{
						Debug.error(e);
					}
				}
				isCustomTypePropertiesLoaded = true;
			}
		}

		return customTypeProperties;
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
}