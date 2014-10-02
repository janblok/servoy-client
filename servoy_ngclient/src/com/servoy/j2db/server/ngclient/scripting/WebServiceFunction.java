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

package com.servoy.j2db.server.ngclient.scripting;

import java.io.IOException;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.sablo.BaseWebObject;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.WebComponentApiDefinition;
import org.sablo.websocket.IWebsocketSession;

import com.servoy.j2db.server.ngclient.property.types.NGConversions;

/**
 * Javascript function to call a client-side function in the web service api.
 *
 * @author jcompagner
 *
 */
public class WebServiceFunction extends WebBaseFunction
{
	private final IWebsocketSession session;
	private final String serviceName;

	public WebServiceFunction(IWebsocketSession session, WebComponentApiDefinition definition, String serviceName)
	{
		super(definition);
		this.session = session;
		this.serviceName = serviceName;
	}

	@Override
	public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args)
	{
		// No unwrapping here so that javascript objects can also be sent to client
		try
		{
			PropertyDescription retPD = definition != null ? definition.getReturnType() : null;
			return NGConversions.INSTANCE.convertSabloComponentToRhinoValue(session.getService(serviceName).executeServiceCall(definition.getName(), args),
				retPD, (BaseWebObject)session.getService(serviceName), thisObj);
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
	}
}
