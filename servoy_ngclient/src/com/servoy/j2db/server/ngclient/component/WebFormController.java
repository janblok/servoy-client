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

package com.servoy.j2db.server.ngclient.component;

import java.lang.ref.WeakReference;
import java.util.List;

import org.mozilla.javascript.Function;
import org.sablo.WebComponent;

import com.servoy.base.persistence.constants.IFormConstants;
import com.servoy.j2db.BasicFormController;
import com.servoy.j2db.DesignModeCallbacks;
import com.servoy.j2db.IBasicFormManager;
import com.servoy.j2db.IFormController;
import com.servoy.j2db.IView;
import com.servoy.j2db.dataprocessing.IRecordInternal;
import com.servoy.j2db.dataprocessing.PrototypeState;
import com.servoy.j2db.persistence.Form;
import com.servoy.j2db.scripting.JSApplication.FormAndComponent;
import com.servoy.j2db.scripting.JSEvent;
import com.servoy.j2db.server.ngclient.IDataAdapterList;
import com.servoy.j2db.server.ngclient.INGApplication;
import com.servoy.j2db.server.ngclient.IWebFormController;
import com.servoy.j2db.server.ngclient.IWebFormUI;
import com.servoy.j2db.server.ngclient.NGRuntimeWindow;
import com.servoy.j2db.server.ngclient.WebFormComponent;
import com.servoy.j2db.server.ngclient.WebFormUI;
import com.servoy.j2db.server.ngclient.WebListFormUI;

/**
 * @author lvostinar
 *
 */
public class WebFormController extends BasicFormController implements IWebFormController
{
	private int view;
	private WebFormUI formUI;
	private boolean rendering;

	public WebFormController(INGApplication application, Form form, String name)
	{
		super(application, form, name);
		initFormUI();
	}

	public void initFormUI()
	{
		Object parentContainer = null;
		if (formUI != null)
		{
			parentContainer = formUI.getParentContainer();
		}
		switch (form.getView())
		{
			case IFormConstants.VIEW_TYPE_TABLE :
			case IFormConstants.VIEW_TYPE_TABLE_LOCKED :
			case IFormConstants.VIEW_TYPE_LIST :
			case IFormConstants.VIEW_TYPE_LIST_LOCKED :
				formUI = new WebListFormUI(this);
				break;
			default :
				formUI = new WebFormUI(this);
		}
		if (parentContainer instanceof String)
		{
			formUI.setParentWindowName((String)parentContainer);
		}
		else if (parentContainer instanceof WebFormComponent)
		{
			formUI.setParentContainer((WebFormComponent)parentContainer);
		}
	}

	@Override
	public final INGApplication getApplication()
	{
		return (INGApplication)super.getApplication();
	}

	@Override
	public IWebFormUI getFormUI()
	{
		return formUI;
	}

	@Override
	public void setView(int view)
	{
		this.view = view;
	}

	@Override
	public int getView()
	{
		return view;
	}

	@Override
	public IBasicFormManager getBasicFormManager()
	{
		return getApplication().getFormManager();
	}

	@Override
	protected IView getViewComponent()
	{
		return formUI;
	}

