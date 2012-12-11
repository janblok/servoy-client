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

package com.servoy.j2db.scripting.api.solutionmodel;

import com.servoy.j2db.scripting.annotations.ServoyMobile;


/**
 * Solution model base interface (for mobile as well as other clients).
 * For more information have a look at ISolutionModel javadoc.
 * 
 * @author rgansevles
 * @author acostescu
 *
 * @since 7.0
 */
@ServoyMobile
public interface IBaseSolutionModel
{

	/**
	 * Creates a new IBaseSMForm Object.
	 * 
	 * NOTE: See the IBaseSMForm node for more information about form objects that can be added to the new form. 
	 *
	 * @sample
	 * var myForm = solutionModel.newForm('newForm', 'my_server', 'my_table', 'myStyleName', false, 800, 600)
	 * //With only a datasource:
	 * //var myForm = solutionModel.newForm('newForm', datasource, 'myStyleName', false, 800, 600)
	 * //now you can add stuff to the form (under IBaseSMForm node)
	 * //add a label
	 * myForm.newLabel('Name', 20, 20, 120, 30)
	 * //add a "normal" text entry field
	 * myForm.newTextField('dataProviderNameHere', 140, 20, 140,20)
	 *
	 * @param name the specified name of the form
	 *
	 * @param serverName the specified name of the server for the specified table
	 *
	 * @param tableName the specified name of the table
	 *
	 * @param styleName the specified style  
	 *
	 * @param show_in_menu if true show the name of the new form in the menu; or false for not showing
	 *
	 * @param width the width of the form in pixels
	 *
	 * @param height the height of the form in pixels
	 * 
	 * @return a new IBaseSMForm object
	 */
	public IBaseSMForm newForm(String name, String serverName, String tableName, String styleName, boolean show_in_menu, int width, int height);

	/**
	 * Creates a new IBaseSMForm Object.
	 * 
	 * NOTE: See the IBaseSMForm node for more information about form objects that can be added to the new form. 
	 *
	 * @sample
	 * var myForm = solutionModel.newForm('newForm', 'db:/my_server/my_table', 'myStyleName', false, 800, 600)
	 * //now you can add stuff to the form (under IBaseSMForm node)
	 * //add a label
	 * myForm.newLabel('Name', 20, 20, 120, 30)
	 * //add a "normal" text entry field
	 * myForm.newTextField('dataProviderNameHere', 140, 20, 140,20)
	 *
	 * @param name the specified name of the form
	 *
	 * @param dataSource the specified name of the datasource for the specified table
	 *
	 * @param styleName the specified style  
	 *
	 * @param show_in_menu if true show the name of the new form in the menu; or false for not showing
	 *
	 * @param width the width of the form in pixels
	 *
	 * @param height the height of the form in pixels
	 * 
	 * @return a new ISMForm object
	 */
	public IBaseSMForm newForm(String name, String dataSource, String styleName, boolean show_in_menu, int width, int height);

	/**
	 * Makes an exact copy of the given form and gives it the new name.
	 *
	 * @sample 
	 * // get an existing form
	 * var form = solutionModel.getForm("existingForm")
	 * // make a clone/copy from it
	 * var clone = solutionModel.cloneForm("clonedForm", form)
	 * // add a new label to the clone
	 * clone.newLabel("added label",50,50,80,20);
	 * // show it
	 * forms["clonedForm"].controller.show();
	 *
	 * @param newName the new name for the form clone
	 *
	 * @param IBaseSMForm the form to be cloned 
	 * 
	 * @return a IBaseSMForm
	 */
	public IBaseSMForm cloneForm(String newName, IBaseSMForm form);

	/**
	 * Makes an exact copy of the given component (IBaseSMComponent/JSField/JSLabel) and gives it a new name.
	 *
	 * @sample
	 * // get an existing field to clone.
	 * var field = solutionModel.getForm("formWithField").getField("fieldName");
	 * // make a clone/copy of the field
	 * var clone = solutionModel.cloneComponent("clonedField",field);
	 * 
	 * @param newName the new name of the cloned component
	 *
	 * @param component the component to clone
	 *
	 * @return the exact copy of the given component
	 */
	public IBaseSMComponent cloneComponent(String newName, IBaseSMComponent component);

