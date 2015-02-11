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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import org.sablo.eventthread.IEventDispatcher;
import org.sablo.services.FormServiceHandler;
import org.sablo.specification.WebComponentSpecification;
import org.sablo.specification.WebServiceSpecProvider;
import org.sablo.websocket.BaseWebsocketSession;
import org.sablo.websocket.CurrentWindow;
import org.sablo.websocket.IClientService;
import org.sablo.websocket.IServerService;
import org.sablo.websocket.IWindow;

import com.servoy.j2db.J2DBGlobals;
import com.servoy.j2db.Messages;
import com.servoy.j2db.persistence.Media;
import com.servoy.j2db.persistence.RepositoryException;
import com.servoy.j2db.persistence.Solution;
import com.servoy.j2db.scripting.StartupArguments;
import com.servoy.j2db.server.ngclient.eventthread.NGClientWebsocketSessionWindows;
import com.servoy.j2db.server.ngclient.eventthread.NGEventDispatcher;
import com.servoy.j2db.server.shared.ApplicationServerRegistry;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.Settings;
import com.servoy.j2db.util.Utils;


/**
 * Handles a websocket session based on a NGClient.
 *
 * @author rgansevles
 *
 */
public class NGClientWebsocketSession extends BaseWebsocketSession implements INGClientWebsocketSession
{
	private NGClient client;

	public NGClientWebsocketSession(String uuid)
	{
		super(uuid);
	}

	public void setClient(NGClient client)
	{
		this.client = client;
	}

	public NGClient getClient()
	{
		return client;
	}

	@Override
	public IWindow createWindow(String windowName)
	{
		return new NGClientWindow(this, windowName);
	}

	@Override
	public boolean isValid()
	{
		return client != null && !client.isShutDown();
	}

	@Override
	protected IEventDispatcher createEventDispatcher()
	{
		return new NGEventDispatcher(client);
	}

	@Override
	public void onOpen(final String... args)
	{
		super.onOpen(args);

		final String solutionName = args[0];

		if (Utils.stringIsEmpty(solutionName))
		{
			CurrentWindow.get().cancelSession("Invalid solution name");
			return;
		}

		if (!client.isEventDispatchThread()) J2DBGlobals.setServiceProvider(client);
		try
		{
			Solution solution = client.getSolution();
			if (solution != null)
			{
				if (!solution.getName().equals(solutionName))
				{
					client.closeSolution(true, null);
				}
				else
				{
					if (args.length > 1)
					{
						args[0] = "solution:" + args[0];
						StartupArguments argumentsScope = new StartupArguments(args);
						String method = argumentsScope.getMethodName();
						String firstArgument = argumentsScope.getFirstArgument();
						if (method != null)
						{
							try
							{
								client.getScriptEngine().getScopesScope().executeGlobalFunction(null, method,
									(firstArgument == null ? null : new Object[] { firstArgument, argumentsScope.toJSMap() }), false, false);
							}
							catch (Exception e1)
							{
								client.reportError(Messages.getString("servoy.formManager.error.ExecutingOpenSolutionMethod", new Object[] { method }), e1); //$NON-NLS-1$
							}
						}
					}

					client.getRuntimeWindowManager().setCurrentWindowName(CurrentWindow.get().getUuid());
					IWebFormController currentForm = client.getFormManager().getCurrentForm();
					if (currentForm != null)
					{
						// we have to call setcontroller again so that switchForm is called and the form is loaded into the reloaded/new window.
						startHandlingEvent();
						try
						{
							client.getRuntimeWindowManager().getCurrentWindow().setController(currentForm);
							sendSolutionCSSURL(solution);
						}
						finally
						{
							stopHandlingEvent();
						}
						return;
					}
				}
			}

			getEventDispatcher().addEvent(new Runnable()
			{
				@Override
				public void run()
				{
					try
					{
						// the solution was not loaded or another was loaded, now create a main window and load the solution.
						// RAGTEST create main window naar ???
						if (true) throw new RuntimeException("RAGTEST");
//						CurrentWindow.get().getEndpoint().setWindowId(client.getRuntimeWindowManager().createMainWindow());

						if (args.length > 1)
						{
							args[0] = "solution:" + args[0];
							client.handleArguments(args);
						}
						client.loadSolution(solutionName);

					}
					catch (RepositoryException e)
					{
						Debug.error("Failed to load the solution: " + solutionName, e);
						sendInternalError(e);
					}
				}


			});
		}
		catch (Exception e)
		{
			Debug.error(e);
			sendInternalError(e);
		}
		finally
		{
			if (!client.isEventDispatchThread()) J2DBGlobals.setServiceProvider(null);
		}
	}

