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
package com.servoy.j2db.scripting.info;

import com.servoy.j2db.IApplication;
import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.scripting.IPrefixedConstantsObject;

@ServoyDocumented(category = ServoyDocumented.RUNTIME)
public class UICONSTANTS implements IPrefixedConstantsObject
{

	/**
	 * Property that can be set using application.putClientProperty() or element.putClientProperty().
	 * 
	 * If set on application it will affect all TYPE-AHEAD fields. If set on an element it will affect only that TYPE-AHEAD element/field (with priority over the application property).
	 * Value can be true/false/null.
	 * 
	 * If set to true, the affected TYPE_AHEAD(s) will show the pop-up when the field content is empty.
	 * DEFAULT: true.
	 *
	 * @sample
	 * // make all TYPE-AHEAD fields not show the pop-up when there is empty content in the field
	 * application.putClientProperty(APP_UI_PROPERTY.TYPE_AHEAD_SHOW_POPUP_WHEN_EMPTY, false);
	 * // make one TYPE-AHEAD field show the pop-up when there is empty content in the field - overrides the application property set
	 * forms.someForm.elements.typeAheadElement.putClientProperty(APP_UI_PROPERTY.TYPE_AHEAD_SHOW_POPUP_WHEN_EMPTY, true);
	 */
	public static final String TYPE_AHEAD_SHOW_POPUP_WHEN_EMPTY = IApplication.TYPE_AHEAD_SHOW_POPUP_WHEN_EMPTY;

	/**
	 * Property that can be set using application.putClientProperty() or element.putClientProperty().
	 * 
	 * If set on application it will affect all TYPE-AHEAD fields. If set on an element it will affect only that TYPE-AHEAD element/field (with priority over the application property).
	 * Value can be true/false/null.
	 * 
	 * If set to true, the affected TYPE_AHEAD(s) will show the pop-up when gaining focus.
	 * DEFAULT: true.
	 *
	 * @sample
	 * // make all TYPE-AHEAD fields not show the pop-up when gaining focus
	 * application.putClientProperty(APP_UI_PROPERTY.TYPE_AHEAD_SHOW_POPUP_ON_FOCUS_GAIN, false);
	 * // make one TYPE-AHEAD field show the pop-up when gaining focus - overrides the application property set
	 * forms.someForm.elements.typeAheadElement.putClientProperty(APP_UI_PROPERTY.TYPE_AHEAD_SHOW_POPUP_ON_FOCUS_GAIN, true);
	 */
	public static final String TYPE_AHEAD_SHOW_POPUP_ON_FOCUS_GAIN = IApplication.TYPE_AHEAD_SHOW_POPUP_ON_FOCUS_GAIN;

	/**
	 * Property that can be set using application.putClientProperty() or element.putClientProperty().
	 * 
	 * If set on application it will affect all COMBOBOX fields. If set on an element it will affect only that COMBOBOX element/field (with priority over the application property).
	 * Value can be true/false/null.
	 * 
	 * If set to true, the affected COMBOBOX will show the pop-up when gaining focus.
	 * DEFAULT: true.
	 *
	 * @sample
	 * // make all COMBOBOX fields not show the pop-up when gaining focus
	 * application.putClientProperty(APP_UI_PROPERTY.COMBOBOX_SHOW_POPUP_ON_FOCUS_GAIN, false);
	 * // make one COMBOBOX field show the pop-up when gaining focus - overrides the application property set
	 * forms.someForm.elements.comboboxElement.putClientProperty(APP_UI_PROPERTY.COMBOBOX_SHOW_POPUP_ON_FOCUS_GAIN, true);
	 */
	public static final String COMBOBOX_SHOW_POPUP_ON_FOCUS_GAIN = IApplication.COMBOBOX_SHOW_POPUP_ON_FOCUS_GAIN;

