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

import java.util.ArrayList;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONString;
import org.sablo.services.server.FormServiceHandler;
import org.sablo.websocket.utils.JSONUtils.IToJSONConverter;

import com.servoy.j2db.dataprocessing.FoundSet;
import com.servoy.j2db.dataprocessing.IRecordInternal;
import com.servoy.j2db.persistence.Form;
import com.servoy.j2db.server.ngclient.component.WebFormController;
import com.servoy.j2db.server.ngclient.property.types.NGConversions.InitialToJSONConverter;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.SecuritySupport;
import com.servoy.j2db.util.Settings;
import com.servoy.j2db.util.Utils;


/**
 * FormService implementation to handle methods at form level.
 *
 * @author rgansevles
 *
 */
public class NGFormServiceHandler extends FormServiceHandler
{
	private final INGApplication client;

	public NGFormServiceHandler(INGApplication client)
	{
		this.client = client;
	}

	protected INGApplication getApplication()
	{
		return client;
	}

	@Override
	protected IToJSONConverter getInitialRequestDataConverter()
	{
		return InitialToJSONConverter.INSTANCE;
	}

	@Override
	public Object executeMethod(String methodName, JSONObject args) throws Exception
	{
		switch (methodName)
		{
			case "svyPush" :
			{
				String formName = args.getString("formname");
				IWebFormUI form = (IWebFormUI)NGClientWindow.getCurrentWindow().getForm(formName);
				if (form == null)
				{
					log.warn("svyPush for unknown form '" + formName + "'");
				}
				else
				{
					dataPush(args);
					WebFormComponent webComponent = form.getWebComponent(args.getString("beanname"));
					form.getDataAdapterList().pushChanges(webComponent, args.getString("property"));
				}
				break;
			}

			case "startEdit" :
			{
				IWebFormUI form = getApplication().getFormManager().getFormAndSetCurrentWindow(args.optString("formname")).getFormUI();
				form.getDataAdapterList().startEdit(form.getWebComponent(args.optString("beanname")), args.optString("property"));
				break;
			}

			case "executeInlineScript" :
			{
				try
				{
					String formName = args.optString("formname", null);
					if (formName == null)
					{
						formName = getApplication().getFormManager().getCurrentForm().getName();
					}
					else
					{
						formName = SecuritySupport.decrypt(Settings.getInstance(), formName);
					}
					IWebFormUI form = getApplication().getFormManager().getFormAndSetCurrentWindow(formName).getFormUI();
					form.getDataAdapterList().executeInlineScript(args.optString("script"), args.optJSONObject("params"), args.optJSONArray("params"));
				}
				catch (Exception ex)
				{
					Debug.error("Cannot execute inline script", ex);
				}
				break;
			}

			case "formreadOnly" :
			{
				IWebFormController form = getApplication().getFormManager().getForm(args.optString("formname"));
				if (form != null)
				{
					((WebFormController)form).setReadOnly(args.optBoolean("readOnly", false));
				}
				break;
			}

			case "formenabled" :
			{
				IWebFormController form = getApplication().getFormManager().getForm(args.optString("formname"));
				if (form != null)
				{
					((WebFormController)form).setComponentEnabled(args.optBoolean("enabled", true));
				}
				break;
			}

			case "formvisibility" :
			{
				IWebFormController parentForm = null;
				IWebFormController controller = null;
				String formName = args.optString("formname");
				if (args.has("parentForm") && !args.isNull("parentForm"))
				{
					parentForm = getApplication().getFormManager().getFormAndSetCurrentWindow(args.optString("parentForm"));
					controller = getApplication().getFormManager().getForm(formName);
				}
				else
				{
					controller = getApplication().getFormManager().getFormAndSetCurrentWindow(formName);
				}
				List<Runnable> invokeLaterRunnables = new ArrayList<Runnable>();
				boolean isVisible = args.getBoolean("visible");
				boolean ok = controller.notifyVisible(isVisible, invokeLaterRunnables);
				if (ok && parentForm != null)
				{
					WebFormComponent containerComponent = parentForm.getFormUI().getWebComponent(args.getString("bean"));
					if (containerComponent != null)
					{
						containerComponent.updateVisibleForm(controller.getFormUI(), isVisible, args.optInt("formIndex"));
					}
					if (isVisible && args.has("relation") && !args.isNull("relation"))
					{
						String relation = args.getString("relation");
						FoundSet parentFs = parentForm.getFormModel();
						IRecordInternal selectedRecord = (IRecordInternal)parentFs.getSelectedRecord();
						if (selectedRecord != null)
						{
							controller.loadRecords(selectedRecord.getRelatedFoundSet(relation));
						}
						else
						{
							// no selected record, then use prototype so we can get global relations
							controller.loadRecords(parentFs.getPrototypeState().getRelatedFoundSet(relation));
						}
						parentForm.getFormUI().getDataAdapterList().addRelatedForm(controller, relation, true);
					}
				}
				Utils.invokeLater(getApplication(), invokeLaterRunnables);
				Form form = getApplication().getFormManager().getPossibleForm(formName);
				if (form != null) NGClientWindow.getCurrentWindow().touchForm(getApplication().getFlattenedSolution().getFlattenedForm(form), formName, true);
				return Boolean.valueOf(ok);
			}
			case "formLoaded" :
			{
				NGClientWindow.getCurrentWindow().formCreated(args.optString("formname"));
				break;
			}

			default :
			{
				return super.executeMethod(methodName, args);
			}
		}

		return null;
	}


	@Override
	protected JSONString requestData(String formName) throws JSONException
	{
		getApplication().getFormManager().getFormAndSetCurrentWindow(formName);
		return super.requestData(formName);
	}

	@Override
	public int getMethodEventThreadLevel(String methodName, JSONObject arguments)
	{
		if ("formLoaded".equals(methodName)) return EVENT_LEVEL_INITIAL_FORM_DATA_REQUEST; // allow it to run on dispatch thread even if some API call is waiting (suspended)
		return super.getMethodEventThreadLevel(methodName, arguments);
	}

}
