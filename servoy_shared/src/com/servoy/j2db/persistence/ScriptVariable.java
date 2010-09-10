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
package com.servoy.j2db.persistence;

import java.util.ArrayList;
import java.util.Date;
import java.util.StringTokenizer;

import org.mozilla.javascript.NativeDate;
import org.mozilla.javascript.Wrapper;

import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.UUID;
import com.servoy.j2db.util.Utils;


/**
 * A so called script variable used as global under solution and as form variable used under form objects
 * 
 * @author jblok
 */
@ServoyDocumented(category = ServoyDocumented.DESIGNTIME, publicName = "Variable")
public class ScriptVariable extends AbstractBase implements IVariable, IDataProvider, ISupportUpdateableName, ISupportHTMLToolTipText, ISupportContentEquals,
	IPersistCloneable, ICloneable
{
	/*
	 * Attributes, do not change default values do to repository default_textual_classvalue
	 */
	private String name = null;
	private String prefixedName = null;
	private String defaultValue = null;
	private int variableType;

	// tmp storage
	private transient Object value = null;
	private String comment;

	public static final String GLOBAL_PREFIX = "globals"; //$NON-NLS-1$
	public static final String GLOBAL_DOT_PREFIX = GLOBAL_PREFIX + '.';

	/**
	 * Constructor I
	 */
	ScriptVariable(ISupportChilds parent, int element_id, UUID uuid)
	{
		super(IRepository.SCRIPTVARIABLES, parent, element_id, uuid);
	}

	/**
	 * do not use unless you have deep inside knowledge about the working
	 */
	public ScriptVariable(String name, int variableType, Object value)
	{
		super(IRepository.SCRIPTVARIABLES, null, 0, null);
		this.name = name;
		this.variableType = variableType;
		this.value = value;
	}

	/*
	 * _____________________________________________________________ Methods from this class
	 */

	/**
	 * update the name
	 * 
	 * @param arg the name
	 */
	public void setName(String arg)
	{
		if (name != null) throw new UnsupportedOperationException("Can't set name 2x, use updateName"); //$NON-NLS-1$
		name = arg;
		prefixedName = null;
	}

	/**
	 * Set the name
	 * 
	 * @param arg the name
	 */
	public void updateName(IValidateName validator, String arg) throws RepositoryException
	{
		validator.checkName(arg, getID(), new ValidatorSearchContext(getRootObject(), IRepository.SCRIPTVARIABLES), false);
		checkForNameChange(name, arg);
		name = arg;
		prefixedName = null;
		getRootObject().getChangeHandler().fireIPersistChanged(this);
	}

	/**
	 * The name of the variable.
	 */
	public String getName()
	{
		return name;
	}

	public String getComment()
	{
		return comment;
	}

	public void setComment(String arg)
	{
		this.comment = arg;
	}

	/**
	 * Set the value
	 * 
	 * @param arg the value
	 */
	public void setValue(Object arg)
	{
		value = arg;

	}

	/**
	 * Get the value
	 * 
	 * @return the value
	 */
	public Object getValue()
	{
		return value;
	}

	/**
	 * Set the variableType
	 * 
	 * @param arg the variableType
	 */
	@SuppressWarnings("nls")
	public void setVariableType(int arg)
	{
		boolean hit = false;
		int[] types = Column.allDefinedTypes;
		for (int element : types)
		{
			if (arg == element)
			{
				hit = true;
				break;
			}
		}
		if (!hit)
		{
			Debug.error("unknown variable type " + arg + " for variable " + name + " reverting to previous or MEDIA type", new RuntimeException());
			if (variableType == 0)
			{
				variableType = IColumnTypes.MEDIA;
			}
			flagChanged();
			return;
		}
		checkForChange(variableType, arg);
		variableType = arg;
	}

	/**
	 * The type of the variable. Can be one of: TEXT, INTEGER, NUMBER, DATETIME or MEDIA.
	 */
	public int getVariableType()
	{
		return variableType;
	}

	@Override
	public String toString()
	{
		return getDataProviderID();
	}

	@SuppressWarnings("nls")
	public void setDefaultValue(String arg)
	{
		if ("".equals(arg))
		{
			checkForChange(defaultValue, null);
			defaultValue = null;
		}
		else
		{
			checkForChange(defaultValue, arg);
			defaultValue = arg;
		}
	}

	/**
	 * The default value of the variable. 
	 * 
	 * It is interpreted as a JS expression.
	 * 
	 * For INTEGER variables it can be an integer constant, like 10 for example.
	 * For NUMBER variables it can be a real constant, like 22.41. For DATETIME
	 * variables it can be "now", or a JS expression like "new Date()". For TEXT 
	 * variables it can be any string surrounded with quotes, like 'some text'.
	 */
	public String getDefaultValue()
	{
		return defaultValue;
	}

	@SuppressWarnings("nls")
	public Object getInitValue()
	{
		switch (Column.mapToDefaultType(variableType))
		{
			case IColumnTypes.DATETIME :
				if ("now".equalsIgnoreCase(defaultValue))
				{
					return new java.util.Date();
				}
				if (defaultValue != null)
				{
					return parseDate(defaultValue);
				}
				return null;

			case IColumnTypes.TEXT :
			{
				if ("null".equalsIgnoreCase(defaultValue))
				{
					return null;
				}
				return (defaultValue == null ? "" : defaultValue); //$NON-NLS-1$
			}

			case IColumnTypes.NUMBER :
				return ("null".equalsIgnoreCase(defaultValue) ? null : new Double(Utils.getAsDouble(defaultValue))); //$NON-NLS-1$

			case IColumnTypes.INTEGER :
				return ("null".equalsIgnoreCase(defaultValue) ? null : new Integer(Utils.getAsInteger(defaultValue))); //$NON-NLS-1$

			case IColumnTypes.MEDIA :
				if ("null".equalsIgnoreCase(defaultValue))
				{
					return null;
				}
				return defaultValue;

			default :
				return null;
		}
	}

	public String toHTML()
	{
		StringBuffer sb = new StringBuffer();
		sb.append("<html>"); //$NON-NLS-1$
		sb.append("<b>"); //$NON-NLS-1$
		sb.append(getName());
		sb.append("</b> "); //$NON-NLS-1$
		sb.append(Column.getDisplayTypeString(getVariableType()));
		sb.append(" defaultvalue: "); //$NON-NLS-1$
		sb.append(getDefaultValue());
		sb.append("</html>"); //$NON-NLS-1$
		return sb.toString();
	}

	/*
	 * _____________________________________________________________ Methods from IDataProvider
	 */

	public String getDataProviderID()//get the id
	{
		if (prefixedName == null)
		{
			if (getParent() instanceof Solution)
			{
				prefixedName = GLOBAL_DOT_PREFIX + getName();
			}
			else
			{
				prefixedName = getName();
			}
		}
		return prefixedName;
	}

	public int getDataProviderType()
	{
		return variableType;
	}

//	public String[] getDependentDataProviderIDs()
//	{
//		return null;
//	}

	public ColumnWrapper getColumnWrapper()
	{
		return null;
	}

	public int getLength()
	{
		return -1;
	}

	public boolean isEditable()
	{
		return true;
	}

	public int getFlags()
	{
		return Column.NORMAL_COLUMN;
	}

	//the repository element id can differ!
	public boolean contentEquals(Object obj)
	{
		if (obj instanceof ScriptVariable)
		{
			ScriptVariable other = (ScriptVariable)obj;
			return (getDataProviderID().equals(other.getDataProviderID()) && getVariableType() == other.getVariableType() && Utils.equalObjects(
				getDefaultValue(), other.getDefaultValue()));
		}
		return false;
	}


	@SuppressWarnings("nls")
	private static Date parseDate(String dateString)
	{
		if (dateString == null) return null;
		if (!dateString.toLowerCase().startsWith("new date(")) return null;

		String value = dateString.substring(9, dateString.lastIndexOf(')'));

		if (value.trim().length() == 0) return new Date();

		Object[] args = null;
		if (value.startsWith("\"") || value.startsWith("'"))
		{
			args = new Object[] { value.substring(1, value.length() - 1) };
		}
		else
		{
			ArrayList<String> al = new ArrayList<String>();
			StringTokenizer st = new StringTokenizer(value, ",");
			while (st.hasMoreTokens())
			{
				al.add(st.nextToken());
			}
			args = al.toArray();
		}
		Wrapper wrapper = (Wrapper)NativeDate.jsConstructor(args);
		return (Date)wrapper.unwrap();
	}
}