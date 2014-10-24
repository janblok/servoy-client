angular.module('servoydefaultNavigator',['servoy','ui.slider']).directive('servoydefaultNavigator', function() {  
    return {
      restrict: 'E',
      scope: {
        model: "=svyModel",
        handlers: "=svyHandlers"
      },
      controller: function($scope)
      {
    	  $scope.slider_model = {};
    	  $scope.slider_handlers = {};
    	  
    	  $scope.setIndex =  function (idx){
    		  var i = parseInt(idx)
    		  if (!i) i = 1;
    		  $scope.handlers.setSelectedIndex(window.Math.abs(i));
    	  }

    	  $scope.slider_handlers.svyApply = $scope.handlers.svy_apply;
    	  $scope.slider_handlers.onStopMethodID = function(event, value) {
    		  $scope.setIndex(value);
    	  };    	  
		  $scope.slider_model.animate = false;
    	  $scope.slider_model.step = 1;	    		  
    	  $scope.slider_model.orientation = 'vertical';
    	  $scope.slider_model.range = 'max';
		  var model = $scope.model;
		  if (model.maxIndex > 0) {
	   	    $scope.slider_model.min = -1*model.maxIndex;
	        $scope.slider_model.max = -1;
	      }
    	  if (model.currentIndex) {
    	    $scope.slider_model.dataProviderID = -1*model.currentIndex;
    	  }
    	  $scope.$watch('model.maxIndex', function (newVal, oldVal, scope) 
    	  {
	    	$scope.slider_model.min = model.maxIndex > 0? -1*model.maxIndex:0;
	        $scope.slider_model.max = model.maxIndex > 0? -1:0;
    	  });
    	  $scope.$watch('model.currentIndex', function (newVal, oldVal, scope) {
    		  if(!newVal) return;
    	      $scope.slider_model.dataProviderID = -1*newVal;
    	  })    	  
      },
      templateUrl: 'servoydefault/navigator/navigator.html',
      replace: true
      
    };
}).controller('DefaultNavigatorController', function ($scope, $sabloInternal , $solutionSettings,$servoyWindowManager){  // special case using internal api
	
	$scope.default_navi = {};
	$scope.$solutionSettings = $solutionSettings;// this should be placed in the window scope similar to DialogInstanceCtrl (But main window doesn't have a window controller)
	var modelToWatch = '$solutionSettings.mainForm';
	if($scope.$eval('win') != null)  modelToWatch ='win.form'
	// ?TODO? revisit after changing to window as a component . $solutionSettings.mainForm will be merged to $servoyWindowManager (and index.ftl will have a 'DialogInstanceCtrl')
	$scope.$watch(modelToWatch, function (newVal, oldVal, scope) {
		    if(newVal) {
		    	var name = newVal.name
		    	$sabloInternal.getFormState(name).then(function(formState){
			    	$scope.default_navi.model = formState.model.svy_default_navigator;
			    	$scope.default_navi.handlers = formState.handlers.svy_default_navigator;
		    	})
		    }
	});
	
})