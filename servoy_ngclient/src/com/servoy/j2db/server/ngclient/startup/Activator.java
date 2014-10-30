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

package com.servoy.j2db.server.ngclient.startup;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.sablo.websocket.IWebsocketSession;
import org.sablo.websocket.IWebsocketSessionFactory;
import org.sablo.websocket.WebsocketSessionManager;

import com.servoy.j2db.IDebugClientHandler;
import com.servoy.j2db.server.ngclient.NGClient;
import com.servoy.j2db.server.ngclient.NGClientWebsocketSession;
import com.servoy.j2db.server.ngclient.WebsocketSessionFactory;
import com.servoy.j2db.server.shared.ApplicationServerRegistry;

/**
 * @author jblok
 */
public class Activator implements BundleActivator
{

	private static BundleContext context;

	public static BundleContext getContext()
	{
		return context;
	}

	@Override
	public void start(BundleContext ctx) throws Exception
	{
		Activator.context = ctx;
		if (ApplicationServerRegistry.getServiceRegistry() != null)
		{
			final IDebugClientHandler service = ApplicationServerRegistry.getServiceRegistry().getService(IDebugClientHandler.class);
			if (service != null)
			{
				WebsocketSessionManager.setWebsocketSessionFactory(WebsocketSessionFactory.CLIENT_ENDPOINT, new IWebsocketSessionFactory()
				{
					@Override
					public IWebsocketSession createSession(String uuid) throws Exception
					{
						NGClientWebsocketSession wsSession = new NGClientWebsocketSession(uuid);
						wsSession.setClient((NGClient)service.createDebugNGClient(wsSession));
						return wsSession;
					}
				});
			}
		}
	}

	@Override
	public void stop(BundleContext ctx) throws Exception
	{
	}
}
