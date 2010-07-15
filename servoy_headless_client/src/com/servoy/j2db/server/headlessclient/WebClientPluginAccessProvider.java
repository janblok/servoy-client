/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2010 Servoy BV

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
package com.servoy.j2db.server.headlessclient;

import org.apache.wicket.Page;
import org.apache.wicket.RequestCycle;
import org.apache.wicket.ajax.AjaxRequestTarget;

import com.servoy.j2db.plugins.ClientPluginAccessProvider;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.plugins.IMediaUploadCallback;
import com.servoy.j2db.server.headlessclient.dataui.WebEventExecutor;

/**
 * A special {@link IClientPluginAccess} that also implements {@link IWebClientPluginAccess} to override behavior that is specific for the webclient.
 * 
 * @author jcompagner
 */
public class WebClientPluginAccessProvider extends ClientPluginAccessProvider implements IWebClientPluginAccess
{
	private final WebClient client;

	public WebClientPluginAccessProvider(WebClient client)
	{
		super(client);
		this.client = client;
	}

	public boolean showURL(String url, String target, String target_options)
	{
		return client.showURL(url, target, target_options, 0);
	}

	public boolean showURL(String url, String target, String target_options, int timeout)
	{
		return client.showURL(url, target, target_options, timeout);
	}

	public IPageContributor getPageContributor()
	{
		MainPage mp = client.getMainPage();
		return mp.getPageContributor();
	}

	public String serveResource(String filename, byte[] bs, String mimetype)
	{
		MainPage mp = client.getMainPage();
		return mp.serveResource(filename, bs, mimetype);
	}

	public void generateAjaxResponse(AjaxRequestTarget target)
	{
		if (RequestCycle.get() != null)
		{
			Page page = RequestCycle.get().getRequest().getPage();
			WebEventExecutor.generateResponse(target, page);
		}
	}

	/**
	 * @see com.servoy.j2db.server.headlessclient.IWebClientPluginAccess#showFileOpenDialog(com.servoy.j2db.scripting.FunctionDefinition, java.lang.String, boolean, java.lang.String[])
	 */
	@Override
	public void showFileOpenDialog(IMediaUploadCallback callback, String fileNameHint, boolean multiSelect, String[] filter, int selection, String dialogTitle)
	{
		MainPage mp = client.getMainPage();
		mp.showOpenFileDialog(callback, multiSelect, dialogTitle);
	}
}