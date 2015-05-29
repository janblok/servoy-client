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

import java.awt.Dimension;
import java.awt.Point;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;

import org.json.JSONObject;
import org.sablo.eventthread.IEventDispatcher;

import com.servoy.j2db.IBasicFormManager.History;
import com.servoy.j2db.IBasicMainContainer;
import com.servoy.j2db.IFormController;
import com.servoy.j2db.dataprocessing.PrototypeState;
import com.servoy.j2db.dataprocessing.TagResolver;
import com.servoy.j2db.persistence.Form;
import com.servoy.j2db.persistence.Solution;
import com.servoy.j2db.scripting.JSWindow;
import com.servoy.j2db.scripting.RuntimeWindow;
import com.servoy.j2db.server.ngclient.component.WebFormController;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.Text;
import com.servoy.j2db.util.Utils;

/**
 * @author jcompagner
 *
 */
public class NGRuntimeWindow extends RuntimeWindow implements IBasicMainContainer
{
	private final History history;
	private boolean visible;
	private String formName;
	private Integer navigatorID = null;

	/**
	 * @param application
	 * @param windowName
	 * @param windowType
	 * @param parentWindow
	 */
	protected NGRuntimeWindow(INGApplication application, String windowName, int windowType, RuntimeWindow parentWindow)
	{
		super(application, windowName, windowType, parentWindow);
		this.history = new History(application, this);
	}

	@Override
	public INGApplication getApplication()
	{
		return (INGApplication)super.getApplication();
	}

	@Override
	public String getContainerName()
	{
		return getName();
	}

	@Override
	public IWebFormController getController()
	{
		if (formName == null) return null;
		return getApplication().getFormManager().getForm(formName);
	}

	public IWebFormController getNavigator()
	{
		if (navigatorID != null && navigatorID > 0)
		{
			Form navigatorForm = getApplication().getFlattenedSolution().getForm(navigatorID);
			navigatorID = null;
			if (navigatorForm != null)
			{
				return getApplication().getFormManager().getForm(navigatorForm.getName());
			}
		}
		return null;
	}

	public void setNavigator(Integer navigatorID)
	{
		this.navigatorID = navigatorID;
	}

	@Override
	public void setController(IFormController form)
	{
		if (form != null)
		{
			this.formName = form.getName();
			switchForm((WebFormController)form);
		}
		else
		{
			this.formName = null;
		}
	}

	@Override
	public History getHistory()
	{
		return history;
	}


	@Override
	public void resetBounds()
	{
		this.storeBounds = false;
		getApplication().getWebsocketSession().getClientService(NGRuntimeWindowManager.WINDOW_SERVICE).executeAsyncServiceCall("resetBounds",
			new Object[] { this.getName() });

	}

	@Override
	public void setLocation(int x, int y)
	{
		Map<String, Integer> location = new HashMap<>();
		location.put("x", x);
		location.put("y", y);
		getApplication().getWebsocketSession().getClientService(NGRuntimeWindowManager.WINDOW_SERVICE).executeAsyncServiceCall("setLocation",
			new Object[] { this.getName(), location });
	}


	@Override
	public int getX()
	{
		Point clientLocation = getClientLocation();
		return clientLocation != null ? clientLocation.x : -1;
	}

	@Override
	public int getY()
	{
		Point clientLocation = getClientLocation();
		return clientLocation != null ? clientLocation.y : -1;
	}

	private Point getClientLocation()
	{
		try
		{
			Object retValue = getApplication().getWebsocketSession().getClientService(NGRuntimeWindowManager.WINDOW_SERVICE).executeServiceCall("getLocation",
				new Object[] { this.getName() });
			if (retValue instanceof JSONObject)
			{
				return new Point(((JSONObject)retValue).optInt("x", -1), ((JSONObject)retValue).optInt("y", -1));
			}
		}
		catch (IOException e)
		{
			Debug.error(e);
		}
		return null;
	}

