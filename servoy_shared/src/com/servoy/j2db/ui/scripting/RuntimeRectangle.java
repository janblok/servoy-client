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

package com.servoy.j2db.ui.scripting;

import javax.swing.JComponent;

import com.servoy.j2db.IApplication;
import com.servoy.j2db.ui.IComponent;
import com.servoy.j2db.ui.IRect;
import com.servoy.j2db.ui.IScriptBaseMethods;
import com.servoy.j2db.ui.IScriptRectMethods;
import com.servoy.j2db.ui.IStylePropertyChangesRecorder;

/**
 * @author lvostinar
 * @since 6.0
 */
public class RuntimeRectangle extends AbstractRuntimeBaseComponent implements IScriptRectMethods
{
	public RuntimeRectangle(IComponent component, IStylePropertyChangesRecorder jsChangeRecorder, IApplication application)
	{
		super(component, jsChangeRecorder, application);
	}

	public int js_getAbsoluteFormLocationY()
	{
		return ((IRect)component).getAbsoluteFormLocationY();
	}

	public String js_getElementType()
	{
		return IScriptBaseMethods.RECTANGLE;
	}

	public void js_setToolTipText(String txt)
	{
		component.setToolTipText(txt);
	}

	public String js_getToolTipText()
	{
		return component.getToolTipText();
	}


	@Override
	public void js_setTransparent(boolean b)
	{
		super.js_setTransparent(b);
		if (component instanceof JComponent)
		{
			((JComponent)component).repaint();
		}
	}

}
