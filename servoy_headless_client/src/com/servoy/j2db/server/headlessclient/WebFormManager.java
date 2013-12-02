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

import java.awt.Rectangle;
import java.awt.print.PrinterJob;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.wicket.IPageMap;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.PageMap;
import org.apache.wicket.RequestCycle;
import org.apache.wicket.ResourceReference;
import org.apache.wicket.Session;

import com.servoy.j2db.FormController;
import com.servoy.j2db.FormManager;
import com.servoy.j2db.IApplication;
import com.servoy.j2db.IFormUIInternal;
import com.servoy.j2db.IMainContainer;
import com.servoy.j2db.dataprocessing.FoundSet;
import com.servoy.j2db.dataprocessing.IFoundSetInternal;
import com.servoy.j2db.persistence.Form;
import com.servoy.j2db.persistence.Solution;
import com.servoy.j2db.server.headlessclient.dataui.IWebFormContainer;
import com.servoy.j2db.smart.SwingForm;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.ServoyException;
import com.servoy.j2db.util.Settings;
import com.servoy.j2db.util.Utils;

/**
 * A {@link FormManager} implementation that is used in the webclient. 
 * 
 * @author jcompagner
 */
public class WebFormManager extends FormManager implements IProvideGlobalResources
{
	private final int maxForms;
	// resources that must be present on all pages
	private final List<Object> addedJSResources = new ArrayList<Object>();
	private final List<Object> addedCSSResources = new ArrayList<Object>();