	@Override
	public void setSize(int width, int height)
	{
		Map<String, Integer> size = new HashMap<>();
		size.put("width", width);
		size.put("height", height);
		getApplication().getWebsocketSession().getClientService(NGRuntimeWindowManager.WINDOW_SERVICE).executeAsyncServiceCall("setSize",
			new Object[] { this.getName(), size });
	}

	@Override
	public int getWidth()
	{
		Dimension clientSize = getClientSize();
		return clientSize != null ? clientSize.width : -1;
	}

	@Override
	public int getHeight()
	{
		Dimension clientSize = getClientSize();
		return clientSize != null ? clientSize.height : -1;
	}

	private Dimension getClientSize()
	{
		try
		{
			Object retValue = getApplication().getWebsocketSession().getClientService(NGRuntimeWindowManager.WINDOW_SERVICE).executeServiceCall("getSize",
				new Object[] { this.getName() });
			if (retValue instanceof JSONObject)
			{
				return new Dimension(((JSONObject)retValue).optInt("width", -1), ((JSONObject)retValue).optInt("height", -1));
			}
		}
		catch (IOException e)
		{
			Debug.error(e);
		}
		return null;
	}

	@Override
	public void setInitialBounds(int x, int y, int width, int height)
	{
		super.setInitialBounds(x, y, width, height);
		Map<String, Integer> initialBounds = new HashMap<>();
		initialBounds.put("x", this.initialBounds.x);
		initialBounds.put("y", this.initialBounds.y);
		initialBounds.put("width", this.initialBounds.width);
		initialBounds.put("height", this.initialBounds.height);
		getApplication().getWebsocketSession().getClientService(NGRuntimeWindowManager.WINDOW_SERVICE).executeAsyncServiceCall("setInitialBounds",
			new Object[] { this.getName(), initialBounds });
	}

	@Override
	public void setTitle(String title, boolean delayed)
	{
		super.setTitle(title);
		sendTitle(title);
	}

	@Override
	public void setTitle(String title)
	{
		sendTitle(title);
	}

	private void sendTitle(String title)
	{
		String titleString = "";
		if (windowType == 2)
		{
			Solution solution = getApplication().getSolution();
			String solutionTitle = solution.getTitleText();
			if (solutionTitle == null)
			{
				titleString = solution.getName();
			}
			else if (!solutionTitle.equals("<empty>")) //$NON-NLS-1$
			{
				titleString = solutionTitle;
			}

			titleString = getApplication().getI18NMessageIfPrefixed(titleString);

			if (title != null && !title.trim().equals("") && !"<empty>".equals(title) && title != null) //$NON-NLS-1$ //$NON-NLS-2$
			{
				String nameString = getApplication().getI18NMessageIfPrefixed(title);
				IWebFormController formController = getController();
				if (formController != null)
				{
					String name2 = Text.processTags(nameString, formController.getFormUI().getDataAdapterList());
					if (name2 != null) nameString = name2;
				}
				else
				{
					String name2 = Text.processTags(nameString, TagResolver.createResolver(new PrototypeState(null)));
					if (name2 != null) nameString = name2;
				}
				if (!nameString.trim().equals("")) //$NON-NLS-1$
				{
					if ("".equals(titleString)) //$NON-NLS-1$
					{
						titleString += nameString;
					}
					else
					{
						titleString += " - " + nameString; //$NON-NLS-1$
					}
				}
			}
			String appName = "Servoy Web Client"; //$NON-NLS-1$
			boolean branding = Utils.getAsBoolean(getApplication().getSettings().getProperty("servoy.branding", "false")); //$NON-NLS-1$ //$NON-NLS-2$
			String appTitle = getApplication().getSettings().getProperty("servoy.branding.windowtitle"); //$NON-NLS-1$
			if (branding && appTitle != null)
			{
				appName = appTitle;
			}
			if (titleString.equals("")) //$NON-NLS-1$
			{
				titleString = appName;
			}
			else
			{
				titleString += " - " + appName; //$NON-NLS-1$
			}
		}
		else
		{
			titleString = title;
		}
		getApplication().getWebsocketSession().getClientService(NGRuntimeWindowManager.WINDOW_SERVICE).executeAsyncServiceCall("setTitle",
			new Object[] { this.getName(), titleString });
	}

