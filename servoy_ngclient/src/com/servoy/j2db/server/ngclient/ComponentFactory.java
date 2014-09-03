/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2013 Servoy BV

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

import java.awt.Point;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.WebComponentSpecProvider;
import org.sablo.specification.property.CustomJSONArrayType;
import org.sablo.specification.property.types.TypesRegistry;

import com.servoy.base.persistence.constants.IValueListConstants;
import com.servoy.j2db.IApplication;
import com.servoy.j2db.component.ComponentFormat;
import com.servoy.j2db.dataprocessing.CustomValueList;
import com.servoy.j2db.dataprocessing.DBValueList;
import com.servoy.j2db.dataprocessing.GlobalMethodValueList;
import com.servoy.j2db.dataprocessing.IValueList;
import com.servoy.j2db.dataprocessing.RelatedValueList;
import com.servoy.j2db.persistence.Form;
import com.servoy.j2db.persistence.IAnchorConstants;
import com.servoy.j2db.persistence.IFormElement;
import com.servoy.j2db.persistence.IPersist;
import com.servoy.j2db.persistence.Part;
import com.servoy.j2db.persistence.PositionComparator;
import com.servoy.j2db.persistence.StaticContentSpecLoader;
import com.servoy.j2db.persistence.ValueList;
import com.servoy.j2db.server.ngclient.property.ComponentPropertyType;
import com.servoy.j2db.server.ngclient.property.types.PropertyPath;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.Settings;
import com.servoy.j2db.util.Utils;


/**
 * @author lvostinar
 *
 */
public class ComponentFactory
{
	public static WebFormComponent createComponent(IApplication application, IDataAdapterList dataAdapterList, FormElement fe, IWebFormUI formUI)
	{
		String name = fe.getName();
		if (name != null)
		{
			// TODO anything to do here for custom special types?
			WebFormComponent webComponent = new WebFormComponent(name, fe, dataAdapterList);
			Map<String, PropertyDescription> valuelistProps = fe.getWebComponentSpec().getProperties(TypesRegistry.getType("valuelist"));
			for (PropertyDescription vlProp : valuelistProps.values())
			{
				int valuelistID = Utils.getAsInteger(fe.getPropertyValue(vlProp.getName()));
				if (valuelistID > 0)
				{
					ValueList val = application.getFlattenedSolution().getValueList(valuelistID);
					if (val != null)
					{
						IValueList valueList;
						switch (val.getValueListType())
						{
							case IValueListConstants.GLOBAL_METHOD_VALUES :
								valueList = new GlobalMethodValueList(application, val);
								break;
							case IValueListConstants.CUSTOM_VALUES :
								String dataproviderID = (String)fe.getPropertyValue((String)vlProp.getConfig());
								String format = null;
								if (dataproviderID != null)
								{
									Map<String, PropertyDescription> properties = fe.getWebComponentSpec().getProperties(TypesRegistry.getType("format"));
									for (PropertyDescription pd : properties.values())
									{
										// compare the config objects for Format and Valuelist properties these are both the "for" dataprovider id property
										if (vlProp.getConfig().equals(pd.getConfig()))
										{
											format = (String)fe.getPropertyValue(pd.getName());
											break;
										}
									}
								}
								ComponentFormat fieldFormat = ComponentFormat.getComponentFormat(format, dataproviderID,
									application.getFlattenedSolution().getDataproviderLookup(application.getFoundSetManager(), fe.getForm()), application);
								valueList = new CustomValueList(application, val, val.getCustomValues(),
									(val.getAddEmptyValue() == IValueListConstants.EMPTY_VALUE_ALWAYS), fieldFormat.dpType, fieldFormat.parsedFormat);
								break;
							default :
								valueList = val.getDatabaseValuesType() == IValueListConstants.RELATED_VALUES ? new RelatedValueList(application, val)
									: new DBValueList(application, val);
						}
						webComponent.setProperty(vlProp.getName(), valueList);
					}
				}
				else
				{

					ColumnBasedValueList vl = new ColumnBasedValueList(application, fe.getForm().getServerName(), fe.getForm().getTableName(),
						(String)fe.getPropertyValue(StaticContentSpecLoader.PROPERTY_DATAPROVIDERID.getPropertyName()));
					webComponent.setProperty(vlProp.getName(), vl);
				}
			}
			return webComponent;
		}
		return null;
	}

	// todo identity key? SolutionModel persist shouldn't be cached at all?
	private static ConcurrentMap<IPersist, FormElement> persistWrappers = new ConcurrentHashMap<IPersist, FormElement>();

	/**
	 * @param iterator
	 */
	public static List<FormElement> getFormElements(Iterator<IPersist> iterator, IServoyDataConverterContext context)
	{
		if (Boolean.valueOf(Settings.getInstance().getProperty("servoy.internal.reloadSpecsAllTheTime", "false")).booleanValue())
		{
			WebComponentSpecProvider.reload();
		}
		List<FormElement> lst = new ArrayList<>();
		while (iterator.hasNext())
		{
			IPersist persist = iterator.next();
			if (persist instanceof IFormElement)
			{
				lst.add(getFormElement((IFormElement)persist, context, null));
			}
		}
		return lst;
	}

