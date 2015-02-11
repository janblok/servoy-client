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

package com.servoy.j2db.server.ngclient;

import org.json.JSONArray;
import org.json.JSONObject;
import org.sablo.WebComponent;

import com.servoy.base.util.ITagResolver;
import com.servoy.j2db.dataprocessing.IRecord;
import com.servoy.j2db.dataprocessing.IRecordInternal;

/**
 * @author jcompagner
 */
public interface IDataAdapterList extends ITagResolver
{

	void pushChanges(WebFormComponent webComponent, String string);

	void pushChanges(WebFormComponent webComponent, String string, Object newValue);

	Object executeEvent(WebComponent webComponent, String event, int eventId, Object[] args);

	/**
	 * @param args args to replace in script
	 * @param appendingArgs args to append in script execution
	 */
	Object executeInlineScript(String script, JSONObject args, JSONArray appendingArgs);

	void setRecord(IRecord record, boolean fireChangeEvent);

	void startEdit(WebFormComponent webComponent, String property);

	void setFindMode(boolean findMode);

	INGApplication getApplication();

	IWebFormController getForm();

	void addRelatedForm(IWebFormController form, String relation);

	void removeRelatedForm(IWebFormController form);

	void removeAllRelatedForms();

	IRecordInternal getRecord();
}