	@Override
	public boolean isVisible()
	{
		return visible;
	}

	@Override
	public void toFront()
	{
		getApplication().getWebsocketSession().getClientService(NGRuntimeWindowManager.WINDOW_SERVICE).executeAsyncServiceCall("toFront",
			new Object[] { this.getName() });

	}

	@Override
	public void toBack()
	{
		getApplication().getWebsocketSession().getClientService(NGRuntimeWindowManager.WINDOW_SERVICE).executeAsyncServiceCall("toBack",
			new Object[] { this.getName() });
	}

	@Override
	public Object getWrappedObject()
	{
		return this;
	}

	@Override
	public void setOpacity(float opacity)
	{
		super.setOpacity(opacity);
		getApplication().getWebsocketSession().getClientService(NGRuntimeWindowManager.WINDOW_SERVICE).executeAsyncServiceCall("setOpacity",
			new Object[] { getName(), opacity });
	}

	@Override
	public void setResizable(boolean resizable)
	{
		super.setResizable(resizable);
		getApplication().getWebsocketSession().getClientService(NGRuntimeWindowManager.WINDOW_SERVICE).executeAsyncServiceCall("setResizable",
			new Object[] { getName(), resizable });
	}

	@Override
	public void setUndecorated(boolean undecorated)
	{
		super.setUndecorated(undecorated);
		getApplication().getWebsocketSession().getClientService(NGRuntimeWindowManager.WINDOW_SERVICE).executeAsyncServiceCall("setUndecorated",
			new Object[] { getName(), undecorated });
	}

	@Override
	public void setTransparent(boolean isTransparent)
	{
		super.setTransparent(isTransparent);
		getApplication().getWebsocketSession().getClientService(NGRuntimeWindowManager.WINDOW_SERVICE).executeAsyncServiceCall("setTransparent",
			new Object[] { getName(), isTransparent });
	}

	@Override
	public void hideUI()
	{
		visible = false;
		getApplication().getWebsocketSession().getClientService(NGRuntimeWindowManager.WINDOW_SERVICE).executeAsyncServiceCall("hide",
			new Object[] { getName() });

		// assume that just after hiding the window, currentController in js is the main window controller
		IFormController formController = getApplication().getRuntimeWindowManager().getMainApplicationWindow().getController();
		if (formController instanceof IWebFormController) getApplication().getFormManager().setCurrentControllerJS((IWebFormController)formController);

		// resume
		if (windowType == JSWindow.MODAL_DIALOG && getApplication().getWebsocketSession().getEventDispatcher() != null)
		{
			getApplication().getWebsocketSession().getEventDispatcher().resume(this);
		}
	}

	@Override
	public void setStoreBounds(boolean storeBounds)
	{
		super.setStoreBounds(storeBounds);
		getApplication().getWebsocketSession().getClientService(NGRuntimeWindowManager.WINDOW_SERVICE).executeAsyncServiceCall("setStoreBounds",
			new Object[] { getName(), String.valueOf(storeBounds) });
	}

	@Override
	protected void doOldShow(String formName, boolean closeAll, boolean legacyV3Behavior)
	{
		IWebFormController controller = getApplication().getFormManager().getForm(formName);
		if (controller != null)
		{
			getApplication().getFormManager().showFormInContainer(formName, this, getTitle(), true, windowName);
			this.formName = formName;
			controller.getFormUI().setParentWindowName(getName());
			//show panel as main
			switchForm(controller);
		}

		String titleArg = getTitle();
		titleArg = titleArg == null ? formName : titleArg;
		getApplication().getWebsocketSession().getClientService(NGRuntimeWindowManager.WINDOW_SERVICE).executeAsyncServiceCall("show",
			new Object[] { getName(), formName, titleArg });

		if (windowType == JSWindow.MODAL_DIALOG && getApplication().getWebsocketSession().getEventDispatcher() != null)
		{
			try
			{
				getApplication().getWebsocketSession().getEventDispatcher().suspend(this, IEventDispatcher.EVENT_LEVEL_DEFAULT, IEventDispatcher.NO_TIMEOUT);
			}
			catch (CancellationException e)
			{
				throw e; // full browser refresh while waiting for modal to close?
			}
			catch (TimeoutException e)
			{
				Debug.error("Modal dialog suspend timed out. This should never happen");
			}
		}
	}

