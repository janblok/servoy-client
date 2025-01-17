/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2015 Servoy BV

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

package com.servoy.j2db.scripting.solutionmodel;

import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.annotations.JSFunction;
import org.mozilla.javascript.annotations.JSGetter;
import org.mozilla.javascript.annotations.JSSetter;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.WebComponentSpecProvider;
import org.sablo.specification.WebComponentSpecification;
import org.sablo.specification.property.types.FunctionPropertyType;

import com.servoy.base.scripting.annotations.ServoyClientSupport;
import com.servoy.j2db.IApplication;
import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.persistence.WebComponent;
import com.servoy.j2db.scripting.IJavaScriptType;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.IRhinoDesignConverter;
import com.servoy.j2db.util.ServoyJSONObject;
import com.servoy.j2db.util.Utils;

/**
 * @author lvostinar
 *
 */
@ServoyDocumented(category = ServoyDocumented.RUNTIME, extendsComponent = "JSComponent")
@ServoyClientSupport(mc = false, wc = false, sc = false, ng = true)
public class JSWebComponent extends JSComponent<WebComponent> implements IJavaScriptType
{

	private final IApplication application;

	protected JSWebComponent(IJSParent< ? > parent, WebComponent baseComponent, IApplication application, boolean isNew)
	{
		super(parent, baseComponent, isNew);
		this.application = application;
	}

	/**
	 * @clonedesc com.servoy.j2db.scripting.solutionmodel.JSComponent#getBackground()
	 * @sampleas com.servoy.j2db.scripting.solutionmodel.JSComponent#getBackground()
	 *
	 * @deprecated not supported
	 */
	@Override
	@Deprecated
	@JSGetter
	public String getBackground()
	{
		return super.getBackground();
	}

	/**
	 * @clonedesc com.servoy.j2db.scripting.solutionmodel.JSComponent#getBorderType()
	 * @sampleas com.servoy.j2db.scripting.solutionmodel.JSComponent#getBorderType()
	 *
	 * @deprecated not supported
	 */
	@Override
	@Deprecated
	@JSGetter
	public String getBorderType()
	{
		return super.getBorderType();
	}

	/**
	 * @clonedesc com.servoy.j2db.scripting.solutionmodel.JSComponent#getFontType()
	 * @sampleas com.servoy.j2db.scripting.solutionmodel.JSComponent#getFontType()
	 *
	 * @deprecated not supported
	 */
	@Override
	@Deprecated
	@JSGetter
	public String getFontType()
	{
		return super.getFontType();
	}

	/**
	 * @clonedesc com.servoy.j2db.scripting.solutionmodel.JSComponent#getForeground()
	 * @sampleas com.servoy.j2db.scripting.solutionmodel.JSComponent#getForeground()
	 *
	 * @deprecated not supported
	 */
	@Override
	@Deprecated
	@JSGetter
	public String getForeground()
	{
		return super.getForeground();
	}

	/**
	 * @clonedesc com.servoy.j2db.scripting.solutionmodel.JSComponent#getGroupID()
	 * @sampleas com.servoy.j2db.scripting.solutionmodel.JSComponent#getGroupID()
	 *
	 * @deprecated not supported
	 */
	@Deprecated
	@JSGetter
	@Override
	public String getGroupID()
	{
		return super.getGroupID();
	}

	/**
	 * @clonedesc com.servoy.j2db.scripting.solutionmodel.JSComponent#getPrintSliding()
	 * @sampleas com.servoy.j2db.scripting.solutionmodel.JSComponent#getPrintSliding()
	 *
	 * @deprecated not supported
	 */
	@Override
	@Deprecated
	@JSGetter
	public int getPrintSliding()
	{
		return super.getPrintSliding();
	}

	/**
	 * @clonedesc com.servoy.j2db.scripting.solutionmodel.JSComponent#getPrintable()
	 * @sampleas com.servoy.j2db.scripting.solutionmodel.JSComponent#getPrintable()
	 *
	 * @deprecated not supported
	 */
	@Override
	@Deprecated
	@JSGetter
	public boolean getPrintable()
	{
		return super.getPrintable();
	}

