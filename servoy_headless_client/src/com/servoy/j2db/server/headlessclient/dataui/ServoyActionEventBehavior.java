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
package com.servoy.j2db.server.headlessclient.dataui;

import org.apache.wicket.Component;
import org.apache.wicket.RequestCycle;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.IAjaxCallDecorator;
import org.apache.wicket.ajax.calldecorator.AjaxCallDecorator;

import com.servoy.j2db.scripting.JSEvent.EventType;
import com.servoy.j2db.ui.IEventExecutor;
import com.servoy.j2db.ui.IScriptReadOnlyMethods;
import com.servoy.j2db.util.Utils;

/**
 * The ajax behavior for handling the onAction or enter in a TextField.
 * 
 * @author jcompagner
 * 
 */
public class ServoyActionEventBehavior extends ServoyAjaxFormComponentUpdatingBehavior
{
	private static final long serialVersionUID = 1L;

	protected final Component component;
	protected final WebEventExecutor eventExecutor;

	/**
	 * @param event
	 * @param eventExecutor
	 */
	public ServoyActionEventBehavior(String event, Component component, WebEventExecutor eventExecutor)
	{
		super(event);
		this.component = component;
		this.eventExecutor = eventExecutor;
	}

	@Override
	public CharSequence getCallbackUrl(boolean onlyTargetActivePage)
	{
		return super.getCallbackUrl(true);
	}

	/**
	 * @see wicket.ajax.form.AjaxFormComponentUpdatingBehavior#onUpdate(wicket.ajax.AjaxRequestTarget)
	 */
	@Override
	protected void onUpdate(AjaxRequestTarget target)
	{
		eventExecutor.onEvent(EventType.action, target, getComponent(),
			Utils.getAsInteger(RequestCycle.get().getRequest().getParameter(IEventExecutor.MODIFIERS_PARAMETER)));
	}

	/**
	 * @see wicket.ajax.form.AjaxFormComponentUpdatingBehavior#onError(wicket.ajax.AjaxRequestTarget, java.lang.RuntimeException)
	 */
	@Override
	protected void onError(AjaxRequestTarget target, RuntimeException e)
	{
		super.onError(target, e);
		eventExecutor.onError(target, component);
	}

	@Override
	protected CharSequence generateCallbackScript(final CharSequence partialCall)
	{
		return super.generateCallbackScript(partialCall + "+'modifiers='+getModifiers(event)"); //$NON-NLS-1$
	}

	/**
	 * @see com.servoy.j2db.server.headlessclient.dataui.ServoyFormComponentUpdatingBehavior#isEnabled(Component)
	 */
	@Override
	public boolean isEnabled(Component component)
	{
		if (super.isEnabled(component))
		{
			if (component instanceof IScriptReadOnlyMethods)
			{
				return !((IScriptReadOnlyMethods)component).js_isReadOnly() && ((IScriptReadOnlyMethods)component).js_isEnabled();
			}
			return true;
		}
		return false;
	}

	/**
	 * @see org.apache.wicket.ajax.AbstractDefaultAjaxBehavior#getAjaxCallDecorator()
	 */
	@Override
	protected IAjaxCallDecorator getAjaxCallDecorator()
	{
		return new AjaxCallDecorator()
		{
			private static final long serialVersionUID = 1L;

			@Override
			public CharSequence decorateScript(CharSequence script)
			{
				if (component instanceof WebDataTextArea)
				{
					return "testEnterKey(event, function() {" + script + "});";
				}
				return "return testEnterKey(event, function() {" + script + "});";
			}
		};
	}

	/**
	 * @see com.servoy.j2db.server.headlessclient.dataui.ServoyFormComponentUpdatingBehavior#findIndicatorId()
	 */
	@Override
	protected String findIndicatorId()
	{
		return "indicator";
	}
}