	/**
	 * Makes an exact copy of the given component (IBaseSMComponent/JSField/JSLabel), gives it a new name and moves it to a new parent form, specified as a parameter.
	 *
	 * @sample
	 * // get an existing field to clone.
	 * var field = solutionModel.getForm("formWithField").getField("fieldName");
	 * // get the target form for the copied/cloned field
	 * var form = solutionModel.getForm("targetForm");
	 * // make a clone/copy of the field and re parent it to the target form.
	 * var clone = solutionModel.cloneComponent("clonedField",field,form);
	 * // show it
	 * forms["targetForm"].controller.show();
	 * 
	 * @param newName the new name of the cloned component
	 *
	 * @param component the component to clone
	 *
	 * @param newParentForm the new parent form 
	 * 
	 * @return the exact copy of the given component
	 */
	public IBaseSMComponent cloneComponent(String newName, IBaseSMComponent component, IBaseSMForm newParentForm);

	/**
	 * Removes the specified form during the persistent connected client session.
	 * 
	 * NOTE: Make sure you call history.remove first in your Servoy method (script).
	 *
	 * @sample
	 * //first remove it from the current history, to destroy any active form instance
	 * var success = history.removeForm('myForm')
	 * //removes the named form from this session, please make sure you called history.remove() first
	 * if(success)
	 * {
	 * 	solutionModel.removeForm('myForm')
	 * }
	 *
	 * @param name the specified name of the form to remove
	 * 
	 * @return true is form has been removed, false if form could not be removed
	 */
	public boolean removeForm(String name);


	/**
	 * Removes the specified global method.
	 * 
	 * @sample
	 * var m1 = solutionModel.newGlobalMethod('globals', 'function myglobalmethod1(){application.output("Global Method 1");}');
	 * var m2 = solutionModel.newGlobalMethod('globals', 'function myglobalmethod2(){application.output("Global Method 2");}');
	 * 
	 * var success = solutionModel.removeGlobalMethod('globals', 'myglobalmethod1');
	 * if (success == false) application.output('!!! myglobalmethod1 could not be removed !!!');
	 * 
	 * var list = solutionModel.getGlobalMethods('globals');
	 * for (var i = 0; i < list.length; i++) { 
	 * 	application.output(list[i].code);
	 * }
	 * 
	 * @param scopeName the scope in which the method is declared
	 * @param name the name of the global method to be removed
	 * @return true if the removal was successful, false otherwise
	 */
	public boolean removeGlobalMethod(String scopeName, String name);

	/**
	 * Removes the specified global variable.
	 * 
	 * @sample
	 * var v1 = solutionModel.newGlobalVariable('globals', 'globalVar1', IBaseSMVariable.INTEGER);
	 * var v2 = solutionModel.newGlobalVariable('globals', 'globalVar2', IBaseSMVariable.TEXT);
	 * 
	 * var success = solutionModel.removeGlobalVariable('globals', 'globalVar1');
	 * if (success == false) application.output('!!! globalVar1 could not be removed !!!');
	 * 
	 * var list = solutionModel.getGlobalVariables('globals');
	 * for (var i = 0; i < list.length; i++) {
	 * 	application.output(list[i].name + '[ ' + list[i].variableType + ']: ' + list[i].variableType);
	 * }
	 * 
	 * @param scopeName the scope in which the variable is declared
	 * @param name the name of the global variable to be removed 
	 * @return true if the removal was successful, false otherwise
	 */
	public boolean removeGlobalVariable(String scopeName, String name);

