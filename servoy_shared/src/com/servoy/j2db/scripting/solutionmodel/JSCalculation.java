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

package com.servoy.j2db.scripting.solutionmodel;

import java.util.Arrays;

import com.servoy.j2db.IApplication;
import com.servoy.j2db.dataprocessing.FoundSetManager;
import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.persistence.RepositoryException;
import com.servoy.j2db.persistence.ScriptCalculation;
import com.servoy.j2db.persistence.ScriptNameValidator;
import com.servoy.j2db.persistence.TableNode;
import com.servoy.j2db.scripting.IJavaScriptType;
import com.servoy.j2db.scripting.TableScope;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.ServoyException;
import com.servoy.j2db.util.UUID;

@SuppressWarnings("nls")
@ServoyDocumented(category = ServoyDocumented.RUNTIME)
public class JSCalculation implements IJavaScriptType
{
	private ScriptCalculation scriptCalculation;
	private final IApplication application;
	private boolean isCopy;
	private final JSDataSourceNode parent;

	public JSCalculation(JSDataSourceNode parent, ScriptCalculation scriptCalculation, IApplication application, boolean isNew)
	{
		this.parent = parent;
		this.scriptCalculation = scriptCalculation;
		this.application = application;
		this.isCopy = isNew;
	}

	private void checkModification()
	{
		if (!isCopy)
		{
			try
			{

				TableNode tableNode = application.getFlattenedSolution().getSolutionCopyTableNode(parent.getDataSource());
				ScriptCalculation sc = tableNode.getScriptCalculation(scriptCalculation.getName());
				if (sc == null)
				{
					sc = (ScriptCalculation)scriptCalculation.clonePersist();
					tableNode.addChild(sc);
					scriptCalculation = sc;
				}
				isCopy = true;
			}
			catch (RepositoryException e)
			{
				Debug.error(e);
				throw new RuntimeException("Can't alter ScriptCalculation " + scriptCalculation.getName() + ", clone failed", e);
			}
		}
	}

	public int js_getVariableType()
	{
		return scriptCalculation.getDataProviderType();
	}

	public void js_setVariableType(int type)
	{
		if (js_isStored()) throw new RuntimeException("Can't alter variable type of the stored calculation " + scriptCalculation.getName());
		checkModification();
		scriptCalculation.setTypeAndCheck(type);

		TableScope tableScope;
		try
		{
			tableScope = (TableScope)application.getScriptEngine().getTableScope(scriptCalculation.getTable());
			if (tableScope != null)
			{
				tableScope.put(scriptCalculation, scriptCalculation);
				((FoundSetManager)application.getFoundSetManager()).flushSQLSheet(scriptCalculation.getTable().getDataSource());
			}

		}
		catch (RepositoryException e)
		{
			Debug.error(e);
		}

	}

	/**
	 * This method returns the name of the stored calculation.
	 * 
	 * @sample
	 * var calc = solutionModel.newCalculation("function myCalculation() { return 123; }", JSVariable.INTEGER, "db:/example_data/customers");
	 * application.output(calc.getName()); 
	 * 
	 * @return the name of the stored calculation
	 */
	public String js_getName()
	{
		return scriptCalculation.getName();
	}

	/**
	 * Returns whether this calculation is a stored one or not.
	 * 
	 * @sample
	 * var calc = solutionModel.newCalculation("function myCalculation() { return 123; }", JSVariable.INTEGER, "db:/example_data/customers");
	 * if (calc.isStored()) application.output("The calculation is stored");
	 * else application.output("The calculation is not stored");
	 * 
	 * @return true if the calculation is stored, false otherwise
	 */
	public boolean js_isStored()
	{
		try
		{
			return scriptCalculation.getTable().getColumn(scriptCalculation.getName()) != null;
		}
		catch (RepositoryException e)
		{
			Debug.error(e);
		}
		return false;
	}

	public String js_getCode()
	{
		return scriptCalculation.getDeclaration();
	}

	public void js_setCode(String code)
	{
		checkModification();

		String name = JSMethod.parseName(code);
		if (!name.equals(scriptCalculation.getName()))
		{
			try
			{
				scriptCalculation.updateName(new ScriptNameValidator(application.getFlattenedSolution()), name);
			}
			catch (RepositoryException e)
			{
				throw new RuntimeException("Error updating the name from " + scriptCalculation.getName() + " to " + name, e); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
		scriptCalculation.setDeclaration(code);

		try
		{
			TableScope tableScope = (TableScope)application.getScriptEngine().getTableScope(scriptCalculation.getTable());
			if (tableScope != null)
			{
				tableScope.put(scriptCalculation, scriptCalculation);
				String dataSource = scriptCalculation.getTable().getDataSource();
				FoundSetManager fsm = (FoundSetManager)application.getFoundSetManager();
				fsm.flushSQLSheet(dataSource);
				fsm.getRowManager(dataSource).clearCalcs(null, Arrays.asList(scriptCalculation.getName()));
			}
		}
		catch (ServoyException e)
		{
			Debug.error(e);
		}
	}

	@Override
	public String toString()
	{
		return "JSCalculation[name:" + scriptCalculation.getName() + ",type:" + scriptCalculation.getTypeAsString() + ']';
	}

	/**
	 * Returns the UUID of the calculation.
	 * 
	 * @sample
	 * var calc = solutionModel.newCalculation("function myCalculation() { return 123; }", JSVariable.INTEGER, "db:/example_data/customers");
	 * application.output(calc.getUUID().toString()); 
	 */
	public UUID js_getUUID()
	{
		return scriptCalculation.getUUID();
	}

}
