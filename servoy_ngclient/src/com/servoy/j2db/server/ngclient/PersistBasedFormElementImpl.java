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

package com.servoy.j2db.server.ngclient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.property.CustomJSONArrayType;

import com.servoy.j2db.component.ComponentFactory;
import com.servoy.j2db.persistence.AbstractBase;
import com.servoy.j2db.persistence.Bean;
import com.servoy.j2db.persistence.Field;
import com.servoy.j2db.persistence.Form;
import com.servoy.j2db.persistence.IFlattenedPersistWrapper;
import com.servoy.j2db.persistence.IFormElement;
import com.servoy.j2db.persistence.IPersist;
import com.servoy.j2db.persistence.ISupportChilds;
import com.servoy.j2db.persistence.ISupportExtendsID;
import com.servoy.j2db.persistence.Portal;
import com.servoy.j2db.persistence.StaticContentSpecLoader;
import com.servoy.j2db.persistence.Tab;
import com.servoy.j2db.persistence.TabPanel;
import com.servoy.j2db.server.ngclient.property.ComponentPropertyType;
import com.servoy.j2db.server.ngclient.property.types.DataproviderPropertyType;
import com.servoy.j2db.server.ngclient.property.types.PropertyPath;
import com.servoy.j2db.util.ComponentFactoryHelper;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.PersistHelper;

/**
 * Form element that is based on a persist.
 *
 * @author acostescu
 */
class PersistBasedFormElementImpl
{
	private final IPersist persist;
	private final FormElement formElement;

	PersistBasedFormElementImpl(IPersist persist, FormElement formElement)
	{
		this.persist = persist;
		this.formElement = formElement;
	}

	public Form getForm()
	{
		if (persist instanceof Form) return (Form)persist;
		ISupportChilds parent = persist.getParent();
		while (parent != null && !(parent instanceof Form))
		{
			parent = parent.getParent();
		}
		return (Form)parent;
	}

	public Map<String, Object> getFormElementPropertyValues(IServoyDataConverterContext context, Map<String, PropertyDescription> specProperties,
		PropertyPath propertyPath)
	{
		if (persist instanceof Bean)
		{
			String customJSONString = ((Bean)persist).getBeanXML();
			if (customJSONString != null)
			{
				// convert from persist design-time value (which might be non-json) to the expected value
				Map<String, Object> jsonMap = processPersistProperties(context, specProperties, propertyPath);

				jsonMap.remove(StaticContentSpecLoader.PROPERTY_BEANXML); // this is handled separately as NG component definition
				try
				{
					// add beanXML (which is actually a JSON string here) defined properties to the map
					JSONObject jsonProperties = new JSONObject(customJSONString);
					formElement.convertFromJSONToFormElementValues(context, specProperties, jsonMap, formElement.getWebComponentSpec().getHandlers(),
						jsonProperties, propertyPath);
				}
				catch (Exception ex)
				{
					Debug.error("Error while parsing bean design json", ex);
					jsonMap.put("error", "Error while parsing bean design json(bean not supported in NGClient?): " + persist);
				}
				return jsonMap;
			}
			else
			{
				Map<String, Object> defaultProperties = new HashMap<String, Object>();
				defaultProperties.put(StaticContentSpecLoader.PROPERTY_SIZE.getPropertyName(), ((Bean)persist).getSize());
				defaultProperties.put(StaticContentSpecLoader.PROPERTY_NAME.getPropertyName(), ((Bean)persist).getName());
				defaultProperties.put("error", "Bean not supported in NGClient: " + persist);
				return defaultProperties;
			}
		}
		else if (persist instanceof AbstractBase)
		{
			Map<String, Object> map = processPersistProperties(context, specProperties, propertyPath);
			if (persist instanceof Field && ((Field)persist).getDisplayType() == Field.MULTISELECT_LISTBOX)
			{
				map.put("multiselectListbox", Boolean.TRUE);
			}
			else if (persist instanceof TabPanel)
			{
				convertFromTabPanelToNGProperties((IFormElement)persist, context, map, specProperties, propertyPath);
			}
			else if (persist instanceof Portal)
			{
				convertFromPortalToNGProperties((IFormElement)persist, context, map, specProperties, propertyPath);
			}
			return map;
		}
		else
		{
			return Collections.emptyMap();
		}
	}

	private Map<String, Object> processPersistProperties(IServoyDataConverterContext context, Map<String, PropertyDescription> specProperties,
		PropertyPath propertyPath)
	{
		Map<String, Object> jsonMap = convertSpecialPersistProperties(getFlattenedPropertiesMap(), context, specProperties);
		formElement.convertFromPersistPrimitivesToFormElementValues(context, specProperties, formElement.getWebComponentSpec().getHandlers(), jsonMap,
			propertyPath);
		return jsonMap;
	}