	/**
	 * Property that can be set using application.putClientProperty() or element.putClientProperty(). It is used only in Smart Client.
	 * 
	 * If set on application it will affect all date formatted fields. If set on an element it will affect only that date formatted element/field (with priority over the application property).
	 * Value can be true/false/null.
	 * 
	 * If set to true, only selected part of the date will be affected when using up/down keys to cycle through values. (for example, pressing up when cursor is on minutes and minutes shows 59 will not result in hour change)
	 * DEFAULT: false.
	 *
	 * @sample
	 * // make all date formatted fields use roll instead of add
	 * application.putClientProperty(APP_UI_PROPERTY.DATE_FORMATTERS_ROLL_INSTEAD_OF_ADD, true);
	 * // make one date formatted field use add instead of roll - overrides the application property set
	 * forms.someForm.elements.typeAheadElement.putClientProperty(APP_UI_PROPERTY.DATE_FORMATTERS_ROLL_INSTEAD_OF_ADD, false);
	 */
	public static final String DATE_FORMATTERS_ROLL_INSTEAD_OF_ADD = IApplication.DATE_FORMATTERS_ROLL_INSTEAD_OF_ADD;

	/**
	 * Property that can be set using application.putClientProperty() or element.putClientProperty().
	 * 
	 * If set on application it will affect all date formatted fields. CAUTION: this property must be set on application before the fields are created (for example in solution onOpen handler). Changing it after fields were created will not affect these existing fields.
	 * If set on an element it will affect only that date formatted element/field (with priority over the application property).
	 * Value can be true/false/null.
	 * 
	 * If set to false, date formatted fields will not allow input of out-of-bounds values (like 62 minutes means 2 minutes and +1 hour).
	 * DEFAULT: true.
	 *
	 * @sample
	 * // make all date formatted fields (created after this line is executed) not use lenient mode
	 * application.putClientProperty(APP_UI_PROPERTY.DATE_FORMATTERS_LENIENT, false);
	 * // make one date formatted field use lenient mode - overrides the application property set
	 * forms.someForm.elements.typeAheadElement.putClientProperty(APP_UI_PROPERTY.DATE_FORMATTERS_LENIENT, true);
	 */
	public static final String DATE_FORMATTERS_LENIENT = IApplication.DATE_FORMATTERS_LENIENT;

	/**
	 * Value that can be used to specify that a dialog should completely fill the screen.
	 * 
	 * @deprecated because of a rewrite of dialogs/windows (based in JSWindow objects)
	 * @sample
	 * application.showFormInDialog('customers',APP_UI_PROPERTY.DIALOG_FULL_SCREEN,APP_UI_PROPERTY.DIALOG_FULL_SCREEN,
	 *      APP_UI_PROPERTY.DIALOG_FULL_SCREEN,APP_UI_PROPERTY.DIALOG_FULL_SCREEN,'My Title',true,true,'customers_dialog',true)
	 */
	@Deprecated
	public static final int DIALOG_FULL_SCREEN = IApplication.FULL_SCREEN;

	/**
	 * Value that can be used to specify that a dialog/window should completely fill the screen.
	 * 
	 * @deprecated because of a rewrite of dialogs/windows (based in JSWindow objects)
	 * @sample
	 * application.showFormInDialog('customers',APP_UI_PROPERTY.FULL_SCREEN,APP_UI_PROPERTY.FULL_SCREEN,
	 *      APP_UI_PROPERTY.FULL_SCREEN,APP_UI_PROPERTY.FULL_SCREEN,'My Title',true,true,'customers_dialog',true)
	 */
	@Deprecated
	public static final int FULL_SCREEN = IApplication.FULL_SCREEN;