	/**
	 * Gets the specified form object and returns information about the form (see IBaseSMForm node).
	 *
	 * @sample
	 * var myForm = solutionModel.getForm('existingFormName');
	 * //get the style of the form (for all other properties see IBaseSMForm node)
	 * var scrollBars = myForm.scrollbars;
	 *
	 * @param name the specified name of the form
	 * 
	 * @return a IBaseSMForm
	 */
	public IBaseSMForm getForm(String name);

//	/**
//	 * Get an array of forms, that are all based on datasource/servername.
//	 *
//	 * @sample
//	 * var forms = solutionModel.getForms(datasource)
//	 * for (var i in forms)
//	 * 	application.output(forms[i].name)
//	 *
//	 * @param datasource the datasource or servername 
//	 * 
//	 * @return an array of IBaseSMForm type elements
//	 */
//	public IBaseSMForm[] getForms(String datasource);
//
//	/**
//	 * Get an array of forms, that are all based on datasource/servername and tablename.
//	 *
//	 * @sample
//	 * var forms = solutionModel.getForms(datasource,tablename)
//	 * for (var i in forms)
//	 * 	application.output(forms[i].name)
//	 *
//	 * @param server the datasource or servername 
//	 * 
//	 * @param tablename the tablename
//	 * 
//	 * @return an array of IBaseSMForm type elements
//	 */
//	public IBaseSMForm[] getForms(String server, String tablename);
//
//	/**
//	 * Get an array of all forms.
//	 *
//	 * @sample
//	 * var forms = solutionModel.getForms()
//	 * for (var i in forms)
//	 * 	application.output(forms[i].name)
//	 *
//	 * @return an array of IBaseSMForm type elements
//	 */
//	public IBaseSMForm[] getForms();

	/**
	 * Gets an existing valuelist by the specified name and returns a IBaseSMValueList Object that can be assigned to a field.
	 *
	 * @sample
	 * var myValueList = solutionModel.getValueList('myValueListHere')
	 * //now set the valueList property of your field
	 * //myField.valuelist = myValueList
	 *
	 * @param name the specified name of the valuelist
	 * 
	 * @return a IBaseSMValueList object
	 */
	public IBaseSMValueList getValueList(String name);

//	/**
//	 * Gets an array of all valuelists for the currently active solution.
//	 *
//	 * @sample 
//	 * var valueLists = solutionModel.getValueLists();
//	 * if (valueLists != null && valueLists.length != 0)
//	 * 	for (var i in valueLists)
//	 * 		application.output(valueLists[i].name); 
//	 * 
//	 * @return an array of IBaseSMValueList objects
//	 */
//	public IBaseSMValueList[] getValueLists();

	/**
	 * Creates a new global variable with the specified name and number type.
	 * 
	 * NOTE: The global variable number type is based on the value assigned from the SolutionModel-IBaseSMVariable node; for example: IBaseSMVariable.INTEGER.
	 *
	 * @sample 
	 * var myGlobalVariable = solutionModel.newGlobalVariable('globals', 'newGlobalVariable', IBaseSMVariable.INTEGER); 
	 * myGlobalVariable.defaultValue = 12;
	 *	//myGlobalVariable.defaultValue = "{a:'First letter',b:'Second letter'}"
	 *
	 * @param scopeName the scope in which the variable is created
	 * @param name the specified name for the global variable 
	 *
	 * @param type the specified number type for the global variable
	 * 
	 * @return a IBaseSMVariable object
	 */
	public IBaseSMVariable newGlobalVariable(String scopeName, String name, int type);

	/**
	 * Gets an existing global variable by the specified name.
	 *
	 * @sample 
	 * var globalVariable = solutionModel.getGlobalVariable('globals', 'globalVariableName');
	 * application.output(globalVariable.name + " has the default value of " + globalVariable.defaultValue);
	 * 
	 * @param scopeName the scope in which the variable is searched
	 * @param name the specified name of the global variable
	 * 
	 * @return a IBaseSMVariable 
	 */
	public IBaseSMVariable getGlobalVariable(String scopeName, String name);

	/**
	 * Gets an array of all scope names used.
	 * 
	 * @sample
	 * var scopeNames = solutionModel.getScopeNames();
	 * for (var name in scopeNames)
	 * 	application.output(name);
	 * 
	 * @return an array of String scope names
	 */
	public String[] getScopeNames();

//	/**
//	 * Gets an array of all global variables.
//	 * 
//	 * @sample
//	 * var globalVariables = solutionModel.getGlobalVariables('globals');
//	 * for (var i in globalVariables)
//	 * 	application.output(globalVariables[i].name + " has the default value of " + globalVariables[i].defaultValue);
//	 * 
//	 * @return an array of IBaseSMVariable type elements
//	 * 
//	 */
//	public IBaseSMVariable[] getGlobalVariables();
//
//	/**
//	 * @clonedesc getGlobalVariables()
//	 * @sampleas getGlobalVariables()
//	 * @param scopeName limit to global vars of specified scope name
//	 * 
//	 * @return an array of IBaseSMVariable type elements
//	 */
//	public IBaseSMVariable[] getGlobalVariables(String scopeName);


