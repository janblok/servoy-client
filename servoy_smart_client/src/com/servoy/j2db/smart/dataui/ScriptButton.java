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
package com.servoy.j2db.smart.dataui;

import com.servoy.j2db.IApplication;
import com.servoy.j2db.scripting.IScriptable;
import com.servoy.j2db.ui.DummyChangesRecorder;
import com.servoy.j2db.ui.scripting.RuntimeScriptButton;


/**
 * @author jcompager
 */

public class ScriptButton extends AbstractScriptButton
{
	private final IScriptable scriptable;

	public ScriptButton(IApplication app)
	{
		super(app);
		scriptable = new RuntimeScriptButton(this, new DummyChangesRecorder(), app);
	}

	public IScriptable getScriptObject()
	{
		return scriptable;
	}

	@Override
	public String toString()
	{
		return "ScriptButton[" + getName() + ":" + getText() + "]"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}
}
