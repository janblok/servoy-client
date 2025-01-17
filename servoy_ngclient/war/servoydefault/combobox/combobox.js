angular.module('servoydefaultCombobox', ['servoy', 'ui.select'])
.directive('servoydefaultCombobox', ['$timeout', '$apifunctions','$sabloConstants','$svyProperties', function ($timeout, $apifunctions,$sabloConstants,$svyProperties) {
	return {
		restrict: 'E',
		scope: {
			model: "=svyModel",
			api: "=svyApi",
			handlers: "=svyHandlers",
			svyServoyapi: "="
		},
		controller: function ($scope) {
			$scope.style = {
					height: '100%',
					width: '100%',
					overflow: 'hidden'
			};

			$scope.findMode = false;
		},
		link: function (scope, element, attrs) {

			scope.$watch("model.format", function (newVal) {
				if (newVal && newVal["text-transform"]) {
					scope.style["text-transform"] = newVal["text-transform"];
				}
			});

			scope.$watch("model.size", function (newVal) {
				scope.style['min-height'] = scope.model.size.height + 'px';
				scope.style['min-width'] = element.children().width() + 'px';
			});
			
			/**
	    	* Request the focus to this combobox.
	    	* @example %%prefix%%%%elementName%%.requestFocus();
	    	* @param mustExecuteOnFocusGainedMethod (optional) if false will not execute the onFocusGained method; the default value is true
	    	*/
			scope.api.requestFocus = function(mustExecuteOnFocusGainedMethod) { 
				var input = element.find('.ui-select-match');
				if (mustExecuteOnFocusGainedMethod === false && scope.handlers.onFocusGainedMethodID)
				{
					input.unbind('focus');
					input[0].focus();
					input.bind('focus', scope.handlers.onFocusGainedMethodID)
				}
				else
				{
					input[0].focus();
				}
			}

			var storedTooltip = false;
			scope.api.onDataChangeCallback = function(event, returnval) {
				var ngModel = element.children().controller("ngModel");
				var stringValue = (typeof returnval === 'string' || returnval instanceof String);
				if (returnval === false || stringValue) {
					element[0].focus();
					ngModel.$setValidity("", false);
					if (stringValue) {
						if (storedTooltip === false) { 
							storedTooltip = scope.model.toolTipText; 
						}
						scope.model.toolTipText = returnval;
					}
				}
				else {
					ngModel.$setValidity("", true);
					if (storedTooltip !== false) scope.model.toolTipText = storedTooltip;
					storedTooltip = false;
				}
			};

			scope.onItemSelect = function (event) {
				$timeout(function () {
					scope.svyServoyapi.apply('dataProviderID');
					if (scope.handlers.onActionMethodID) {
						scope.handlers.onActionMethodID(event?event:$.Event("click"));
					}
				}, 0);
			};
			
			scope.api.getWidth = $apifunctions.getWidth(element[0]);
	    	scope.api.getHeight = $apifunctions.getHeight(element[0]);
	    	scope.api.getLocationX = $apifunctions.getX(element[0]);
	    	scope.api.getLocationY = $apifunctions.getY(element[0]);
	    	
	    	Object.defineProperty(scope.model,$sabloConstants.modelChangeNotifier, {configurable:true,value:function(property,value) {
	    		var child = element.find("span.ui-select-toggle")
				switch(property) {
					case "borderType":
						$svyProperties.setBorder(child,value);
						break;
					case "background":
					case "transparent":
						$svyProperties.setCssProperty(child,"backgroundColor",scope.model.transparent?"transparent":scope.model.background);
						break;
					case "foreground":
						$svyProperties.setCssProperty(child,"color",value);
						break;
					case "fontType":
						$svyProperties.setCssProperty(child,"font",value);
						break;						
				}
			}});
			var destroyListenerUnreg = scope.$on("$destroy", function() {
				destroyListenerUnreg();
				delete scope.model[$sabloConstants.modelChangeNotifier];
			});
			// data can already be here, if so call the modelChange function so that it is initialized correctly.
			function pushValues() {
				if (element.find("span.ui-select-toggle").length > 0) {
					var modelChangFunction = scope.model[$sabloConstants.modelChangeNotifier];
					for (key in scope.model) {
						modelChangFunction(key,scope.model[key]);
					}
				}
				else $timeout(pushValues);
			}
			pushValues();
		},
		templateUrl: 'servoydefault/combobox/combobox.html'
	};
}])
.filter('emptyOrNull', function () {
	return function (item) {
		if (item === null || item === '') {return '&nbsp;'; }
		return item;
	};
})
.filter('showDisplayValue', function () { // filter that takes the realValue as an input and returns the displayValue
	return function (input, valuelist) {
		var i = 0;
		var realValue = input;
		if (valuelist) {
			if (input && input.hasOwnProperty("realValue")) {
				realValue = input.realValue;
			}
			//TODO performance upgrade: change the valuelist to a hashmap so that this for loop is no longer needed. 
			//maybe to something like {realValue1:displayValue1, realValue2:displayValue2, ...}
			for (i = 0; i < valuelist.length; i++) {
				if (realValue === valuelist[i].realValue) {
					return valuelist[i].displayValue;
				}
			}
			var hasRealValues = false;
			for (var i = 0; i < valuelist.length; i++) {
				var item = valuelist[i];
				if (item.realValue != item.displayValue) {
					hasRealValues = true;
					break;
				}
			}
			if (hasRealValues || valuelist.length == 0) return null;
		}
		return input;
	};
});