	/**
	 * @clonedesc com.servoy.j2db.scripting.solutionmodel.JSComponent#getStyleClass()
	 * @sampleas com.servoy.j2db.scripting.solutionmodel.JSComponent#getStyleClass()
	 *
	 * @deprecated not supported
	 */
	@Override
	@Deprecated
	@JSGetter
	public String getStyleClass()
	{
		return super.getStyleClass();
	}

	/**
	 * @clonedesc com.servoy.j2db.scripting.solutionmodel.JSComponent#getTransparent()
	 * @sampleas com.servoy.j2db.scripting.solutionmodel.JSComponent#getTransparent()
	 *
	 * @deprecated not supported
	 */
	@Override
	@Deprecated
	@JSGetter
	public boolean getTransparent()
	{
		return super.getTransparent();
	}

	@SuppressWarnings("nls")
	@Override
	public String toString()
	{
		return "JSWebComponent[name:" + getName() + ",type name:" + getTypeName() + ']';
	}

	/**
	 * The webcomponent type (name from the spec).
	 *
	 * @sample
	 * var wc = form.getWebComponent('mycomponent');
	 * application.output(bean.typeName);
	 */
	@JSGetter
	public String getTypeName()
	{
		return getBaseComponent(false).getTypeName();
	}

	@JSSetter
	public void setTypeName(String typeName)
	{
		getBaseComponent(true).setTypeName(typeName);
	}

	/**
	 * Set a property value of the spec.
	 *
	 * @sample
	 * var wc = form.getWebComponent('mycomponent');
	 * wc.setJSONProperty('mytext','Hello World!');
	 */
	@JSFunction
	public void setJSONProperty(String propertyName, Object value)
	{
		try
		{
			WebComponent webComponent = getBaseComponent(true);
			if (value instanceof JSMethod)
			{
				// should we move this into a IRhinoDesignConverter impl?
				value = new Integer(JSBaseContainer.getMethodId(application, webComponent, ((JSMethod)value).getScriptMethod()));
			}
			else if (value instanceof JSValueList)
			{
				// should we move this into a IRhinoDesignConverter impl?
				value = new Integer(((JSValueList)value).getValueList().getID());
			}
			else
			{
				WebComponentSpecification spec = WebComponentSpecProvider.getInstance().getWebComponentSpecification(webComponent.getTypeName());
				PropertyDescription pd = spec.getProperty(propertyName);
				if (pd != null && pd.getType() instanceof IRhinoDesignConverter)
				{
					value = ((IRhinoDesignConverter)pd.getType()).toDesignValue(value, pd);
				}
				else
				{
					// default - stringify what we get from rhino and convert that to org.json usable value
					Scriptable topLevelScope = ScriptableObject.getTopLevelScope(application.getScriptEngine().getSolutionScope());

					Context cx = Context.enter();
					try
					{
						String stringified = (String)ScriptableObject.callMethod(cx, (Scriptable)topLevelScope.get("JSON", topLevelScope), "stringify",
							new Object[] { value });
						value = new JSONObject("{ \"a\" : " + stringified + " }").get("a");
					}
					finally
					{
						Context.exit();
					}
				}
			}
			JSONObject jsonObject = webComponent.getJson() == null ? new ServoyJSONObject(true, true) : webComponent.getJson();
			jsonObject.put(propertyName, value);
			webComponent.setJson(jsonObject);
		}
		catch (JSONException e)
		{
			Debug.error(e);
		}
	}

	/**
	 * Get a property value of the spec.
	 *
	 * @sample
	 * var wc = form.getWebComponent('mycomponent');
	 * application.output(wc.getJSONProperty('mytext'));
	 */
	@JSFunction
	public Object getJSONProperty(String propertyName)
	{
		WebComponent webComponent = getBaseComponent(false);
		JSONObject json = webComponent.getFlattenedJson();
		Object value = json.opt(propertyName);
		WebComponentSpecification spec = WebComponentSpecProvider.getInstance().getWebComponentSpecification(webComponent.getTypeName());
		if (spec != null)
		{
			PropertyDescription pd = spec.getProperty(propertyName);
			if (pd != null && pd.getType() instanceof FunctionPropertyType)
			{
				value = JSForm.getEventHandler(application, webComponent, Utils.getAsInteger(value), getJSParent(), propertyName);
			}
		}
		return value;
	}
}
