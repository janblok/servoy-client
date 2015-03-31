angular.module('servoydefaultCalendar',['servoy']).directive('servoydefaultCalendar', function($log) {  
	return {
		restrict: 'E',
		scope: {
			model: "=svyModel",
			handlers: "=svyHandlers",
			api: "=svyApi",
			svyServoyapi: "="
		},
		link: function($scope, $element, $attrs) {
			var child = $element.children();
			var ngModel = child.controller("ngModel");

			$scope.style = {width:'100%',height: $scope.model.size.height,overflow:'hidden',paddingTop:'0',paddingBottom:'0'}

			child.datetimepicker({
				useCurrent:false,
				useStrict:true,
				showClear: true
			});

			$scope.$watch('model.size.height', function(){ 
				if ($scope.model.size != undefined)
				{
					$scope.style.height = $scope.model.size.height;
				}
			});

			$scope.$watch('model.format', function(){
				setDateFormat($scope.model.format);
			});

			function inputChanged(e) {
				if ($scope.model.findmode) {
					ngModel.$setViewValue(child.children("input").val());        		  
				}
				else {
					if (e.date) ngModel.$setViewValue(e.date.toDate());
					else ngModel.$setViewValue(null);
				}
				ngModel.$setValidity("", true);
				$scope.svyServoyapi.apply('dataProviderID');
			};

			// when model change, update our view, set the date in the datepicker
			ngModel.$render = function() {
				try {
					$element.off("change.dp",inputChanged);
					var x = child.data('DateTimePicker');
					if (x && !$scope.model.findmode) x.date(angular.isDefined(ngModel.$viewValue) ? ngModel.$viewValue : null); // set default date for widget open; turn undefined to null as well (undefined gives exception)
					else {
						// in find mode 
						child.children("input").val(ngModel.$viewValue);
					}
				} finally {
					$element.on("change.dp",inputChanged);
				}
			};

			var dateFormat = 'YYYY-MM-DD';

			// helper function
			function setDateFormat(format){
				if(format && format.display){
					dateFormat = moment().toMomentFormatString(format.display);
				}
				var x = child.data('DateTimePicker');
				if (angular.isDefined(x)) { // can be undefined in find mode
					x.format(dateFormat);
					try {
						$element.off("change.dp",inputChanged);
						x.date(angular.isDefined(ngModel.$viewValue) ? ngModel.$viewValue : null);
					}
					finally {
						$element.on("change.dp",inputChanged);
					}
				}
			}

			$element.on("change.dp",inputChanged);

			function onError(val){
				if (child.children("input").val() === '')
				{
					ngModel.$setViewValue(null);
					ngModel.$setValidity("", true);
					$scope.svyServoyapi.apply('dataProviderID');
					return;
				}	
				ngModel.$setValidity("", false);
				$scope.$digest();
			}
			$element.on("error.dp", onError);
			
			$scope.$watch('model.findmode', function() {
				if ($scope.model.findmode) 
				{
					child.data('DateTimePicker').destroy();
				} 
				else 
				{
					$element.off("error.dp");
					child.datetimepicker({
						useCurrent:false,
						useStrict:true,
						showClear: true
					});
					var x = child.data('DateTimePicker');
					x.format(dateFormat);
					try {
						$element.off("change.dp", inputChanged);
						x.date(angular.isDefined(ngModel.$viewValue) ? ngModel.$viewValue : null);
					} finally {
						$element.on("error.dp", onError);
						$element.on("change.dp", inputChanged);
					}
				}
			});

			var storedTooltip = false;
			$scope.api.onDataChangeCallback = function(event, returnval) {
				var stringValue = typeof returnval == 'string'
					if(!returnval || stringValue) {
						$element[0].focus();
						ngModel.$setValidity("", false);
						if (stringValue) {
							if ( storedTooltip == false)
								storedTooltip = $scope.model.toolTipText;
							$scope.model.toolTipText = returnval;
						}
					}
					else {
						ngModel.$setValidity("", true);
						if (storedTooltip !== false) $scope.model.toolTipText = storedTooltip;
						storedTooltip = false;
					}
			}
			
			/**
			 * Set the focus to this calendar.
			 * @example %%prefix%%%%elementName%%.requestFocus();
			 * @param mustExecuteOnFocusGainedMethod (optional) if false will not execute the onFocusGained method; the default value is true
			 */
			$scope.api.requestFocus = function(mustExecuteOnFocusGainedMethod) { 
				var input = $element.find('input');
				if (mustExecuteOnFocusGainedMethod === false && $scope.handlers.onFocusGainedMethodID)
				{
					input.unbind('focus');
					input[0].focus();
					input.bind('focus', $scope.handlers.onFocusGainedMethodID)
				}
				else
				{
					input[0].focus();
				}
			}
		},
		templateUrl: 'servoydefault/calendar/calendar.html'
	};
})
