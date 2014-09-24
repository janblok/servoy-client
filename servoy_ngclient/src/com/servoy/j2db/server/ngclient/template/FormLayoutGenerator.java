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
import java.util.Iterator;

import com.servoy.j2db.persistence.BaseComponent;
import com.servoy.j2db.persistence.Form;
import com.servoy.j2db.persistence.Part;
import com.servoy.j2db.server.ngclient.ComponentFactory;
import com.servoy.j2db.server.ngclient.FormElement;
import com.servoy.j2db.server.ngclient.IServoyDataConverterContext;

/**
 * @author lvostinar
 *
 */
public class FormLayoutGenerator
{

	public static void generateRecordViewForm(PrintWriter writer, Form form, IServoyDataConverterContext context, boolean design)
	{
		generateFormStartTag(writer, form);
		Iterator<Part> it = form.getParts();
		while (it.hasNext())
		{
			Part part = it.next();
			if (!Part.rendersOnlyInPrint(part.getPartType()))
			{
				writer.print("<div ng-style=\"");
				writer.print(PartWrapper.getName(part));
				writer.println("Style\">");
				for (BaseComponent bc : PartWrapper.getBaseComponents(part, form, context))
				{
					FormElement fe = ComponentFactory.getFormElement(bc, context, null);

					generateFormElementWrapper(writer, fe, design);
					generateFormElement(writer, fe, false);
					generateEndDiv(writer);
				}

				generateEndDiv(writer);
			}
		}
		generateEndDiv(writer);
	}

	public static void generateFormStartTag(PrintWriter writer, Form form)
	{
		writer.print(String.format(
			"<div ng-controller=\"%1$s\" svy-formstyle=\"formStyle\" svy-scrollbars='formProperties.scrollbars' svy-layout-update svy-formload svy-autosave",
			form.getName()));
		if (form.getStyleClass() != null)
		{
			writer.print(" class=\"");
			writer.print(form.getStyleClass());
			writer.print("\"");
		}
		writer.println(">");
	}

	public static void generateEndDiv(PrintWriter writer)
	{
		writer.println("</div>");
	}

	public static void generateFormElementWrapper(PrintWriter writer, FormElement fe, boolean design)
	{
		writer.print("<div ng-style=\"layout.");
		writer.print(fe.getName());
		writer.print("\" svy-layout-update=\"");
		writer.print(fe.getName());
		writer.print("\"");
		if (design)
		{
			writer.print(" svy-id='");
			writer.print(fe.getDesignId());
			writer.print("'");
			writer.print(" name='");
			writer.print(fe.getName());
			writer.print("'");
		}
		writer.println(">");
	}

	public static void generateFormElement(PrintWriter writer, FormElement fe, boolean design)
	{
		writer.print("<");
		writer.print(fe.getTagname());
		writer.print(" name='");
		writer.print(fe.getName());
		writer.print("'");
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
		}
		writer.print(" svy-apply='handlers.");
		writer.print(fe.getName());
		writer.print(".svy_apply'");
		writer.print(" svy-servoyApi='handlers.");
		writer.print(fe.getName());
		writer.print(".svy_servoyApi'");
		writer.println(">");
		writer.print("</");
		writer.print(fe.getTagname());
		writer.println(">");
	}
}