	public static FormElement getFormElement(IFormElement formElement, IServoyDataConverterContext context, PropertyPath propertyPath)
	{
		if (propertyPath == null) propertyPath = new PropertyPath();
		// dont cache if solution model is used (media,valuelist,relations can be changed for a none changed element)
		if (context.getSolution().getSolutionCopy(false) != null)
		{
			if (formElement instanceof ListViewPortal) return createListViewPortalFormElement((ListViewPortal)formElement, context);
			else return new FormElement(formElement, context, new PropertyPath());
		}
		FormElement persistWrapper = persistWrappers.get(formElement);
		if (persistWrapper == null)
		{
			if (formElement instanceof ListViewPortal) persistWrapper = createListViewPortalFormElement((ListViewPortal)formElement, context);
			else persistWrapper = new FormElement(formElement, context, new PropertyPath());
			FormElement existing = persistWrappers.putIfAbsent(formElement, persistWrapper);
			if (existing != null)
			{
				persistWrapper = existing;
			}
		}
		return persistWrapper;
	}

	private static FormElement createListViewPortalFormElement(ListViewPortal listViewPortal, IServoyDataConverterContext context)
	{
		Form form = listViewPortal.getForm();
		Part bodyPart = null;
		for (Part prt : Utils.iterate(form.getParts()))
		{
			if (prt.getPartType() == Part.BODY)
			{
				bodyPart = prt;
				break;
			}
		}
		if (bodyPart != null)
		{
			try
			{
				String name = "svy_lvp_" + form.getName();

				JSONObject portal = new JSONObject();
				portal.put("name", name);
				portal.put("multiLine", true);
				portal.put("rowHeight", bodyPart.getHeight());
				portal.put("anchors", IAnchorConstants.ALL);
				JSONObject location = new JSONObject();
				location.put("x", 0);
				location.put("y", 0);
				portal.put("location", location);
				JSONObject size = new JSONObject();
				size.put("width", form.getWidth());
				size.put("height", bodyPart.getHeight());
				portal.put("size", size);
				portal.put("visible", listViewPortal.getVisible());
				portal.put("enabled", listViewPortal.getEnabled());
				portal.put("childElements", new JSONArray()); // empty contents; will be updated afterwards directly with form element values for components

				JSONObject relatedFoundset = new JSONObject();
				relatedFoundset.put("foundsetSelector", "");
				portal.put("relatedFoundset", relatedFoundset);

				PropertyPath propertyPath = new PropertyPath();
				FormElement portalFormElement = new FormElement("svy-portal", portal, form, name, context, propertyPath);

				PropertyDescription pd = portalFormElement.getWebComponentSpec().getProperties().get("childElements");
				if (pd != null) pd = ((CustomJSONArrayType< ? , ? >)pd.getType()).getCustomJSONTypeDefinition();
				if (pd == null)
				{
					Debug.error(new RuntimeException("Cannot find component definition special type to use for portal."));
					return null;
				}
				ComponentPropertyType type = ((ComponentPropertyType)pd.getType());

				Map<String, Object> portalFormElementProperties = portalFormElement.getRawPropertyValues();
				// now put real child component form element values in "childElements"
				int startPos = form.getPartStartYPos(bodyPart.getID());
				int endPos = bodyPart.getHeight();
				Iterator<IPersist> it = form.getAllObjects(PositionComparator.XY_PERSIST_COMPARATOR);
				List<Object> children = new ArrayList<>(); // contains actually ComponentTypeFormElementValue objects
				propertyPath.add(portalFormElement.getName());
				while (it.hasNext())
				{
					IPersist persist = it.next();
					if (persist instanceof IFormElement)
					{
						Point loc = ((IFormElement)persist).getLocation();
						if (startPos <= loc.y && endPos >= loc.y)
						{
							FormElement fe = ComponentFactory.getFormElement((IFormElement)persist, context, propertyPath);
							children.add(type.getFormElementValue(null, pd, propertyPath, fe));
						}
					}
				}
				propertyPath.backOneLevel();
				portalFormElementProperties.put("childElements", children.toArray());
				portalFormElement.updatePropertyValuesDontUse(portalFormElementProperties);

				return portalFormElement;
			}
			catch (JSONException ex)
			{
				Debug.error("Cannot create list view portal component", ex);
			}
		}

		return null;
	}

	public static String getMarkupId(String formName, String elementName)
	{
		return Utils.calculateMD5HashBase16(formName + elementName);
	}

	public static void reload()
	{
		persistWrappers.clear();
		WebComponentSpecProvider.reload();
	}

	/**
	 * @param valuelist
	 * @return
	 */
	public static boolean isSingleValue(ValueList valuelist)
	{
		if (valuelist != null && valuelist.getValueListType() == IValueListConstants.CUSTOM_VALUES &&
			valuelist.getAddEmptyValue() != IValueListConstants.EMPTY_VALUE_ALWAYS && valuelist.getCustomValues() != null &&
			!valuelist.getCustomValues().contains("\n") && !valuelist.getCustomValues().contains("\r"))
		{
			return true;
		}
		return false;
	}


}