	private void switchForm(IWebFormController currentForm)
	{
		visible = true;
		// set the parent and current window ,
		currentForm.getFormUI().setParentWindowName(getName());
		getApplication().getFormManager().getFormAndSetCurrentWindow(formName);
		Map<String, Object> mainForm = new HashMap<String, Object>();
		Map<String, Integer> size = new HashMap<>();
		size.put("width", currentForm.getForm().getSize().width);
		size.put("height", currentForm.getForm().getSize().height);
		mainForm.put("size", size);
		mainForm.put("name", currentForm.getName());

		Map<String, Object> navigatorForm = getNavigatorProperties(currentForm);
		NGClientWindow.getCurrentWindow().touchForm(currentForm.getForm(), null, true);
		getApplication().getWebsocketSession().getClientService(NGRuntimeWindowManager.WINDOW_SERVICE).executeAsyncServiceCall("switchForm",
			new Object[] { getName(), mainForm, navigatorForm });
		sendTitle(title);
	}

	/**
	 * Get the navigator properties based on the navigatorID of the form.
	 * @param formController
	 * @return a map which contains navigator properties such as name, size.
	 */
	private Map<String, Object> getNavigatorProperties(IWebFormController formController)
	{
		Map<String, Object> navigatorForm = new HashMap<String, Object>();
		int navigatorId = formController.getForm().getNavigatorID();
		if (formController.getFormUI() instanceof WebListFormUI && navigatorId == Form.NAVIGATOR_DEFAULT)
		{
			navigatorId = Form.NAVIGATOR_NONE;
		}
		switch (navigatorId)
		{
			case Form.NAVIGATOR_NONE :
				break;
			case Form.NAVIGATOR_DEFAULT :
			{
				navigatorForm.put("name", "servoydefault/navigator/default_navigator_container.html");
				Map<String, Integer> navSize = new HashMap<>();
				navSize.put("width", 70);
				navigatorForm.put("size", navSize);
				break;
			}
			case Form.NAVIGATOR_IGNORE :
			{
				if (history.getIndex() > 0)
				{
					String prevForm = history.getFormName(history.getIndex() - 1);
					if (prevForm != null)
					{
						navigatorForm = getApplication().getFormManager().getForm(prevForm).getNavigatorProperties();
					}
				}
				break;
			}
			default :
			{
				Form navForm = getApplication().getFlattenedSolution().getForm(navigatorId);
				if (navForm != null)
				{
					getApplication().getFormManager().getForm(navForm.getName()).getFormUI().setParentWindowName(getName());
					navigatorForm.put("name", navForm.getName());
					Map<String, Integer> navSize = new HashMap<>();
					navSize.put("width", navForm.getSize().width);
					navSize.put("height", navForm.getSize().height);
					navigatorForm.put("size", navSize);
					NGClientWindow.getCurrentWindow().touchForm(getApplication().getFlattenedSolution().getFlattenedForm(navForm), null, true);
				}
			}
		}


		if (navigatorForm.isEmpty()) // Form.NAVIGATOR_NONE
		{
			// just make it an empty object.
			Map<String, Integer> navSize = new HashMap<>();
			navSize.put("width", 0);
			navigatorForm.put("size", navSize);
		}

		formController.setNavigatorProperties(navigatorForm);
		return navigatorForm;
	}

	@Override
	public void destroy()
	{
		super.destroy();
		if (visible) hideUI();
		getApplication().getWebsocketSession().getClientService(NGRuntimeWindowManager.WINDOW_SERVICE).executeAsyncServiceCall("destroy",
			new Object[] { getName() });
	}
}