	@Override
	public void showNavigator(List<Runnable> invokeLaterRunnables)
	{
		String parentWindowName = getFormUI().getParentWindowName();
		NGRuntimeWindow window = getApplication().getRuntimeWindowManager().getWindow(parentWindowName);
		// only show a navigator if it is the main window and this controller is the windows controller
		if (window != null && window.getParent() == null && window.getController() == this)
		{
			IFormController currentNavigator = window.getNavigator();
			int form_id = form.getNavigatorID();
			if (form_id > 0)
			{
				if (currentNavigator == null || currentNavigator.getForm().getID() != form_id)//is already there
				{
					if (currentNavigator != null)
					{
						currentNavigator.notifyVisible(false, invokeLaterRunnables);
					}
					Form navigator = application.getFlattenedSolution().getForm(form_id);
					if (navigator != null)
					{
						IFormController navigatorController = getApplication().getFormManager().getForm(navigator.getName());
						navigatorController.notifyVisible(true, invokeLaterRunnables);
					}
				}
				else
				{
					// Try to lease it extra so it will be added to last used screens.
					Form navigator = application.getFlattenedSolution().getForm(form_id);
					if (navigator != null)
					{
						getBasicFormManager().leaseFormPanel(navigator.getName());
					}
				}
			}
			else if (form_id != Form.NAVIGATOR_IGNORE)
			{
				if (currentNavigator != null) currentNavigator.notifyVisible(false, invokeLaterRunnables);
			}
			window.setNavigator(form_id);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.servoy.j2db.BasicFormController#stopUIEditing(boolean)
	 */
	@Override
	public boolean stopUIEditing(boolean looseFocus)
	{
		if (looseFocus && form.getOnRecordEditStopMethodID() != 0)
		{
			//allow beans to store there data via method
			IRecordInternal[] records = getApplication().getFoundSetManager().getEditRecordList().getUnmarkedEditedRecords(formModel);
			for (IRecordInternal element : records)
			{
				boolean b = executeOnRecordEditStop(element);
				if (!b) return false;
			}
		}
		return true;
	}

	public void setRendering(boolean rendering)
	{
		if (rendering == this.rendering) throw new IllegalArgumentException("rendering is already: " + this.rendering);
		this.rendering = rendering;
	}

	@Override
	public boolean isRendering()
	{
		return rendering;
	}

	@Override
	protected void refreshAllPartRenderers(IRecordInternal[] records)
	{
		if (!isFormVisible || application.isShutDown() || rendering) return;
		// don't do anything yet when there are records but the selection is invalid
		if (formModel != null && (formModel.getSize() > 0 && (formModel.getSelectedIndex() < 0 || formModel.getSelectedIndex() >= formModel.getSize()))) return;

		// let the ui know that it will be touched, so that locks can be taken if needed.
		boolean executeOnRecordSelect = false;
		IRecordInternal[] state = records;
		if (state == null)
		{
			if (formModel != null)
			{
				state = new IRecordInternal[] { formModel.getPrototypeState() };
			}
			else
			{
				state = new IRecordInternal[] { new PrototypeState(null) };
			}
		}
		if (!(records == null && formModel != null && formModel.getRawSize() > 0) && isStateChanged(state))
		{
			lastState = state;
			executeOnRecordSelect = true;
		}

		IDataAdapterList dataAdapterList = getFormUI().getDataAdapterList();
		for (IRecordInternal r : state)
			dataAdapterList.setRecord(r, true);


		if (executeOnRecordSelect)
		{
			// do this at the end because dataRenderer.refreshRecord(state) will update selection
			// for related tabs - and we should execute js code after they have been updated
			executeOnRecordSelect();
		}

	}

	@Override
	public void touch()
	{
	}

	@Override
	protected void focusFirstField()
	{
		// TODO Auto-generated method stub

	}

	@Override
	protected void focusField(String fieldName, boolean skipReadonly)
	{
		// TODO Auto-generated method stub

	}

	@Override
	public void propagateFindMode(boolean findMode)
	{
		if (!findMode)
		{
			application.getFoundSetManager().getEditRecordList().prepareForSave(true);
		}
		if (isReadOnly())
		{
			// TODO should something happen here, should edit state be pushed or is that just handled in the find mode call?
//			if (view != null)
//			{
//				view.setEditable(findMode);
//			}
		}
		IDataAdapterList dal = getFormUI().getDataAdapterList();
		dal.setFindMode(findMode);//disables related data en does getText instead if getValue on fields
	}

	@Override
	public void setReadOnly(boolean b)
	{
		formUI.setReadOnly(b);
	}

	@Override
	public boolean isReadOnly()
	{
		return formUI.isReadOnly();
	}

	@Override
	public boolean isEnabled()
	{
		return formUI.isEnabled();
	}

	@Override
	public void setComponentEnabled(boolean b)
	{
		formUI.setComponentEnabled(b);
	}

	@Override
	public boolean recreateUI()
	{
		Form f = application.getFlattenedSolution().getForm(form.getName());
		form = application.getFlattenedSolution().getFlattenedForm(f);
		getFormUI().init();
		((INGApplication)application).getWebsocketSession().updateForm(form, getName());
		return true;
	}

	@Override
	public void refreshView()
	{
	}

	@Override
	public boolean getDesignMode()
	{
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void setDesignMode(DesignModeCallbacks callback)
	{
		// TODO Auto-generated method stub

	}

	@Override
	public void setTabSequence(Object[] arrayOfElements)
	{
		// TODO Auto-generated method stub

	}

	@Override
	public String[] getTabSequence()
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getPartYOffset(int partType)
	{
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	protected FormAndComponent getJSApplicationNames(Object source, Function function, boolean useFormAsEventSourceEventually)
	{
		Object src = source;
		if (src == null && useFormAsEventSourceEventually) src = formScope;
		return new FormAndComponent(src, getName());
	}

	@Override
	protected JSEvent getJSEvent(Object src)
	{
		JSEvent event = new JSEvent();
		event.setType(JSEvent.EventType.form);
		event.setFormName(getName());
		event.setSource(src);
		event.setElementName(src instanceof WebComponent ? ((WebComponent)src).getName() : null);
		return event;
	}

	@Override
	public String toString()
	{
		if (formModel != null)
		{
			return "FormController[form: " + getName() + ", fs size:" + Integer.toString(formModel.getSize()) + ", selected record: " + formModel.getRecord(formModel.getSelectedIndex()) + ",destroyed:" + isDestroyed() + "]"; //$NON-NLS-1$ //$NON-NLS-2$//$NON-NLS-3$ //$NON-NLS-4$
		}
		else
		{
			return "FormController[form: " + getName() + ",destroyed:" + isDestroyed() + "]"; //$NON-NLS-1$//$NON-NLS-2$
		}
	}

	private WeakReference<IWebFormController> parentFormController;

	public void setParentFormController(IWebFormController parentFormController)
	{
		this.parentFormController = new WeakReference<IWebFormController>(parentFormController);
	}

	@Override
	public boolean notifyVisible(boolean visible, List<Runnable> invokeLaterRunnables)
	{
		boolean notifyVisibleSuccess = super.notifyVisible(visible, invokeLaterRunnables);
		if (parentFormController != null && !visible && notifyVisibleSuccess)
		{
			IWebFormController parentFC = parentFormController.get();
			if (parentFC != null) parentFC.getFormUI().getDataAdapterList().removeRelatedForm(this);
		}
		if (notifyVisibleSuccess)
		{
			getFormUI().setComponentVisible(visible);
		}
		return notifyVisibleSuccess;
	}
}
