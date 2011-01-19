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

import java.awt.Insets;
import java.awt.Point;
import java.util.ArrayList;

import org.apache.wicket.Component;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.Request;
import org.apache.wicket.RequestCycle;
import org.apache.wicket.Session;
import org.apache.wicket.Component.IVisitor;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.markup.html.IHeaderResponse;
import org.mozilla.javascript.ScriptRuntime;

import com.servoy.j2db.DesignModeCallbacks;
import com.servoy.j2db.FormController;
import com.servoy.j2db.dnd.DRAGNDROP;
import com.servoy.j2db.scripting.JSEvent;
import com.servoy.j2db.scripting.JSEvent.EventType;
import com.servoy.j2db.scripting.info.CLIENTDESIGN;
import com.servoy.j2db.server.headlessclient.dataui.AbstractServoyDefaultAjaxBehavior;
import com.servoy.j2db.server.headlessclient.dataui.WebDataCalendar;
import com.servoy.j2db.server.headlessclient.dataui.WebDataRenderer;
import com.servoy.j2db.server.headlessclient.dataui.WebEventExecutor;
import com.servoy.j2db.server.headlessclient.dataui.drag.DraggableBehavior;
import com.servoy.j2db.server.headlessclient.yui.YUILoader;
import com.servoy.j2db.ui.IComponent;
import com.servoy.j2db.ui.IProviderStylePropertyChanges;
import com.servoy.j2db.ui.IScriptBaseMethods;
import com.servoy.j2db.ui.IScriptInputMethods;
import com.servoy.j2db.ui.ISupportWebBounds;
import com.servoy.j2db.ui.ITabPanel;
import com.servoy.j2db.util.Utils;

/**
 * @author jcompagner
 * @author jblok
 */
@SuppressWarnings("nls")
public class DesignModeBehavior extends AbstractServoyDefaultAjaxBehavior
{
	public static final String ACTION_RESIZE = "aResize";
	public static final String PARAM_RESIZE_HEIGHT = "resizeHeight";
	public static final String PARAM_RESIZE_WIDTH = "resizeWidth";

	private DesignModeCallbacks callback;
	private FormController controller;

	private IComponent onDragComponent = null;
	private IComponent onSelectComponent = null;

