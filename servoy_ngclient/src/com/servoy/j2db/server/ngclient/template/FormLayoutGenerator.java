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

package com.servoy.j2db.server.ngclient.template;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jsoup.helper.StringUtil;
import org.sablo.specification.PropertyDescription;

import com.servoy.base.persistence.constants.IFormConstants;
import com.servoy.j2db.BasicFormManager;
import com.servoy.j2db.IFormController;
import com.servoy.j2db.persistence.BaseComponent;
import com.servoy.j2db.persistence.FlattenedForm;
import com.servoy.j2db.persistence.Form;
import com.servoy.j2db.persistence.IAnchorConstants;
import com.servoy.j2db.persistence.IPersist;
import com.servoy.j2db.persistence.Part;
import com.servoy.j2db.server.ngclient.FormElement;
import com.servoy.j2db.server.ngclient.FormElementHelper;
import com.servoy.j2db.server.ngclient.IServoyDataConverterContext;
import com.servoy.j2db.server.ngclient.WebFormUI;
import com.servoy.j2db.util.Settings;
import com.servoy.j2db.util.Utils;

/**
 * Generates HTML for a absolute layout form
 * @author lvostinar
 */
@SuppressWarnings("nls")
public class FormLayoutGenerator
{

	public static void generateRecordViewForm(PrintWriter writer, Form form, String realFormName, IServoyDataConverterContext context, boolean design,
		boolean highlight)
	{
		generateFormStartTag(writer, form, realFormName, false, design);
		Iterator<Part> it = form.getParts();

		if (design)
		{
			while (it.hasNext())
			{
				Part part = it.next();
				if (!Part.rendersOnlyInPrint(part.getPartType()))
				{
					writer.print("<div ng-style=\"");
					writer.print(PartWrapper.getName(part));
					writer.print("Style\"");
					String partClass = "svy-" + PartWrapper.getName(part);
					if (part.getStyleClass() != null)
					{
						partClass += " " + part.getStyleClass();
					}
					writer.print(" class=\"");
					writer.print(partClass);
					writer.println("\">");
					generateEndDiv(writer);
				}
			}
		}

		Map<IPersist, FormElement> cachedElementsMap = new HashMap<IPersist, FormElement>();
		if (context != null && context.getApplication() != null)
		{
			IFormController controller = ((BasicFormManager)context.getApplication().getFormManager()).getCachedFormController(realFormName);
			if (controller != null && controller.getFormUI() instanceof WebFormUI)
			{
				List<FormElement> cachedFormElements = ((WebFormUI)controller.getFormUI()).getFormElements();
				for (FormElement fe : cachedFormElements)
				{
					if (fe.getPersistIfAvailable() != null)
					{
						cachedElementsMap.put(fe.getPersistIfAvailable(), fe);
					}
				}
			}
		}
		it = form.getParts();
		while (it.hasNext())
		{
			Part part = it.next();
			if (!Part.rendersOnlyInPrint(part.getPartType()))
			{
				if (!design)
				{
					writer.print("<div ng-style=\"");
					writer.print(PartWrapper.getName(part));
					writer.print("Style\"");
					String partClass = "svy-" + PartWrapper.getName(part);
					if (part.getStyleClass() != null)
					{
						partClass += " " + part.getStyleClass();
					}
					writer.print(" class=\"");
					writer.print(partClass);
					writer.println("\">");
				}

				for (BaseComponent bc : PartWrapper.getBaseComponents(part, form, context, design))
				{
					FormElement fe = null;
					if (cachedElementsMap.containsKey(bc))
					{
						fe = cachedElementsMap.get(bc);
					}
					if (fe == null)
					{
						fe = FormElementHelper.INSTANCE.getFormElement(bc, context, null);
					}

					generateFormElementWrapper(writer, fe, design, form);
					generateFormElement(writer, fe, false, highlight);
					generateEndDiv(writer);
				}

				if (!design) generateEndDiv(writer);
			}
		}

		generateFormEndTag(writer);
	}

