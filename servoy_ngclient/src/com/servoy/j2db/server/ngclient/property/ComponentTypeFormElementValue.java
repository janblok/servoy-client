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

package com.servoy.j2db.server.ngclient.property;

import java.util.List;
import java.util.Map;

import com.servoy.j2db.server.ngclient.FormElement;

/**
 * Value used in FormElement for component types.
 *
 * @author acostescu
 */
@SuppressWarnings("nls")
public class ComponentTypeFormElementValue
{

	protected final FormElement element;
	protected final List<String> apisOnAll; // here are the api's that should be called on all records, not only selected one when called on a foundset linked component
	protected final Map<String, String> dataLinks;
	protected final Object[] propertyPath;

	/**
	 * @param apisOnAll can be null if the component is not linked to a foundset
	 * @param dataLinks can be null if the component is not linked to a foundset
	 */
	public ComponentTypeFormElementValue(FormElement element, List<String> apisOnAll, Map<String, String> dataLinks, Object[] propertyPath)
	{
		this.element = element;
		this.apisOnAll = apisOnAll;
		this.dataLinks = dataLinks;
		this.propertyPath = propertyPath;
	}


}