	/**
	 * Property than can be set using application.putClientProperty().
	 * 
	 * If set to true, the system standard Printing dialog will be used when printing is needed.
	 * If set to false, the Servoy Printing dialog will be used.
	 * 
	 * The value can be true/false/null.
	 * DEFAULT: false 
	 * 
	 * @sample
	 * application.putClientProperty(APP_UI_PROPERTY.USE_SYSTEM_PRINT_DIALOG, true)
	 */
	public static final String USE_SYSTEM_PRINT_DIALOG = IApplication.USE_SYSTEM_PRINT_DIALOG;

	/**
	 * Property than can be set using application.putClientProperty() and
	 * indicates the delay in milliseconds before the tooltip is shown.
	 *
	 * @sample
	 * application.putClientProperty(APP_UI_PROPERTY.TOOLTIP_INITIAL_DELAY, 2000)
	 */
	public static final String TOOLTIP_INITIAL_DELAY = IApplication.TOOLTIP_INITIAL_DELAY;

	/**
	 * Property than can be set using application.putClientProperty() and
	 * indicates the delay in milliseconds after the tooltip is dismissed.
	 *
	 * @sample
	 * application.putClientProperty(APP_UI_PROPERTY.TOOLTIP_DISMISS_DELAY, 4000)
	 */
	public static final String TOOLTIP_DISMISS_DELAY = IApplication.TOOLTIP_DISMISS_DELAY;

	/**
	 * Property than can be set using application.putClientProperty().
	 * 
	 * If set to true, fields that are read-only won't be editable in find mode
	 * If set to false, fields that are read-only will be editable in find mode
	 * 
	 * The value can be true/false
	 * DEFAULT: false 
	 * 
	 * @sample
	 * application.putClientProperty(APP_UI_PROPERTY.LEAVE_FIELDS_READONLY_IN_FIND_MODE, true)
	 */
	public static final String LEAVE_FIELDS_READONLY_IN_FIND_MODE = IApplication.LEAVE_FIELDS_READONLY_IN_FIND_MODE;

	/**
	 * Property than can be set using application.putClientProperty().
	 * 
	 * If set to true, table views in web client are scrollable by default
	 * If set to false, table views in web client are not scrollable, but pageable by default
	 * 
	 * The value can be true/false
	 * DEFAULT: false 
	 * 
	 * @sample
	 * application.putClientProperty(APP_UI_PROPERTY.TABLEVIEW_WC_DEFAULT_SCROLLABLE, true)
	 */
	public static final String TABLEVIEW_WC_DEFAULT_SCROLLABLE = IApplication.TABLEVIEW_WC_DEFAULT_SCROLLABLE;

	/**
	 * Property than can be set using application.putClientProperty().
	 * 
	 * If set to true, scrollable table views in web client will keep the already loaded rows in the view
	 * If set to false, scrollable table views in web client will unload not visible rows in the view
	 * 
	 * The value can be true/false
	 * DEFAULT: false 
	 * 
	 * @sample
	 * application.putClientProperty(APP_UI_PROPERTY.TABLEVIEW_WC_SCROLLABLE_KEEP_LOADED_ROWS, true)
	 */
	public static final String TABLEVIEW_WC_SCROLLABLE_KEEP_LOADED_ROWS = IApplication.TABLEVIEW_WC_SCROLLABLE_KEEP_LOADED_ROWS;
	/**
	 * Property than can be set using element.putClientProperty()
	 * 
	 * If set to true, the element will accept javascript links in the input
	 * If set to false, all 'javascript:' texts will be removed from the input
	 * 
	 * The value can be true/false
	 * DEFAULT: false 
	 * 
	 * @sample
	 * element.putClientProperty(APP_UI_PROPERTY.ALLOW_JAVASCRIPT_LINKS_INPUT, true)
	 */
	public static final String ALLOW_JAVASCRIPT_LINKS_INPUT = IApplication.ALLOW_JAVASCRIPT_LINKS_INPUT;


	public String getPrefix()
	{
		return "APP_UI_PROPERTY"; //$NON-NLS-1$
	}

	@Override
	public String toString()
	{
		return "Properties that alter L&F of UI components";
	}

}