	/**
	 * @see org.apache.wicket.ajax.AbstractDefaultAjaxBehavior#renderHead(org.apache.wicket.markup.html.IHeaderResponse)
	 */
	@Override
	public void renderHead(IHeaderResponse response)
	{
		super.renderHead(response);

		YUILoader.renderResize(response);

		final ArrayList<Component> markupIds = new ArrayList<Component>();
		final ArrayList<Component> dropMarkupIds = new ArrayList<Component>();
		((MarkupContainer)getComponent()).visitChildren(IComponent.class, new IVisitor<Component>()
		{
			public Object component(Component component)
			{
				if (!(component instanceof WebDataRenderer))
				{
					markupIds.add(component);
					if (component instanceof ITabPanel || component instanceof WebDataCalendar)
					{
						return IVisitor.CONTINUE_TRAVERSAL_BUT_DONT_GO_DEEPER;
					}
				}
				else if (component instanceof WebDataRenderer)
				{
					dropMarkupIds.add(component);
				}
				return IVisitor.CONTINUE_TRAVERSAL;
			}
		});

		if (markupIds.size() > 0)
		{
			boolean webAnchorsEnabled = Utils.getAsBoolean(((WebClientSession)Session.get()).getWebClient().getRuntimeProperties().get("enableAnchors"));

			//WebClientSession webClientSession = (WebClientSession)getSession();
			//WebClient webClient = webClientSession.getWebClient();

			StringBuilder sb = new StringBuilder(markupIds.size() * 10);
			sb.append("Servoy.ClientDesign.attach({");
			for (int i = 0; i < markupIds.size(); i++)
			{
				Component component = markupIds.get(i);

				Object clientdesign_handles = null;
				if (component instanceof IScriptBaseMethods)
				{
					IScriptBaseMethods sbmc = (IScriptBaseMethods)component;
					if (sbmc.js_getName() == null) continue; //skip, elements with no name are not usable in CD

					clientdesign_handles = sbmc.js_getClientProperty(CLIENTDESIGN.HANDLES);
					Object clientdesign_selectable = sbmc.js_getClientProperty(CLIENTDESIGN.SELECTABLE);
					if (clientdesign_selectable != null && !Utils.getAsBoolean(clientdesign_selectable)) continue; //skip
				}

				String padding = "0px 0px 0px 0px"; //$NON-NLS-1$
				if (component instanceof ISupportWebBounds)
				{
					Insets p = ((ISupportWebBounds)component).getPaddingAndBorder();
					if (p != null) padding = "0px " + (p.left + p.right) + "px " + (p.bottom + p.top) + "px 0px";
				}
				boolean editable = false;
				if (component instanceof IScriptInputMethods)
				{
					editable = ((IScriptInputMethods)component).js_isEditable();
				}
				if (webAnchorsEnabled && component instanceof IScriptBaseMethods &&
					needsWrapperDivForAnchoring(((IScriptBaseMethods)component).js_getElementType(), editable))
				{
					sb.append(component.getMarkupId() + "_wrapper");
				}
				else
				{
					sb.append(component.getMarkupId());
				}
				sb.append(":['");
				sb.append(padding);
				sb.append("'");
				if (clientdesign_handles instanceof Object[])
				{
					sb.append(",[");
					Object[] array = (Object[])clientdesign_handles;
					for (Object element : array)
					{
						sb.append('\'');
						sb.append(ScriptRuntime.escapeString(element.toString()));
						sb.append("',");
					}
					sb.setLength(sb.length() - 1); //rollback last comma
					sb.append("]");
				}
				sb.append("],");
			}
			sb.setLength(sb.length() - 1); //rollback last comma
			sb.append("},'" + getCallbackUrl() + "')");
			response.renderOnDomReadyJavascript(sb.toString());

//			if (dropMarkupIds.size() > 0)
//			{
//				StringBuilder attachDrop = new StringBuilder();
//				attachDrop.append("attachDrop([");
//				for (int i = 0; i < dropMarkupIds.size(); i++)
//				{
//					Component component = dropMarkupIds.get(i);
//					attachDrop.append("'");
//					attachDrop.append(component.getMarkupId());
//					attachDrop.append("',");
//				}
//				attachDrop.setLength(attachDrop.length() - 1);
//				attachDrop.append("])");
//				response.renderOnDomReadyJavascript(attachDrop.toString());
//			}
		}
	}

	private boolean needsWrapperDivForAnchoring(String type, boolean editable)
	{
		// this needs to be in sync with TemplateGenerator.needsWrapperDivForAnchoring(Field field)
		// and TemplateGenerator.isButton(GraphicalComponent label)
		return "PASSWORD".equals(type) || "TEXT_AREA".equals(type) || "COMBOBOX".equals(type) || "TYPE_AHEAD".equals(type) || "TEXT_FIELD".equals(type) ||
			(IScriptBaseMethods.HTML_AREA.equals(type) && editable) || IScriptBaseMethods.BUTTON.equals(type);
	}

