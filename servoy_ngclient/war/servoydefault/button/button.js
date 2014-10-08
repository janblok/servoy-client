angular.module('servoydefaultButton',['servoy']).directive('servoydefaultButton', function(formatFilterFilter) {  
    return {
      restrict: 'E',
      scope: {
       	model: "=svyModel",
       	handlers: "=svyHandlers"
      },
      controller: function($scope, $element, $attrs) {
    	  $scope.containerstyle = {overflow:'hidden',position:'absolute'}
          $scope.contentstyle = {width:'100%',overflow:'hidden',position:'relative',whiteSpace:'nowrap'}
      },
      templateUrl: 'servoydefault/button/button.html'
    };
  })
  
  
  
