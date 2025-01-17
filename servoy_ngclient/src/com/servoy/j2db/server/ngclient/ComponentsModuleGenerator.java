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

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.sablo.specification.WebComponentSpecProvider;
import org.sablo.specification.WebComponentSpecification;
import org.sablo.specification.WebServiceSpecProvider;

import com.servoy.j2db.util.HTTPUtils;

/**
 * @author jcompagner
 *
 * generates the /js/servoy-components.js file that has the servoy components module declared with all the webcomponents modules
 *
 */
@WebServlet("/js/servoy-components.js")
public class ComponentsModuleGenerator extends HttpServlet
{

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
	{
		resp.setContentType("text/javascript");
		HTTPUtils.checkAndSetUnmodified(req, resp, System.currentTimeMillis());
		StringBuilder sb = new StringBuilder("angular.module('servoy-components', [ ");
		generateModules(sb, WebServiceSpecProvider.getInstance().getAllWebServiceSpecifications());
		generateModules(sb, WebComponentSpecProvider.getInstance().getAllWebComponentSpecifications());
		sb.setLength(sb.length() - 1);
		sb.append("]);");
		resp.setContentLength(sb.length());
		resp.getWriter().write(sb.toString());
	}

	/**
	 * @param sb
	 * @param webComponentDescriptions
	 */
	protected void generateModules(StringBuilder sb, WebComponentSpecification[] webComponentDescriptions)
	{
		for (WebComponentSpecification webComponentSpec : webComponentDescriptions)
		{
			String name = webComponentSpec.getName();
			generateModule(sb, name);
		}
	}

	protected void generateModule(StringBuilder sb, String name)
	{
		StringBuilder nameSb = new StringBuilder();
		boolean upperNext = false;
		for (int i = 0; i < name.length(); i++)
		{
			if (name.charAt(i) == '-')
			{
				upperNext = true;
			}
			else
			{
				if (upperNext)
				{
					upperNext = false;
					nameSb.append(Character.toUpperCase(name.charAt(i)));
				}
				else
				{
					nameSb.append(name.charAt(i));
				}
			}
		}
		sb.append('\'');
		sb.append(nameSb);
		sb.append('\'');
		sb.append(',');
	}
}
