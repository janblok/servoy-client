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

import org.sablo.specification.WebComponentSpecification;
import org.sablo.websocket.IClientService;
import org.sablo.websocket.IWindow;
import org.sablo.websocket.impl.ClientService;

import com.servoy.j2db.server.ngclient.NGClientWebsocketSession;

/**
 * @author jcompagner
 */
public final class DesignNGClientWebsocketSession extends NGClientWebsocketSession
{
	public static final String EDITOR_CONTENT_SERVICE = "$editorContentService";

	private static final WebComponentSpecification EDITOR_CONTENT_SERVICE_SPECIFICATION = new WebComponentSpecification(EDITOR_CONTENT_SERVICE, "",
		EDITOR_CONTENT_SERVICE, null, null, "", null);

	/**
	 * @param uuid
	 */
	public DesignNGClientWebsocketSession(String uuid)
	{
		super(uuid);
	}

	@Override
	protected IClientService createClientService(String name)
	{
		if (EDITOR_CONTENT_SERVICE.equals(name))
		{
			return new ClientService(EDITOR_CONTENT_SERVICE, EDITOR_CONTENT_SERVICE_SPECIFICATION);
		}
		return super.createClientService(name);
	}

	@Override
	public IWindow createWindow(String windowName)
	{
		return new DesignNGClientWindow(this, windowName);
	}


	@Override
	public void onOpen(String solutionName)
	{
		// always generate a new window id. The window session seems to be shared over multiple swt browsers.
// RAGTEST		CurrentWindow.get().getEndpoint().setWindowId(getClient().getRuntimeWindowManager().createMainWindow());
		super.onOpen(solutionName);
		if (getClient().getSolution() != null)
		{
			sendSolutionCSSURL(getClient().getSolution());
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.servoy.j2db.server.ngclient.NGClientWebsocketSession#closeSession()
	 */
	@Override
	public void closeSession()
	{
		getClient().closeSolution(false, null);
		super.closeSession();
	}
}