	@SuppressWarnings("nls")
	public WebFormManager(IApplication app, IMainContainer mainp)
	{
		super(app, mainp);
		int max = Utils.getAsInteger(Settings.getInstance().getProperty("servoy.max.webforms.loaded", "128"), false);
		maxForms = max == 0 ? 128 : max;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.servoy.j2db.FormManager#makeSolutionSettings(com.servoy.j2db.persistence.Solution)
	 */
	@Override
	protected void makeSolutionSettings(Solution s)
	{
		super.makeSolutionSettings(s);
		if (getCurrentContainer() instanceof MainPage)
		{
			// store the minimum version of the current main container loading
			// this new solution. So that it can't go past it again when using 
			// the back button in the browser
			((MainPage)getCurrentContainer()).storeMinVersion();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.servoy.j2db.FormManager#getMaxFormsLoaded()
	 */
	@Override
	protected int getMaxFormsLoaded()
	{
		return maxForms;
	}

	@Override
	public IFormUIInternal getFormUI(FormController formController)
	{
		if (Utils.getAsBoolean(getApplication().getRuntimeProperties().get("isPrinting"))) //$NON-NLS-1$
		{
			return new SwingForm(formController);
		}
		return new WebForm(formController);
	}

	protected void addIPersistListenerHook()
	{
	}

	@Override
	protected boolean checkAndUpdateFormUser(FormController fp, Object parentContainer)
	{
		if (parentContainer instanceof MainPage)
		{
			return ((MainPage)parentContainer).getMainWebForm() != fp.getFormUI();
		}

		return true;
	}

	@Override
	protected void enableCmds(boolean enable)
	{
	}

	@Override
	public void fillScriptMenu()
	{
	}

	public void synchViewMenu(int viewType)
	{
	}

	protected void removeIPersistListenerHook()
	{
	}

	@Override
	protected void selectFormMenuItem(Form form)
	{
	}

	@Override
	protected boolean isShowingPrintPreview()
	{
		// Web Printing doesn't have to show the forms, it prints to PDF,t hat code sits in 
		// WebForm.print(boolean showDialogs, boolean printCurrentRecordOnly, boolean showPrinterSelectDialog, PrinterJob printerJob)
		return false;
	}

	@Override
	protected FormController removePreview()
	{
		// Web Printing doesn't have to show the forms, it prints to PDF,t hat code sits in 
		// WebForm.print(boolean showDialogs, boolean printCurrentRecordOnly, boolean showPrinterSelectDialog, PrinterJob printerJob)
		return null;
	}


	@Override
	public void showPreview(FormController afp, IFoundSetInternal foundset, int zoomFactor, PrinterJob printJob)
	{
		// Web Printing doesn't have to show the forms, it prints to PDF,t hat code sits in 
		// WebForm.print(boolean showDialogs, boolean printCurrentRecordOnly, boolean showPrinterSelectDialog, PrinterJob printerJob)
	}

	@Override
	protected void removeFormUser(FormController fp)
	{
	}

	public void reload(FormController[] fcontrollers)
	{
		HashSet<MainPage> mainPages = new HashSet<MainPage>();
		for (FormController fc : fcontrollers)
		{
			WebForm formUI = (WebForm)fc.getFormUI();
			MainPage mp = formUI.findParent(MainPage.class);
			if (mp != null && mainPages.add(mp))
			{
				mp.setVersioned(false);
				mp.setMainPageSwitched();
			}
		}

		String navigatorName = null;
		Set<MainPage> parents = new HashSet<MainPage>();
		for (FormController fp : fcontrollers)
		{
			if (fp != null)
			{
				boolean formVisible = fp.isFormVisible();
				FoundSet foundset = fp.getFormModel();
				WebForm wf = (WebForm)fp.getFormUI();
				MarkupContainer wfParent = wf.getParent();

				boolean refresh = false;
				//datasource has changed, but foundset has not
				if (foundset != null && !Utils.equalObjects(foundset.getDataSource(), fp.getDataSource()))
				{
					try
					{
						foundset = (FoundSet)(getApplication()).getFoundSetManager().getSharedFoundSet(fp.getDataSource());
						foundset.loadAllRecords();
						refresh = true;
					}
					catch (ServoyException e)
					{
						Debug.error("Failed to reload foundset.", e); //$NON-NLS-1$
					}
				}

				fp.destroy();

				while (wfParent != null && !(wfParent instanceof IWebFormContainer) && !(wfParent.getParent() instanceof MainPage) &&
					!(wfParent.getParent() instanceof WebForm))
				{
					wfParent = wfParent.getParent();
				}

				if (wfParent instanceof IWebFormContainer)
				{
					if (formVisible)
					{
						FormController fc = leaseFormPanel(fp.getName());
						if (fc != null)
						{
							// form was deleted in developer?
							fc.loadData(foundset, null);
							if (refresh) fc.recreateUI();
							List<Runnable> runnables = new ArrayList<Runnable>();
							((IWebFormContainer)wfParent).notifyVisible(true, runnables);
							Utils.invokeLater(getApplication(), runnables);
						}
					}
				}
				else if (wfParent != null)
				{
					WebForm parentWF = wfParent.findParent(WebForm.class);
					if (parentWF != null)
					{
						if (!Arrays.asList(fcontrollers).contains(parentWF.getController())) parentWF.getController().destroy();
					}
					else
					{
						MainPage parent = wfParent.findParent(MainPage.class);
						if (parent != null && !parents.contains(parent))
						{
							parents.add(parent);

							if (parent.getNavigator() == fp)
							{
								navigatorName = fp.getName();
								FormController navigator = getFormController(navigatorName, parent);
								if (navigator != null)
								{
									List<Runnable> invokeLaterRunnables = new ArrayList<Runnable>();
									navigator.notifyVisible(true, invokeLaterRunnables);
									Utils.invokeLater(getApplication(), invokeLaterRunnables);
									parent.setNavigator(navigator);
									//parent.triggerBrowserRequestIfNeeded(); // FIXME: this is needed here but currently does nothing because the request target is not yet set
								}
							}

							FormController previousMainShowingForm = (parent != null ? parent.getController() : null);
							if (previousMainShowingForm != null)
							{
								FormController previousNavigator = parent.getNavigator();
								parent.setFormController(null);
								// navigator is not re-applied so apply it manually
								int navigatorID = previousMainShowingForm.getForm().getNavigatorID();
								if (navigatorID == Form.NAVIGATOR_IGNORE || (previousNavigator != null && previousNavigator.getForm().getID() == navigatorID))
								{
									parent.setNavigator(previousNavigator);
								}
								else if (navigatorID > 0)
								{
									Form newNavigator = application.getFlattenedSolution().getForm(navigatorID);
									if (newNavigator != null)
									{
										parent.setNavigator(leaseFormPanel(newNavigator.getName()));
									}
								}
								showFormInMainPanel(previousMainShowingForm.getName(), parent, null, true, null);
//								parent.triggerBrowserRequestIfNeeded(); // FIXME: this is needed here but currently does nothing because the request target is not yet set
							}
						}
					}
				}
			}
		}

		for (MainPage mainPage : mainPages)
		{
			mainPage.setVersioned(true);
		}
	}

	public void reload()
	{
		FormController[] array = createdFormControllers.values().toArray(new FormController[0]);
		createdFormControllers.clear();
		leaseHistory.clear();

		reload(array);
	}

	@Override
	public void showFormInDialog(String formName, Rectangle bounds, String title, boolean resizeble, boolean showTextToolbar, boolean closeAll, boolean modal,
		String windowName)
	{
		// with old implementation, non-modal dialogs in WC were actually separate browser windows; keep it compatible with new implementation
		if (modal)
		{
			super.showFormInDialog(formName, bounds, title, resizeble, showTextToolbar, closeAll, modal, windowName);
		}
		else
		{
			super.showFormInFrame(formName, bounds, title, resizeble, showTextToolbar, windowName);
		}
	}

	/**
	 * @see com.servoy.j2db.FormManager#getOrCreateMainContainer(java.lang.String)
	 */
	@Override
	public IMainContainer getOrCreateMainContainer(String nm)
	{
		String name = nm;
		if (name != null && name.trim().length() == 0)
		{
			// blank names would make PageMap throw an exception or generate JS errors
			name = "blank_name"; //$NON-NLS-1$
		}
		IMainContainer container = getMainContainer(name);
		if (container == null)
		{
			WebClient wc = (WebClient)getApplication();
			IPageMap pageMap = PageMap.forName(name);
			container = new MainPage(wc, pageMap);
			containers.put(name, container);
		}
		return container;
	}

	@Override
	protected void destroyContainer(IMainContainer container)
	{
		if (container instanceof MainPage)
		{
			((MainPage)container).close();
		}
		super.destroyContainer(container);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.servoy.j2db.FormManager#destroySolutionSettings()
	 */
	@Override
	protected void destroySolutionSettings()
	{
		super.destroySolutionSettings();
		if (RequestCycle.get() != null) Session.get().setMetaData(Session.PAGEMAP_ACCESS_MDK, null); // reset all pagemap accesses. 
	}

	@Override
	public void addGlobalJSResourceReference(ResourceReference resource)
	{
		if (resource != null && !addedJSResources.contains(resource))
		{
			addedJSResources.add(resource);
		}
	}

	@Override
	public void addGlobalJSResourceReference(String url)
	{
		if (url != null && !addedJSResources.contains(url))
		{
			addedJSResources.add(url);
		}
	}

	@Override
	public void addGlobalCSSResourceReference(ResourceReference resource)
	{
		if (resource != null && !addedCSSResources.contains(resource))
		{
			addedCSSResources.add(resource);
		}
	}

	@Override
	public void addGlobalCSSResourceReference(String url)
	{
		if (url != null && !addedCSSResources.contains(url))
		{
			addedCSSResources.add(url);
		}
	}

	@Override
	public void removeGlobalResourceReference(ResourceReference resource)
	{
		if (resource != null)
		{
			addedCSSResources.remove(resource);
			addedJSResources.remove(resource);
		}
	}

	@Override
	public void removeGlobalResourceReference(String url)
	{
		if (url != null)
		{
			addedCSSResources.remove(url);
			addedJSResources.remove(url);
		}
	}

	@Override
	public List<Object> getGlobalJSResources()
	{
		return new ArrayList<Object>(addedJSResources);
	}

	@Override
	public List<Object> getGlobalCSSResources()
	{
		return new ArrayList<Object>(addedCSSResources);
	}
}
