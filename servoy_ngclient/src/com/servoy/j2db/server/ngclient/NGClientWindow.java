/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2015 Servoy BV

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
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;

import org.sablo.Container;
import org.sablo.WebComponent;
import org.sablo.eventthread.EventDispatcher;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.WebComponentApiDefinition;
import org.sablo.websocket.BaseWindow;
import org.sablo.websocket.CurrentWindow;
import org.sablo.websocket.IWebsocketEndpoint;

import com.servoy.j2db.FlattenedSolution;
import com.servoy.j2db.persistence.Form;
import com.servoy.j2db.persistence.Solution;
import com.servoy.j2db.server.ngclient.endpoint.INGClientWebsocketEndpoint;
import com.servoy.j2db.server.ngclient.template.FormTemplateGenerator;
import com.servoy.j2db.util.Debug;

/**
 * Sablo window for NGClient
 *
 * @author rgansevles
 *
 */
public class NGClientWindow extends BaseWindow implements INGClientWindow
{

	private final INGClientWebsocketSession websocketSession;

	/**
	 * @param websocketSession
	 * @param windowName
	 */
	public NGClientWindow(INGClientWebsocketSession websocketSession, String windowName)
	{
		super(windowName);
		this.websocketSession = websocketSession;
	}

	public static INGClientWindow getCurrentWindow()
	{
		return (INGClientWindow)CurrentWindow.get();
	}

	@Override
	public INGClientWebsocketSession getSession()
	{
		return (INGClientWebsocketSession)super.getSession();
	}

	@Override
	public INGClientWebsocketEndpoint getEndpoint()
	{
		return (INGClientWebsocketEndpoint)super.getEndpoint();
	}

	public INGApplication getClient()
	{
		return getSession().getClient();
	}

	@Override
	public Container getForm(String formName)
	{
		return (Container)websocketSession.getClient().getFormManager().getForm(formName).getFormUI();
	}

	@Override
	protected Object invokeApi(WebComponent receiver, WebComponentApiDefinition apiFunction, Object[] arguments, PropertyDescription argumentTypes,
		Map<String, Object> callContributions)
	{
		Map<String, Object> call = new HashMap<>();
		if (callContributions != null) call.putAll(callContributions);

		IWebFormController form = websocketSession.getClient().getFormManager().getForm(receiver.findParent(IWebFormUI.class).getName());
		touchForm(form.getForm(), form.getName(), false);
		if (receiver instanceof WebFormComponent && ((WebFormComponent)receiver).getComponentContext() != null)
		{
			ComponentContext componentContext = ((WebFormComponent)receiver).getComponentContext();
			call.put("propertyPath", componentContext.getPropertyPath());
		}
		return super.invokeApi(receiver, apiFunction, arguments, argumentTypes, call);
	}

	@Override
	public void touchForm(Form form, String realInstanceName, boolean async)
	{
		if (form == null) return;
		String formName = realInstanceName == null ? form.getName() : realInstanceName;
		String formUrl = "solutions/" + form.getSolution().getName() + "/forms/" + formName + ".html";
		if (getEndpoint().addFormIfAbsent(formName, formUrl))
		{
			// form is not yet on the client, send over the controller
			updateController(form, formName, formUrl, !async);
		}
		else
		{
			formUrl = getEndpoint().getFormUrl(formName);
		}

		// if sync wait until we got response from client as it is loaded
		if (!async)
		{
			if (!getEndpoint().isFormCreated(formName))
			{
				// really send the changes
				try
				{
					sendChanges();
				}
				catch (IOException e)
				{
					Debug.error(e);
				}
				try
				{
					websocketSession.getEventDispatcher().suspend(formUrl, IWebsocketEndpoint.EVENT_LEVEL_SYNC_API_CALL, EventDispatcher.CONFIGURED_TIMEOUT);
				}
				catch (CancellationException e)
				{
					throw e; // full browser refresh while doing this?
				}
				catch (TimeoutException e)
				{
					throw new RuntimeException(e); // timeout... something went wrong; propagate this exception to calling code...
				}
			}
		}
	}

	protected void updateController(Form form, String realFormName, String formUrl, boolean forceLoad)
	{
		try
		{
			String realUrl = formUrl;
			FlattenedSolution fs = websocketSession.getClient().getFlattenedSolution();
			Solution sc = fs.getSolutionCopy(false);
			boolean copy = false;
			if (sc != null && sc.getChild(form.getUUID()) != null)
			{
				realUrl = realUrl + "?lm:" + form.getLastModified() + "&sessionId=" + getSession().getUuid();
				copy = true;
			}
			else if (!form.getName().endsWith(realFormName))
			{
				realUrl = realUrl + "?lm:" + form.getLastModified() + "&sessionId=" + getSession().getUuid();
			}
			else
			{
				realUrl = realUrl + "?sessionId=" + getSession().getUuid();
			}
			StringWriter sw = new StringWriter(512);
			if (copy || !Boolean.valueOf(System.getProperty("servoy.generateformscripts", "false")).booleanValue())
			{
				new FormTemplateGenerator(new ServoyDataConverterContext(websocketSession.getClient()), true, false).generate(form, realFormName,
					"form_recordview_js.ftl", sw);
			}
			if (websocketSession.getClient().isEventDispatchThread() && forceLoad)
			{
				websocketSession.getClientService(NGRuntimeWindowManager.WINDOW_SERVICE).executeServiceCall("updateController",
					new Object[] { realFormName, sw.toString(), realUrl, Boolean.valueOf(forceLoad) });
			}
			else
			{
				websocketSession.getClientService(NGRuntimeWindowManager.WINDOW_SERVICE).executeAsyncServiceCall("updateController",
					new Object[] { realFormName, sw.toString(), realUrl, Boolean.valueOf(forceLoad) });
			}
		}
		catch (IOException e)
		{
			Debug.error(e);
		}
	}

	@Override
	public void updateForm(Form form, String name)
	{
		String formUrl = "solutions/" + form.getSolution().getName() + "/forms/" + name + ".html";
		updateController(form, name, formUrl, false);
	}

	public void destroyForm(String name)
	{
		try
		{
			websocketSession.getClientService(NGRuntimeWindowManager.WINDOW_SERVICE).executeServiceCall("destroyController", new Object[] { name });
		}
		catch (IOException e)
		{
			Debug.error(e);
		}
	}

	public void formCreated(String formName)
	{
		String formUrl = getEndpoint().getFormUrl(formName);
		if (formUrl != null)
		{
			synchronized (formUrl)
			{
				getEndpoint().markFormCreated(formName);
				getSession().getEventDispatcher().resume(formUrl);
			}
		}
	}

}