	public static void generateFormStartTag(PrintWriter writer, Form form, String realFormName, boolean responsiveMode, boolean design)
	{
		writer.print(String.format("<svy-formload formname=\"%1$s\"><div ng-controller=\"%1$s\" ", realFormName));
		if (Utils.getAsBoolean(Settings.getInstance().getProperty("servoy.ngclient.testingMode", "false")))
		{
			writer.print(String.format("data-svy-name=\"%1$s\" ", realFormName));
		}

		if (!form.isResponsiveLayout() && !responsiveMode)
		{
			writer.print("svy-formstyle=\"formStyle\" ");
		}
		else if (design)
		{
			writer.print(" style=\"height:100%\"");
		}
		writer.print("svy-layout-update svy-autosave ");
		// skip the scrollbars for forms in table or list view then the portal component does this.
		if (!isTableOrListView(form))
		{
			writer.print(" svy-scrollbars='formProperties.scrollbars'");
		}
		String formClass = "svy-form";
		if (form.getStyleClass() != null)
		{
			formClass += " " + form.getStyleClass();
		}
		writer.print(" class=\"");
		writer.print(formClass);
		writer.print("\"");
		writer.println(">");
	}

	public static boolean isTableOrListView(Form form)
	{
		return (form.getView() == IFormConstants.VIEW_TYPE_TABLE || form.getView() == IFormConstants.VIEW_TYPE_TABLE_LOCKED ||
			form.getView() == IFormConstants.VIEW_TYPE_LIST || form.getView() == IFormConstants.VIEW_TYPE_LIST_LOCKED);
	}

	public static void generateEndDiv(PrintWriter writer)
	{
		writer.println("</div>");
	}

	public static void generateFormEndTag(PrintWriter writer)
	{
		generateEndDiv(writer);
		writer.println("</svy-formload>");
	}

	public static void generateFormElementWrapper(PrintWriter writer, FormElement fe, boolean design, Form form)
	{
		writer.print("<div ng-style=\"layout.");
		writer.print(fe.getName());
		writer.print("\"");
		if (!form.isResponsiveLayout() && fe.getPersistIfAvailable() instanceof BaseComponent)
		{
			BaseComponent bc = (BaseComponent)fe.getPersistIfAvailable();
			int anchors = bc.getAnchors();
			String style = "";
			if (((anchors & IAnchorConstants.EAST) > 0) && ((anchors & IAnchorConstants.WEST) > 0))
			{
				style += "min-width:" + bc.getSize().width + "px;";
			}
			if (((anchors & IAnchorConstants.NORTH) > 0) && ((anchors & IAnchorConstants.SOUTH) > 0))
			{
				style += "min-height:" + bc.getSize().height + "px";
			}
			if (!style.isEmpty())
			{
				writer.print(" style='");
				writer.print(style);
				writer.print("'");
			}
		}
		if (design)
		{
			writer.print(" svy-id='");
			writer.print(getDesignId(fe));
			writer.print("'");
			writer.print(" name='");
			writer.print(fe.getName());
			writer.print("'");
			if (isNotSelectable(fe)) writer.print(" svy-non-selectable");
			Form currentForm = form;
			if (form instanceof FlattenedForm) currentForm = ((FlattenedForm)form).getForm();

			if (fe.getPersistIfAvailable() != null && Utils.isInheritedFormElement(fe.getPersistIfAvailable(), currentForm))
			{
				writer.print(" class='inherited_element'");
			}
			List<String> typeNames = fe.getSvyTypesNames();
			if (typeNames.size() > 0)
			{
				writer.print(" svy-types='");
				writer.print("[" + StringUtil.join(typeNames, ",") + "]");
				writer.print("'");
			}
			List<String> forbiddenComponentNames = fe.getForbiddenComponentNames();
			if (forbiddenComponentNames.size() > 0)
			{
				writer.print(" svy-forbidden-components='");
				writer.print("[" + StringUtil.join(forbiddenComponentNames, ",") + "]");
				writer.print("'");
			}
			String directEditPropertyName = getDirectEditProperty(fe);
			if (directEditPropertyName != null)
			{
				writer.print(" directEditPropertyName='");
				writer.print(directEditPropertyName);
				writer.print("'");
			}
		}
		writer.println(">");
	}

//	private static boolean canContainComponents(WebComponentSpecification spec)
//	{
//		Map<String, PropertyDescription> properties = spec.getProperties();
//		for (PropertyDescription propertyDescription : properties.values())
//		{
//			String simpleTypeName = propertyDescription.getType().getName().replaceFirst(spec.getName() + ".", "");
//			if (simpleTypeName.equals(ComponentPropertyType.TYPE_NAME)) return true;
//			Object configObject = propertyDescription.getConfig();
//			if (configObject != null)
//			{
//				try
//				{
//					if (configObject instanceof JSONObject && ((JSONObject)configObject).has(DesignerFilter.DROPPABLE))
//					{
//						Object droppable = ((JSONObject)configObject).get(DesignerFilter.DROPPABLE);
//						if (droppable instanceof Boolean && (Boolean)droppable)
//						{
//							if (simpleTypeName.equals("tab")) return true;
//						}
//					}
//				}
//				catch (JSONException e)
//				{
//					Debug.log(e);
//				}
//			}
//		}
//		return false;
//	}