	/**
	 * Applies 'Conversion 0' (see https://support.servoy.com/browse/SVY-6666 attachment) to persist property values - from design to FormElement value. 'Conversion 0' is currently hardcoded as it's limited
	 * to a small set of persist properties - that are not likely to be added to when ng types develop further.
	 *
	 * AbstractBase.getPropertiesMap() returns for format, borderType etc the STRING representation instead of the high level class representation used in ngClient.
	 * It doesn't return JSON either so we can't use 'Conversion 1' type converters in this case.
	 *
	 * Converts string representation to high level Class representation of properties that will be the FormElement value of that property.
	 */
	private Map<String, Object> convertSpecialPersistProperties(Map<String, Object> propertiesMap, final IServoyDataConverterContext context,
		Map<String, PropertyDescription> specProperties)
	{
		// it is a bit strange here as from Persist we get
		// 1. primitive values (some of which might need no conversion and some of which in NG client world need to be converted - for example TagStringPropertyType);
		//    primitives are not affected by this method and can further undergo 'Conversion 1'
		// 2. actual values (like Color) - that will need no conversion; if in the future a 'Conversion 1' is needed for such a value then this method should convert
		//    that value to JSON format first that can be used by 'Conversion 1'
		// 3. (serialized to String) values like borders (see ComponentFactoryHelper.createBorder()) that need to be converted to actual values; once that is done,
		//    it is the same as "2" above
		Map<String, Object> convPropertiesMap = new HashMap<>();
		for (String pv : propertiesMap.keySet())
		{
			Object val = propertiesMap.get(pv);
			if (val == null) continue;
			// the ones in this switch are converted from
			switch (pv)
			{
				case com.servoy.j2db.persistence.IContentSpecConstants.PROPERTY_BORDERTYPE : //PropertyType.border.getType
					val = ComponentFactoryHelper.createBorder((String)val);
					break;
				case com.servoy.j2db.persistence.IContentSpecConstants.PROPERTY_FONTTYPE : //PropertyType.font.getType
					val = PersistHelper.createFont((String)val);
					break;
				default :
					// use default
					break;
			}

			convPropertiesMap.put(pv, val);
		}

		return convPropertiesMap;
	}

	Map<String, Object> getFlattenedPropertiesMap()
	{
		IPersist p = persist;
		if (p instanceof IFlattenedPersistWrapper)
		{
			p = ((IFlattenedPersistWrapper< ? >)p).getWrappedPersist();
		}
		if (p instanceof ISupportExtendsID)
		{
			return ((ISupportExtendsID)p).getFlattenedPropertiesMap();
		}
		return ((AbstractBase)p).getPropertiesMap();
	}

	private void putAndConvertProperty(String propName, Object val, Map<String, Object> map, final IServoyDataConverterContext context,
		PropertyDescription desc, PropertyPath propertyPath)
	{
		formElement.convertDesignToFormElementValueAndPut(context, desc, map, propName, val, propertyPath);
	}

	private void convertFromTabPanelToNGProperties(IFormElement persist, final IServoyDataConverterContext context, Map<String, Object> map,
		Map<String, PropertyDescription> specProperties, PropertyPath propertyPath)
	{
		ArrayList<Map<String, Object>> tabList = new ArrayList<>();
		// add the tabs.
		Iterator<IPersist> tabs = ((TabPanel)persist).getTabs();
		putAndConvertProperty("tabIndex", 1, map, context, specProperties.get("tabIndex"), propertyPath);
		PropertyDescription tabSpecProperties = specProperties.get("tabs");
		boolean active = true;
		while (tabs.hasNext())
		{
			Map<String, Object> tabMap = new HashMap<>();
			Tab tab = (Tab)tabs.next();
			putAndConvertProperty("text", tab.getText(), tabMap, context, tabSpecProperties.getProperty("text"), propertyPath);
			putAndConvertProperty("relationName", tab.getRelationName(), tabMap, context, tabSpecProperties.getProperty("relationName"), propertyPath);
			putAndConvertProperty("active", Boolean.valueOf(active), tabMap, context, tabSpecProperties.getProperty("active"), propertyPath);
			putAndConvertProperty("foreground", tab.getForeground(), tabMap, context, tabSpecProperties.getProperty("foreground"), propertyPath);
			putAndConvertProperty("name", tab.getName(), tabMap, context, tabSpecProperties.getProperty("name"), propertyPath);
			putAndConvertProperty("mnemonic", tab.getMnemonic(), tabMap, context, tabSpecProperties.getProperty("mnemonic"), propertyPath);
			int containsFormID = tab.getContainsFormID();
			// TODO should this be resolved way later on?
			// if solution model then this form can change..
			Form form = context.getSolution().getForm(containsFormID);
			putAndConvertProperty("containsFormId", form.getName(), tabMap, context, tabSpecProperties.getProperty("containsFormId"), propertyPath);
			putAndConvertProperty("disabled", false, tabMap, context, tabSpecProperties.getProperty("disabled"), propertyPath);
			int orient = ((TabPanel)persist).getTabOrientation();
			if (orient != TabPanel.SPLIT_HORIZONTAL && orient != TabPanel.SPLIT_VERTICAL)
			{
				int tabMediaID = tab.getImageMediaID();
				if (tabMediaID > 0)
				{
					putAndConvertProperty("imageMediaID", Integer.valueOf(tabMediaID), tabMap, context, tabSpecProperties.getProperty("imageMediaID"),
						propertyPath);
				}
			}
			tabList.add(tabMap);
			active = false;
		}
		map.put("tabs", tabList.toArray());
	}

