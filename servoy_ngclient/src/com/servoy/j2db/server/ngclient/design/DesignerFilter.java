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

package com.servoy.j2db.server.ngclient.design;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.WebComponentSpecProvider;
import org.sablo.specification.WebComponentSpecification;

import com.servoy.j2db.server.ngclient.template.FormTemplateGenerator;
import com.servoy.j2db.util.Debug;

/**
 * Filter for designer editor
 * @author gboros
 */
@WebFilter(urlPatterns = { "/designer/*" })
@SuppressWarnings("nls")
public class DesignerFilter implements Filter
{
	private static List<String> ignoreList = Arrays.asList(new String[] { "svy-checkgroup", "svy-errorbean", "svy-navigator", "svy-radiogroup", "svy-htmlview", "colorthefoundset" });

	@Override
	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException
	{
		try
		{
			HttpServletRequest request = (HttpServletRequest)servletRequest;
			String uri = request.getRequestURI();
			if (uri != null && uri.endsWith("palette"))
			{
				WebComponentSpecProvider provider = WebComponentSpecProvider.getInstance();

				((HttpServletResponse)servletResponse).setContentType("application/json");
				try
				{
					JSONWriter jsonWriter = new JSONWriter(servletResponse.getWriter());
					jsonWriter.array();
					Set<String> packageNames = provider.getPackageNames();
					ArrayList<String> orderedPackageNames = new ArrayList<>();
					for (String packName : packageNames)
					{
						orderedPackageNames.add(packName);
					}
					Collections.sort(orderedPackageNames, new Comparator<String>()
					{

						@Override
						public int compare(String o1, String o2)
						{
							if (o1.toLowerCase().contains("default")) return -1;
							else return o1.compareTo(o2);
						}
					});
					for (String packageName : orderedPackageNames)
					{
						jsonWriter.object();
						jsonWriter.key("packageName").value(packageName);
						jsonWriter.key("components");
						jsonWriter.array();
						for (String componentName : provider.getComponentsInPackage(packageName))
						{
							if (!ignoreList.contains(componentName))
							{
								WebComponentSpecification spec = provider.getWebComponentSpecification(componentName);
								jsonWriter.object();
								jsonWriter.key("name").value(spec.getName());
								jsonWriter.key("displayName").value(spec.getDisplayName());
								jsonWriter.key("tagName").value(FormTemplateGenerator.getTagName(componentName));
								Map<String, Object> model = new HashMap<String, Object>();
								PropertyDescription pd = spec.getProperty("size");
								if (pd != null && pd.getDefaultValue() != null)
								{
									model.put("size", pd.getDefaultValue());
								}
								if (spec.getProperty("enabled") != null)
								{
									model.put("enabled", Boolean.TRUE);
								}
								if (spec.getProperty("editable") != null)
								{
									model.put("editable", Boolean.TRUE);
								}
								if ("svy-label".equals(spec.getName()))
								{
									model.put("text", "label");
								}
								jsonWriter.key("model").value(new JSONObject(model));
								if (spec.getIcon() != null)
								{
									jsonWriter.key("icon").value(spec.getIcon());
								}
								jsonWriter.endObject();
							}
						}
						jsonWriter.endArray();
						jsonWriter.endObject();
					}
					jsonWriter.endArray();
				}
				catch (JSONException ex)
				{
					Debug.error("Exception during designe palette generation", ex);
				}

				return;
			}

			filterChain.doFilter(request, servletResponse);
		}
		catch (RuntimeException | Error e)
		{
			Debug.error(e);
			throw e;
		}
	}

	@Override
	public void destroy()
	{
	}

	@Override
	public void init(FilterConfig filterConfig) throws ServletException
	{
	}
}
