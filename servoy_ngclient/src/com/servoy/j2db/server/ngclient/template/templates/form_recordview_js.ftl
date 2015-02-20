<#--
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2013 Servoy BV

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
-->
	
${registerMethod}("${controllerName}", function($scope,$servoyInternal,$sabloApplication,$timeout,$formService,$sabloUtils) {

	var beans = {
	<#list baseComponents as bc>
		'${bc.name}': ${bc.propertiesString}<#if bc_has_next>,</#if>
	</#list>
	}

	var formProperties = ${propertiesString}
	var formState = $servoyInternal.initFormState("${name}", beans, formProperties, $scope);
	
	$scope.model = formState.model;
	$scope.api = formState.api;
	$scope.layout = formState.layout;
	$scope.formStyle = formState.style;
	$scope.formProperties = formState.properties;
	$scope.formname = "${name}";

	<#list parts as part>
	$scope.${part.name}Style = ${part.style};
	</#list>
	
	var getExecutor = function(beanName,eventType) {
		var callExecutor = function(args, rowId) {
			return $sabloApplication.getExecutor("${name}").on(beanName,eventType,null,args,rowId);
		}
		var wrapper = function() {
			return callExecutor(arguments, null);
		}
		wrapper.selectRecordHandler = function(rowId) {
			return function () { return callExecutor(arguments, rowId); }
		}
		return wrapper;
	}

	var servoyApi = function(beanname) {
		return {
			showForm: function(formname,relationname,formIndex) {
				$formService.showForm(formname,$scope.formname,beanname,relationname,formIndex);
			},
			hideForm: function(formname,relationname,formIndex) {
				return $formService.hideForm(formname,$scope.formname,beanname,relationname,formIndex);
			},
			setFormEnabled: function(formname, enabled) {
				$formService.setFormEnabled(formname,enabled);
			},
			setFormReadOnly: function(formname, readOnly) {
				$formService.setFormReadOnly(formname,readOnly);
			},
			getFormUrl: function(formUrl) {
				return $formService.getFormUrl(formUrl);
			},
			startEdit: function(propertyName) {
				$sabloApplication.callService("formService", "startEdit", {formname:$scope.formname,beanname:beanname,property:propertyName},true)
			},
			apply: function(propertyName) {
				$servoyInternal.pushDPChange("${name}", beanname, propertyName);
			}
		}
	}

	$scope.handlers = {
	<#list baseComponents as bc>
		'${bc.name}': {"svy_servoyApi":servoyApi('${bc.name}')<#list bc.handlers as handler>,${handler}:getExecutor('${bc.name}', '${handler}')</#list>}<#if bc_has_next>,</#if>
	</#list>
	}
	
	formState.handlers = $scope.handlers;
	
	var wrapper = function(beanName) {
		return function(newvalue,oldvalue) {
				if(oldvalue === newvalue) return;
				$servoyInternal.sendChanges(newvalue,oldvalue, "${name}", beanName);
		}
	}
	
	var watches = {};

	formState.addWatches = function (beanNames) {
		if (beanNames) {
		 	for (var beanName in beanNames) {
		 		watches[beanName] = $scope.$watch($sabloUtils.generateWatchFunctionFor($scope, "model", "beanName"), wrapper(beanName), true);
			}
		}
		else {
		<#list parts as part>
			<#if (part.baseComponents)??>
				<#list part.baseComponents as bc><#-- TODO refine this watch; it doesn't need to go deep into complex properties as those handle their own changes! -->
					watches['${bc.name}'] = $scope.$watch($sabloUtils.generateWatchFunctionFor($scope, "model", "${bc.name}"), wrapper('${bc.name}'), true);
				</#list>
			</#if>
		</#list>
		}
	}
	
	formState.removeWatches = function (beanNames) {
		if (Object.getOwnPropertyNames(watches).length == 0) return false;
		
		if (beanNames) {
		 	for (var beanName in beanNames) {
			 	if (watches[beanName]) watches[beanName]();
			}
		}
		else {
			 for (var beanName in watches) {
			 	watches[beanName]();
			 }
		}
		return true;
	}
	
	formState.getScope = function() { return $scope; }
	
	$scope.$watch("formProperties", wrapper(''), true);
});	
