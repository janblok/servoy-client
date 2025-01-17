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

${registerMethod}("${name}", function($scope,$servoyInternal,$sabloApplication,$timeout,$formService,$windowService,$log,$propertyWatchesRegistry) {
	if ($log.debugEnabled) $log.debug("svy * ftl; form '${name}' - scope create: " + $scope.$id);

	var beans = {
			<#list baseComponents as bc>
				'${bc.name}': ${bc.propertiesString}<#if bc_has_next>,</#if>
			</#list>
			}

	var beanTypes = {
			<#list baseComponents as bc>
				'${bc.name}': '${bc.typeName}'<#if bc_has_next>,</#if>
			</#list>
			}

	var formProperties = ${propertiesString}

	var formState = $servoyInternal.initFormState("${name}", beans, formProperties, $scope, false);
	formState.resolving = true;
	if ($log.debugEnabled) $log.debug("svy * ftl; resolving form = ${name}");

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
			formWillShow: function(formname,relationname,formIndex) {
				$formService.formWillShow(formname,true,$scope.formname,beanname,relationname,formIndex);
			},
			hideForm: function(formname,relationname,formIndex) {
				return $formService.hideForm(formname,$scope.formname,beanname,relationname,formIndex);
			},
			getFormUrl: function(formUrl) {
				return $windowService.getFormUrl(formUrl);
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


	var wrapper = function(beanName) {
		return function(newvalue,oldvalue,property) {
				if(oldvalue === newvalue) return;
				$servoyInternal.sendChanges(newvalue,oldvalue, "${name}", beanName,property);
		}
	}

	var watches = {};

	formState.handlers = $scope.handlers;

	formState.addWatches = function (beanNames) {
	    // always first remove the existing watches if there are any.
		formState.removeWatches(beanNames);
		if (beanNames) {
		 	for (var beanName in beanNames) {
		 		watches[beanName] =	$propertyWatchesRegistry.watchDumbPropertiesForComponent($scope, beanTypes[beanName], $scope.model[beanName], wrapper(beanName));
			}
		}
		else {
		<#list parts as part>
			<#if (part.baseComponents)??>
				<#list part.baseComponents as bc>
					watches['${bc.name}'] = $propertyWatchesRegistry.watchDumbPropertiesForComponent($scope, beanTypes.${bc.name}, $scope.model.${bc.name}, wrapper('${bc.name}'));
				</#list>
			</#if>
		</#list>
		}
	}

	formState.removeWatches = function (beanNames) {
		if (Object.getOwnPropertyNames(watches).length == 0) return false;
		if (beanNames) {
		 	for (var beanName in beanNames) {
				if (watches[beanName]) for (unW in watches[beanName]) watches[beanName][unW]();
			}
		} else {
			for (var beanName in watches) {
				for (unW in watches[beanName]) watches[beanName][unW]();
			}
		}
		return true;
	}

	formState.getScope = function() { return $scope; }

	formState.addWatches();
	
	var formStateWrapper = wrapper('');
	$scope.$watch("formProperties", function(newValue, oldValue) {
		formStateWrapper(newValue, oldValue, undefined);
	}, true);

	var destroyListenerUnreg = $scope.$on("$destroy", function() {
		if ($log.debugEnabled) $log.debug("svy * ftl; form '${name}' - scope destroyed: " + $scope.$id);
		destroyListenerUnreg();
		$sabloApplication.updateScopeForState("${name}", null);
		for(var key in $scope.api) {
			$scope.api[key] = {};
		}	
		if (formState && formState.removeWatches) {
			if (!$scope.hiddenDivFormDiscarded) {
				formState.removeWatches();
				delete formState.removeWatches;
				delete formState.getScope;
				delete formState.addWatches;
				delete formState.handlers;
				$sabloApplication.unResolveFormState("${name}");
			}
			delete $scope.hiddenDivFormDiscarded;
			delete formState.resolving;
			formState = null;
		}
		else console.log("no formstate for ${name}" + formState + " " + $scope.$id);
	});
});
