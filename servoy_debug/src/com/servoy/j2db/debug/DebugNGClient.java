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

package com.servoy.j2db.debug;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.sablo.eventthread.WebsocketSessionEndpoints;
import org.sablo.specification.WebComponentSpecification;
import org.sablo.specification.WebServiceSpecProvider;
import org.sablo.websocket.utils.JSONUtils.FullValueToJSONConverter;

import com.servoy.j2db.IDebugClient;
import com.servoy.j2db.IDesignerCallback;
import com.servoy.j2db.IFormController;
import com.servoy.j2db.persistence.FlattenedForm;
import com.servoy.j2db.persistence.Form;
import com.servoy.j2db.persistence.IPersist;
import com.servoy.j2db.persistence.Solution;
import com.servoy.j2db.scripting.IExecutingEnviroment;
import com.servoy.j2db.scripting.PluginScope;
import com.servoy.j2db.server.ngclient.ComponentFactory;
import com.servoy.j2db.server.ngclient.INGClientWebsocketSession;
import com.servoy.j2db.server.ngclient.NGClient;
import com.servoy.j2db.server.ngclient.NGRuntimeWindowManager;
import com.servoy.j2db.server.ngclient.component.WebFormController;
import com.servoy.j2db.server.ngclient.scripting.WebServiceScriptable;
import com.servoy.j2db.util.ILogLevel;

/**
 * @author jcompagner
 *
 */
public class DebugNGClient extends NGClient implements IDebugClient
{
	private final IDesignerCallback designerCallback;

	/**
	 * @param webSocketClientEndpoint
	 * @param designerCallback
	 */
	public DebugNGClient(INGClientWebsocketSession wsSession, IDesignerCallback designerCallback) throws Exception
	{
		super(wsSession);
		this.designerCallback = designerCallback;
	}

	@Override
	protected IExecutingEnviroment createScriptEngine()
	{
		RemoteDebugScriptEngine engine = new RemoteDebugScriptEngine(this);
		WebComponentSpecification[] serviceSpecifications = WebServiceSpecProvider.getInstance().getWebServiceSpecifications();
		PluginScope scope = (PluginScope)engine.getSolutionScope().get("plugins", engine.getSolutionScope());
		scope.setLocked(false);
		for (WebComponentSpecification serviceSpecification : serviceSpecifications)
		{
			scope.put(serviceSpecification.getName(), scope, new WebServiceScriptable(this, serviceSpecification, engine.getSolutionScope()));
		}
		scope.setLocked(true);
		if (designerCallback != null)
		{
			designerCallback.addScriptObjects(this, engine.getSolutionScope());
		}
		return engine;
	}

	@Override
	public void output(Object msg, int level)
	{
		super.output(msg, level);
		if (level == ILogLevel.WARNING || level == ILogLevel.ERROR)
		{
			DebugUtils.errorToDebugger(getScriptEngine(), msg.toString(), null);
		}
		else
		{
			DebugUtils.stdoutToDebugger(getScriptEngine(), msg);
		}
	}

	/**
	 * @see com.servoy.j2db.smart.J2DBClient#reportJSError(java.lang.String, java.lang.Object)
	 */
	@Override
	public void reportJSError(String message, Object detail)
	{
		DebugUtils.errorToDebugger(getScriptEngine(), message, detail);
		super.reportJSError(message, detail);
	}

	/**
	 * @see com.servoy.j2db.ClientState#reportError(java.lang.String, java.lang.Object)
	 */
	@Override
	public void reportError(String message, Object detail)
	{
		DebugUtils.errorToDebugger(getScriptEngine(), message, detail);
		super.reportError(message, detail);
	}

	@Override
	public void reportJSWarning(String s)
	{
		DebugUtils.errorToDebugger(getScriptEngine(), s, null);
		super.reportJSWarning(s);
	}

	@Override
	public void reportJSInfo(String s)
	{
		DebugUtils.stdoutToDebugger(getScriptEngine(), "INFO: " + s);
		super.reportJSInfo(s);
	}

	@Override
	public void setCurrent(Solution current)
	{
		// TODO Auto-generated method stub

	}

	@Override
	public void refreshForI18NChange(boolean recreateForms)
	{
		// TODO Auto-generated method stub

	}

	@Override
	public void refreshPersists(Collection<IPersist> changes)
	{
		if (isShutDown()) return;

		Set<IFormController>[] scopesAndFormsToReload = DebugUtils.getScopesAndFormsToReload(this, changes);

		if (scopesAndFormsToReload[1].size() > 0)
		{
			ComponentFactory.reload();
			List<Runnable> invokeLaterRunnables = new ArrayList<Runnable>();
			for (IFormController controller : scopesAndFormsToReload[1])
			{
				boolean isVisible = controller.isFormVisible();
				if (isVisible) controller.notifyVisible(false, invokeLaterRunnables);
				((WebFormController)controller).initFormUI();
				if (isVisible) controller.notifyVisible(true, invokeLaterRunnables);
			}
			WebsocketSessionEndpoints allendpoints = new WebsocketSessionEndpoints(getWebsocketSession());
			allendpoints.executeAsyncServiceCall(NGRuntimeWindowManager.WINDOW_SERVICE, "reload", null, null);
			try
			{
				allendpoints.sendMessage(null, null, true, FullValueToJSONConverter.INSTANCE);
			}
			catch (IOException e)
			{
				reportError("error sending changes to the client", e);
			}
		}

		for (IFormController controller : scopesAndFormsToReload[0])
		{
			if (controller.getForm() instanceof FlattenedForm)
			{
				FlattenedForm ff = (FlattenedForm)controller.getForm();
				ff.reload();
			}
			controller.getFormScope().reload();
		}


	}

	/**
	 * @param form
	 */
	public void show(Form form)
	{
		// TODO Auto-generated method stub
	}

	@Override
	protected void showInfoPanel()
	{
		//ignore
	}

	@Override
	public boolean isInDesigner()
	{
		return false;
	}
}