	@Override
	protected IServerService createFormService()
	{
		return FormServiceHandler.INSTANCE;
	}

	// RAGTEST wat deed dit?
//	public void handleMessage(final JSONObject obj)
//	{
//		if (client != null) J2DBGlobals.setServiceProvider(client);
//		try
//		{
//		}
//		catch (Exception e)
//		{
//			Debug.error(e);
//			sendInternalError(e);
//		}
//		finally
//		{
//			J2DBGlobals.setServiceProvider(null);
//		}
//	}

	@Override
	public void solutionLoaded(Solution solution)
	{
		sendSolutionCSSURL(solution);
	}

	protected void sendSolutionCSSURL(Solution solution)
	{
		int styleSheetID = solution.getStyleSheetID();
		if (styleSheetID > 0)
		{
			Media styleSheetMedia = solution.getMedia(styleSheetID);
			if (styleSheetMedia != null)
			{
				String path = "resources/" + MediaResourcesServlet.FLATTENED_SOLUTION_ACCESS + "/" + solution.getName() + "/" + styleSheetMedia.getName();
				getService(NGClient.APPLICATION_SERVICE).executeAsyncServiceCall("setStyleSheet", new Object[] { path });
			}
			else
			{
				Debug.error("Cannot find solution styleSheet in media lib.");
			}
		}
		else
		{
			getService(NGClient.APPLICATION_SERVICE).executeAsyncServiceCall("setStyleSheet", new Object[] { });
		}
	}

	@Override
	public void valueChanged()
	{
		if (client != null)
		{
			super.valueChanged();
		}
	}


	@Override
	public void closeSession()
	{
		this.closeSession(null);
	}

	public void closeSession(String redirectUrl)
	{
		if (client.getWebsocketSession() != null)
		{
			IWindow current = CurrentWindow.set(new NGClientWebsocketSessionWindows(client.getWebsocketSession()));
			try
			{
				Map<String, Object> detail = new HashMap<>();
				String htmlfilePath = Settings.getInstance().getProperty("servoy.webclient.pageexpired.page");
				if (htmlfilePath != null) detail.put("viewUrl", htmlfilePath);
				if (redirectUrl != null) detail.put("redirectUrl", redirectUrl);
				getService("$sessionService").executeAsyncServiceCall("expireSession", new Object[] { detail });
			}
			finally
			{
				CurrentWindow.set(current);
			}
		}
		super.closeSession();
	}

	@Override
	protected IClientService createClientService(String name)
	{
		WebComponentSpecification spec = WebServiceSpecProvider.getInstance().getWebServiceSpecification(name);
		if (spec == null) spec = new WebComponentSpecification(name, "", name, null, null, "", null);
		return new ServoyClientService(name, spec, this);
	}

	/**
	 * Sets an internalServerError object on the client side which shows the internal server error page.
	 * If it is run from the developer it also adds the stack trace
	 * @param e
	 */
	public static void sendInternalError(Exception e)
	{
		Map<String, Object> internalError = new HashMap<>();
		StringWriter sw = new StringWriter();
		e.printStackTrace(new PrintWriter(sw));
		String stackTrace = sw.toString();
		if (ApplicationServerRegistry.get().isDeveloperStartup()) internalError.put("stack", stackTrace);
		String htmlView = Settings.getInstance().getProperty("servoy.webclient.error.page");
		if (htmlView != null) internalError.put("viewUrl", htmlView);
		CurrentWindow.get().getSession().getService("$sessionService").executeAsyncServiceCall("setInternalServerError", new Object[] { internalError });
	}
}
