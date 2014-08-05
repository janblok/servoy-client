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

import org.sablo.websocket.IWebsocketSession;
import org.sablo.websocket.IWebsocketSessionFactory;

/**
 * Create websocket session handler based on endpoint type.
 *
 * @author rgansevles
 *
 */
public class WebsocketSessionFactory implements IWebsocketSessionFactory
{
	private volatile IClientCreator clientCreator;

	private static WebsocketSessionFactory me;

	public static WebsocketSessionFactory get()
	{
		if (me == null)
		{
			me = new WebsocketSessionFactory();
		}
		return me;
	}

	private WebsocketSessionFactory()
	{
	}

	/**
	 * @param uuid
	 * @return the session
	 */
	public IWebsocketSession createSession(String uuid) throws Exception
	{
		NGClientWebsocketSession wsSession = new NGClientWebsocketSession(uuid);
		wsSession.setClient(getClientCreator().createClient(wsSession));
		return wsSession;
	}

	/**
	 * @return the clientCreator
	 */
	public IClientCreator getClientCreator()
	{
		if ((clientCreator == null))
		{
			clientCreator = new IClientCreator()
			{
				@Override
				public NGClient createClient(INGClientWebsocketSession wsSession) throws Exception
				{
					return new NGClient(wsSession);
				}
			};
		}
		return clientCreator;
	}

	/**
	 * @param clientCreator the clientCreator to set
	 */
	public void setClientCreator(IClientCreator creator)
	{
		clientCreator = creator;
	}
}
