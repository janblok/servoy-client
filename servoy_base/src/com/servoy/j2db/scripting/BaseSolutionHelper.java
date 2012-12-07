/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2012 Servoy BV

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

package com.servoy.j2db.scripting;

import com.servoy.j2db.scripting.api.solutionmodel.IBaseSMButton;
import com.servoy.j2db.scripting.api.solutionmodel.IBaseSMComponent;
import com.servoy.j2db.scripting.api.solutionmodel.IBaseSMLabel;

/**
 * Mobile helper is used as a complementary tool to solution model.
 * Components/solution structure in mobile client needs special tags which are not supported by solution model API,
 * but this mobile helper can be used to apply them. For example a button can be the right header button or the left header button and so on.
 * 
 * @author acostescu
 */
public abstract class BaseSolutionHelper
{

	/**
	 * Constant for specifying a predefined icon type for a button.
	 * @sample
	 * plugins.mobile.solutionHelper.setIconType(myJSButton, plugins.mobile.SolutionHelper.ICON_...);
	 */
	public static final String ICON_GEAR = "gear"; // DataIcon.GEAR.getJqmValue() //$NON-NLS-1$
	/**
	 * @sameas com.servoy.j2db.scripting.BaseSolutionHelper#ICON_GEAR
	 */
	public static final String ICON_LEFT = "arrow-l"; // DataIcon.LEFT.getJqmValue() //$NON-NLS-1$
	/**
	 * @sameas com.servoy.j2db.scripting.BaseSolutionHelper#ICON_GEAR
	 */
	public static final String ICON_RIGHT = "arrow-r"; // DataIcon.RIGHT.getJqmValue() //$NON-NLS-1$
	/**
	 * @sameas com.servoy.j2db.scripting.BaseSolutionHelper#ICON_GEAR
	 */
	public static final String ICON_UP = "arrow-u"; // DataIcon.UP.getJqmValue() //$NON-NLS-1$
	/**
	 * @sameas com.servoy.j2db.scripting.BaseSolutionHelper#ICON_GEAR
	 */
	public static final String ICON_DOWN = "arrow-d"; // DataIcon.DOWN.getJqmValue() //$NON-NLS-1$
	/**
	 * @sameas com.servoy.j2db.scripting.BaseSolutionHelper#ICON_GEAR
	 */
	public static final String ICON_DELETE = "delete"; // DataIcon.DELETE.getJqmValue() //$NON-NLS-1$
	/**
	 * @sameas com.servoy.j2db.scripting.BaseSolutionHelper#ICON_GEAR
	 */
	public static final String ICON_PLUS = "plus"; // DataIcon.PLUS.getJqmValue() //$NON-NLS-1$
	/**
	 * @sameas com.servoy.j2db.scripting.BaseSolutionHelper#ICON_GEAR
	 */
	public static final String ICON_MINUS = "minus"; // DataIcon.MINUS.getJqmValue() //$NON-NLS-1$
	/**
	 * @sameas com.servoy.j2db.scripting.BaseSolutionHelper#ICON_GEAR
	 */
	public static final String ICON_CHECK = "check"; // DataIcon.CHECK.getJqmValue() //$NON-NLS-1$
	/**
	 * @sameas com.servoy.j2db.scripting.BaseSolutionHelper#ICON_GEAR
	 */
	public static final String ICON_REFRESH = "refresh"; // DataIcon.REFRESH.getJqmValue() //$NON-NLS-1$
	/**
	 * @sameas com.servoy.j2db.scripting.BaseSolutionHelper#ICON_GEAR
	 */
	public static final String ICON_FORWARD = "forward"; // DataIcon.FORWARD.getJqmValue() //$NON-NLS-1$
	/**
	 * @sameas com.servoy.j2db.scripting.BaseSolutionHelper#ICON_GEAR
	 */
	public static final String ICON_BACK = "back"; // DataIcon.BACK.getJqmValue() //$NON-NLS-1$
	/**
	 * @sameas com.servoy.j2db.scripting.BaseSolutionHelper#ICON_GEAR
	 */
	public static final String ICON_GRID = "grid"; // DataIcon.GRID.getJqmValue() //$NON-NLS-1$
	/**
	 * @sameas com.servoy.j2db.scripting.BaseSolutionHelper#ICON_GEAR
	 */
	public static final String ICON_STAR = "star"; // DataIcon.STAR.getJqmValue() //$NON-NLS-1$
	/**
	 * @sameas com.servoy.j2db.scripting.BaseSolutionHelper#ICON_GEAR
	 */
	public static final String ICON_ALERT = "alert"; // DataIcon.ALERT.getJqmValue() //$NON-NLS-1$
	/**
	 * @sameas com.servoy.j2db.scripting.BaseSolutionHelper#ICON_GEAR
	 */
	public static final String ICON_INFO = "info"; // DataIcon.INFO.getJqmValue() //$NON-NLS-1$
	/**
	 * @sameas com.servoy.j2db.scripting.BaseSolutionHelper#ICON_GEAR
	 */
	public static final String ICON_HOME = "home"; // DataIcon.HOME.getJqmValue() //$NON-NLS-1$
	/**
	 * @sameas com.servoy.j2db.scripting.BaseSolutionHelper#ICON_GEAR
	 */
	public static final String ICON_SEARCH = "search"; // DataIcon.SEARCH.getJqmValue() //$NON-NLS-1$

	public abstract void markLeftHeaderButton(IBaseSMButton button);

	public abstract void markRightHeaderButton(IBaseSMButton button);

	public abstract void markHeaderText(IBaseSMLabel label);

	public abstract void markFooterItem(IBaseSMComponent component);

	public abstract void setIconType(IBaseSMButton button, String iconType);

	public void groupComponents(IBaseSMComponent c1, IBaseSMComponent c2)
	{
		String gid = c1.getGroupID();
		if (gid == null) gid = c2.getGroupID();
		if (gid == null) gid = createNewGroupId();

		c1.setGroupID(gid);
		c2.setGroupID(gid);
	}

	protected abstract String createNewGroupId();

}