	/**
	 * Creates a new global method with the specified code in a scope.
	 *
	 * @sample 
	 * var method = solutionModel.newGlobalMethod('globals', 'function myglobalmethod(){currentcontroller.newRecord()}')
	 *
	 * @param scopeName the scope in which the method is created
	 * @param code the specified code for the global method
	 * 
	 * @return a IBaseSMMethod object
	 */
	public IBaseSMMethod newGlobalMethod(String scopeName, String code);

	/**
	 * Gets an existing global method by the specified name.
	 *
	 * @sample 
	 * var method = solutionModel.getGlobalMethod('globals', 'nameOfGlobalMethod'); 
	 * if (method != null) application.output(method.code);
	 * 
	 * @param scopeName the scope in which the method is searched
	 * @param name the name of the specified global method
	 * 
	 * @return a IBaseSMMethod
	 */
	public IBaseSMMethod getGlobalMethod(String scopeName, String name);

//	/**
//	 * The list of all global methods.
//	 * 
//	 * @sample
//	 * var methods = solutionModel.getGlobalMethods('globals'); 
//	 * for (var x in methods) 
//	 * 	application.output(methods[x].getName());
//	 * 
//	 * @return an array of IBaseSMMethod type elements
//	 * 
//	 */
//	public IBaseSMMethod[] getGlobalMethods();
//
//	/**
//	 * @clonedesc getGlobalMethods()
//	 * @sampleas getGlobalMethods()
//	 * @param scopeName limit to global methods of specified scope name
//	 * @return an array of IBaseSMMethod type elements
//	 */
//	public IBaseSMMethod[] getGlobalMethods(String scopeName);

	/**
	 * Creates a new IBaseSMRelation Object with a specified name; includes the primary datasource, foreign datasource and the type of join for the new relation.
	 *
	 * @sample 
	 * var rel = solutionModel.newRelation('myRelation', myPrimaryDataSource, myForeignDataSource, IBaseSMRelation.INNER_JOIN);
	 * application.output(rel.getRelationItems()); 
	 *
	 * @param name the specified name of the new relation
	 *
	 * @param primaryDataSource the specified name of the primary datasource
	 *
	 * @param foreignDataSource the specified name of the foreign datasource
	 *
	 * @param joinType the type of join for the new relation; IBaseSMRelation.INNER_JOIN, IBaseSMRelation.LEFT_OUTER_JOIN
	 * 
	 * @return a IBaseSMRelation object
	 */
	public IBaseSMRelation newRelation(String name, String primaryDataSource, String foreignDataSource, int joinType);

	/**
	 * Gets an existing relation by the specified name and returns a IBaseSMRelation Object.
	 * 
	 * @sample 
	 * var relation = solutionModel.getRelation('name');
	 * application.output("The primary server name is " + relation.primaryServerName);
	 * application.output("The primary table name is " + relation.primaryTableName); 
	 * application.output("The foreign table name is " + relation.foreignTableName); 
	 * application.output("The relation items are " + relation.getRelationItems());
	 * 
	 * @param name the specified name of the relation
	 * 
	 * @return a IBaseSMRelation
	 */
	public IBaseSMRelation getRelation(String name);

//	/**
//	 * Gets an array of all relations; or an array of all global relations if the specified table is NULL.
//	 *
//	 * @sample 
//	 * var relations = solutionModel.getRelations('server_name','table_name');
//	 * if (relations.length != 0)
//	 * 	for (var i in relations)
//	 * 		application.output(relations[i].name);
//	 *
//	 * @param datasource the specified name of the datasource for the specified table
//	 * 
//	 * @return an array of all relations (all elements in the array are of type IBaseSMRelation)
//	 */
//
//	public IBaseSMRelation[] getRelations(String datasource);
//
//	/**
//	 * @clonedesc getRelations(String)
//	 * @sampleas getRelations(String)
//	 * @param servername the specified name of the server for the specified table
//	 * @param tablename the specified name of the table
//	 * 
//	 * @return an array of all relations (all elements in the array are of type IBaseSMRelation)
//	 */
//	public IBaseSMRelation[] getRelations(String servername, String tablename);

}