	private void convertFromPortalToNGProperties(IFormElement portalPersist, final IServoyDataConverterContext context, Map<String, Object> map,
		Map<String, PropertyDescription> specProperties, PropertyPath propertyPath)
	{
		try
		{
			map.remove("relationName");

			Portal portal = ((Portal)portalPersist);
			JSONObject relatedFoundset = new JSONObject();
			relatedFoundset.put("foundsetSelector", portal.getRelationName());

			// get property type 'foundset'
			PropertyDescription pd = specProperties.get("relatedFoundset");
			if (pd == null)
			{
				Debug.error(new RuntimeException("Cannot find foundset special type to use for portal."));
				return;
			}
			else
			{
				putAndConvertProperty("relatedFoundset", relatedFoundset, map, context, pd, propertyPath);
			}

//			components: 'component[]',
//			component: {
//				definition: 'componentDef',
//				(...)
//			},

			// get property type 'component definition'
			pd = specProperties.get("childElements");
			if (pd != null) pd = ((CustomJSONArrayType< ? , ? >)pd.getType()).getCustomJSONTypeDefinition();
			List<IPersist> components = ComponentFactory.sortElementsOnPositionAndGroup(portal.getAllObjectsAsList());
			if (pd == null)
			{
				Debug.error(new RuntimeException("Cannot find component definition special type to use for portal."));
				return;
			}
			else
			{
				Object[] componentFormElementValues = new Object[components.size()]; // of ComponentTypeFormElementValue type (this array of objects corresponds to CustomJSONArrayType form element value
				int i = 0;
				ComponentPropertyType type = ((ComponentPropertyType)pd.getType());
				propertyPath.add("childElements");
				for (IPersist component : components)
				{
					if (component instanceof IFormElement)
					{
						FormElement nfe = com.servoy.j2db.server.ngclient.ComponentFactory.getFormElement((IFormElement)component, context, propertyPath);
						boolean dpChanged = false;

						propertyPath.add(i);
						// remove the name of relation prefix from child dataproviders as it only stands in the way later on...
						List<String> dataProviders = WebGridFormUI.getWebComponentPropertyType(nfe.getWebComponentSpec(), DataproviderPropertyType.INSTANCE);
						String relationPrefix = portal.getRelationName() + '.';
						Map<String, Object> elementProperties = new HashMap<>(nfe.getRawPropertyValues());
						for (String dpPropertyName : dataProviders)
						{
							String dp = (String)nfe.getPropertyValue(dpPropertyName); // TODO adjust this when/if dataprovider properties change the form element value type in the future
							if (dp != null && dp.startsWith(relationPrefix))
							{
								dpChanged = true;
								// portal always prefixes comp. dataproviders with related fs name
								putAndConvertProperty(dpPropertyName, dp.substring(relationPrefix.length()), elementProperties, context,
									nfe.getWebComponentSpec().getProperty(dpPropertyName), propertyPath);
							}
						}
						if (dpChanged) nfe.updatePropertyValuesDontUse(elementProperties);

						componentFormElementValues[i++] = type.getFormElementValue(null, pd, propertyPath, nfe);
						propertyPath.backOneLevel();
					}
				}
				propertyPath.backOneLevel();
				map.put("childElements", componentFormElementValues);
			}

		}
		catch (IllegalArgumentException e)
		{
			Debug.error(e);
			return;
		}
		catch (JSONException e)
		{
			Debug.error(e);
			return;
		}
	}

	public boolean isLegacy()
	{
		return !(persist instanceof Bean);
	}

	public boolean isForm()
	{
		return persist instanceof Form;
	}

	IPersist getPersist()
	{
		return persist;
	}

}