	/**
	 * @see org.apache.wicket.ajax.AbstractDefaultAjaxBehavior#respond(org.apache.wicket.ajax.AjaxRequestTarget)
	 */
	@Override
	protected void respond(AjaxRequestTarget target)
	{
		Request request = RequestCycle.get().getRequest();
		String action = request.getParameter(DraggableBehavior.PARAM_ACTION);
		String id = extractId(request.getParameter(DraggableBehavior.PARAM_DRAGGABLE_ID));
		if (id != null)
		{
			if (id.endsWith("_wrapper"))
			{
				id = id.substring(0, id.length() - 8);
			}
			final String finalId = id;
			MarkupContainer comp = (MarkupContainer)getComponent();
			IScriptBaseMethods child = (IScriptBaseMethods)comp.visitChildren(IScriptBaseMethods.class, new IVisitor<Component>()
			{
				public Object component(Component component)
				{
					String markupId = component.getMarkupId();
					if (finalId.equals(markupId)) return component;
					return IVisitor.CONTINUE_TRAVERSAL;
				}
			});
			if (child != null && action != null)
			{
				int height = stripUnitPart(request.getParameter(PARAM_RESIZE_HEIGHT));
				int width = stripUnitPart(request.getParameter(PARAM_RESIZE_WIDTH));
				int x = stripUnitPart(request.getParameter(DraggableBehavior.PARAM_X));
				int y = stripUnitPart(request.getParameter(DraggableBehavior.PARAM_Y));
				if (child != onSelectComponent)
				{
					onSelectComponent = (IComponent)child;
					Object ret = callback.executeOnSelect(getJSEvent(EventType.rightClick, 0, new Point(x, y), new IComponent[] { (IComponent)child }));
					if (ret instanceof Boolean && !((Boolean)ret).booleanValue())
					{
						onSelectComponent = null;
						return;
					}

				}
				if (action.equals(ACTION_RESIZE))
				{
					if (width != -1 && height != -1)
					{
						if (child instanceof ISupportWebBounds)
						{
							Insets paddingAndBorder = ((ISupportWebBounds)child).getPaddingAndBorder();
							if (paddingAndBorder != null)
							{
								height += paddingAndBorder.bottom + paddingAndBorder.top;
								width += paddingAndBorder.left + paddingAndBorder.right;
							}
						}
						child.js_setSize(width, height);
						if (child instanceof IProviderStylePropertyChanges) ((IProviderStylePropertyChanges)child).getStylePropertyChanges().setRendered();
					}
					callback.executeOnResize(getJSEvent(EventType.onDrop, 0, new Point(x, y), new IComponent[] { (IComponent)child }));
				}
				else if (action.equals(DraggableBehavior.ACTION_DRAG_START))
				{
					Object onDragAllowed = callback.executeOnDrag(getJSEvent(EventType.onDrag, 0, new Point(x, y), new IComponent[] { (IComponent)child }));
					if ((onDragAllowed instanceof Boolean && !((Boolean)onDragAllowed).booleanValue()) ||
						(onDragAllowed instanceof Number && ((Number)onDragAllowed).intValue() == DRAGNDROP.NONE))
					{
						onDragComponent = null;
					}
					else
					{
						onDragComponent = (IComponent)child;
					}
				}
				else
				{
					if (child == onDragComponent)
					{
						if (x != -1 && y != -1)
						{
							child.js_setLocation(x, y);
							if (child instanceof IProviderStylePropertyChanges)
							{
								// test if it is wrapped
								if (((Component)child).getParent() instanceof WrapperContainer)
								{
									// call for the changes on the wrapper container so that it will copy the right values over
									WrapperContainer wrapper = (WrapperContainer)((Component)child).getParent();
									wrapper.getStylePropertyChanges().getChanges();
									wrapper.getStylePropertyChanges().setRendered();

								}
								((IProviderStylePropertyChanges)child).getStylePropertyChanges().setRendered();
							}
						}
						callback.executeOnDrop(getJSEvent(EventType.onDrop, 0, new Point(x, y), new IComponent[] { (IComponent)child }));
					}
				}
			}
		}
		WebEventExecutor.generateResponse(target, getComponent().getPage());
		target.prependJavascript("Servoy.ClientDesign.reattach();");
	}

	/**
	 * @param parameter
	 * @return
	 */
	private String extractId(String id)
	{
		if (id != null && id.endsWith("_wrap"))
		{
			return id.substring(0, id.length() - 5);
		}
		return id;
	}

	private int stripUnitPart(String str)
	{
		if (str == null || str.trim().equals("") || "auto".equals(str)) return -1;
		if (str.endsWith("px"))
		{
			return Integer.parseInt(str.substring(0, str.length() - 2));
		}
		return Integer.parseInt(str);
	}

	/**
	 * @see org.apache.wicket.behavior.AbstractBehavior#isEnabled(org.apache.wicket.Component)
	 */
	@Override
	public boolean isEnabled(Component component)
	{
		return super.isEnabled(component) && callback != null;
	}

	public void setDesignModeCallback(DesignModeCallbacks callback, FormController controller)
	{
		this.callback = callback;
		this.controller = controller;
	}

	private JSEvent getJSEvent(EventType type, int modifiers, Point point, IComponent[] selected)
	{
		JSEvent event = new JSEvent();
		event.setFormName(controller.getName());
		event.setType(type);
		event.setModifiers(modifiers);
		event.setLocation(point);
		event.setData(selected);
		//event.setSource(e)
		return event;
	}
}