	public static void generateFormElement(PrintWriter writer, FormElement fe, boolean design, boolean highlight)
	{
		if (highlight) writer.print("<div class='highlight_element" + (fe.getForm().isResponsiveLayout() ? "" : " inherit_size") + "'>");

		writer.print("<");
		writer.print(fe.getTagname());
		writer.print(" name='");
		writer.print(fe.getName());
		writer.print("'");
		if (Utils.getAsBoolean(Settings.getInstance().getProperty("servoy.ngclient.testingMode", "false")))
		{
			String elementName = fe.getName();
			if (elementName.startsWith("svy_") && fe.getPersistIfAvailable() != null)
			{
				elementName = "svy_" + fe.getPersistIfAvailable().getUUID().toString();
			}
			writer.print(" data-svy-name='");
			writer.print(fe.getForm().getName() + "." + elementName);
			writer.print("'");
		}
		writer.print(" svy-model='model.");
		writer.print(fe.getName());
		writer.print("'");
		writer.print(" svy-api='api.");
		writer.print(fe.getName());
		writer.print("'");
		writer.print(" svy-handlers='handlers.");
		writer.print(fe.getName());
		writer.print("'");
		if (design)
		{
			writer.print(" svy-id='");
			writer.print(fe.getDesignId());
			writer.print("'");
			if (fe.getForm().isResponsiveLayout())
			{
				List<String> typeNames = fe.getSvyTypesNames();
				if (typeNames.size() > 0)
				{
					writer.print(" svy-types='");
					writer.print("[" + StringUtil.join(typeNames, ",") + "]");
					writer.print("'");
				}
				List<String> forbiddenComponentNames = fe.getForbiddenComponentNames();
				if (forbiddenComponentNames.size() > 0)
				{
					writer.print(" svy-forbidden-components='");
					writer.print("[" + StringUtil.join(forbiddenComponentNames, ",") + "]");
					writer.print("'");
				}
			}
			String directEditPropertyName = getDirectEditProperty(fe);
			if (directEditPropertyName != null)
			{
				writer.print(" directEditPropertyName='");
				writer.print(directEditPropertyName);
				writer.print("'");
			}
		}
		writer.print(" svy-servoyApi='handlers.");
		writer.print(fe.getName());
		writer.print(".svy_servoyApi'");
		writer.println(">");
		writer.print("</");
		writer.print(fe.getTagname());
		writer.println(">");
		if (highlight) writer.print("</div>");

	}

	/**
	 * When the formElement represents a portal of a list / table view form, we should return the uuid of the form.
	 * @param fe
	 * @return
	 */
	private static String getDesignId(FormElement fe)
	{
		return (fe.getDesignId() == null && fe.getTypeName().equals("servoydefault-portal")) ? fe.getForm().getUUID().toString() : fe.getDesignId();
	}

	private static String getDirectEditProperty(FormElement fe)
	{
		Map<String, PropertyDescription> properties = fe.getWebComponentSpec(false).getProperties();
		for (PropertyDescription pd : properties.values())
		{
			if (Utils.getAsBoolean(pd.getTag("directEdit")))
			{
				return pd.getName();
			}
		}
		return null;
	}

	private static boolean isNotSelectable(FormElement fe)
	{
		return (fe.getDesignId() == null && fe.getTypeName().equals("servoydefault-portal"));
	}
}
