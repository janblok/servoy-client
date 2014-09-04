var controllerProvider;
angular.module('servoyApp', ['servoy','webStorageModule','ngGrid','servoy-components', 'webSocketModule','servoyWindowManager','pasvaz.bindonce']).config(function($controllerProvider) {
	controllerProvider = $controllerProvider;
}).factory('$servoyInternal', function ($rootScope,$swingModifiers,webStorage,$anchorConstants, $q,$solutionSettings, $window, $webSocket,$sessionService,$sabloConverters,$sabloUtils,$utils) {
	   // formName:[beanname:{property1:1,property2:"test"}] needs to be synced to and from server
	   // this holds the form model with all the data, per form is this the "synced" view of the the IFormUI on the server 
	   // (3 way binding)
	   var formStates = {};
	   var formStatesConversionInfo = {};
	   
	   var deferredProperties = {};
	   var deferredformStates = {};
	   var getChangeNotifier = function(formName, beanName) {
		   return function() {
			   // will be called by the custom property when it needs to send changes server size
			   var beanModel = formStates[formName].model[beanName];
			   sendChanges(beanModel, beanModel, formName, beanName);
		   }
	   }

	   var getComponentChanges = function(now, prev, beanConversionInfo, beanLayout, parentSize, changeNotifier) {
		   if (ignoreChanges) return false;
		   // first build up a list of all the properties both have.
		   var fulllist = $sabloUtils.getCombinedPropertyNames(now,prev);
		   var changes = {}, prop;

		   for (prop in fulllist) {
			   var changed = false;
			   if (!prev) {
				   changed = true;
			   }
			   else if (now[prop] && now[prop][$sabloConverters.INTERNAL_IMPL] && now[prop][$sabloConverters.INTERNAL_IMPL].isChanged && now[prop][$sabloConverters.INTERNAL_IMPL].isChanged())
			   {
				   changed = true;
			   }
			   else if (prev[prop] !== now[prop]) {
				   if (typeof now[prop] == "object") {
					   if ($sabloUtils.isChanged(now[prop], prev[prop], beanConversionInfo ? beanConversionInfo[prop] : undefined)) {
						   changed = true;
					   }
				   } else {
					   changed = true;
				   }
			   }
			   if (changed) {
				   if (beanConversionInfo && beanConversionInfo[prop]) changes[prop] = $sabloConverters.convertFromClientToServer(now[prop], beanConversionInfo[prop], prev ? prev[prop] : undefined);
				   else changes[prop] = $sabloUtils.convertClientObject(now[prop])
			   }
		   }
		   if (changes.location || changes.size || changes.visible || changes.anchors) {
			   if (beanLayout) {
				   applyBeanData(now /*formStates[formname].model[beanname]*/, beanLayout, changes, parentSize, changeNotifier);
			   }
		   }
		   return changes;
	   };
	   
	   var sendChanges = function(now, prev, formname, beanname) {
		   var changes = getComponentChanges(now, prev, $utils.getInDepthProperty(formStatesConversionInfo, formname, beanname),
				   formStates[formname].layout[beanname], formStates[formname].properties.designSize, getChangeNotifier(formname, beanname));
		   if (Object.getOwnPropertyNames(changes).length > 0) {
			   getSession().sendMessageObject({cmd:'datapush',formname:formname,beanname:beanname,changes:changes})
		   }
	   };

	   var applyBeanData = function(beanModel, beanLayout, beanData, containerSize, changeNotifier, beanConversionInfo, newConversionInfo) {
		   if (newConversionInfo) { // then means beanConversionInfo should also be defined - we assume that
			   // beanConversionInfo will be granularly updated in the loop below
			   // (to not drop other property conversion info when only one property is being applied granularly to the bean)
			   $sabloConverters.convertFromServerToClient(beanData, newConversionInfo, beanModel);
		   }

		   for(var key in beanData) {
			   // remember conversion info for when it will be sent back to server - it might need special conversion as well
			   if (newConversionInfo && newConversionInfo[key]) {
				   beanConversionInfo[key] = newConversionInfo[key];
				   if (beanModel[key] !== beanData[key] && beanData[key] && beanData[key][$sabloConverters.INTERNAL_IMPL] && beanData[key][$sabloConverters.INTERNAL_IMPL].setChangeNotifier) {
					   beanData[key][$sabloConverters.INTERNAL_IMPL].setChangeNotifier(changeNotifier);
				   }
			   }

			   // also make location and size available in model
			   beanModel[key] = beanData[key];
		   }

		   //beanData.anchors means anchors changed or must be initialized
		   if((beanData.anchors !== undefined) && containerSize) {
			   var anchoredTop = (beanModel.anchors & $anchorConstants.NORTH) != 0; // north
			   var anchoredRight = (beanModel.anchors & $anchorConstants.EAST) != 0; // east
			   var anchoredBottom = (beanModel.anchors & $anchorConstants.SOUTH) != 0; // south
			   var anchoredLeft = (beanModel.anchors & $anchorConstants.WEST) != 0; //west

			   var runtimeChanges = beanData.size != undefined || beanData.location != undefined;

			   if (!anchoredLeft && !anchoredRight) anchoredLeft = true;
			   if (!anchoredTop && !anchoredBottom) anchoredTop = true;

			   if (anchoredTop)
			   {
				   if (beanLayout.top == undefined || runtimeChanges && beanModel.location != undefined) beanLayout.top = beanModel.location.y + 'px';
			   }
			   else delete beanLayout.top;

			   if (anchoredBottom)
			   {
				   if (beanLayout.bottom == undefined) {
					   beanLayout.bottom = containerSize.height - beanModel.location.y - beanModel.size.height;
					   if(beanModel.offsetY) {
						   beanLayout.bottom = beanLayout.bottom - beanModel.offsetY;
					   }
					   beanLayout.bottom = beanLayout.bottom + "px";
				   }
			   }
			   else delete beanLayout.bottom;

			   if (!anchoredTop || !anchoredBottom) beanLayout.height = beanModel.size.height + 'px';
			   else delete beanLayout.height;

			   if (anchoredLeft)
			   {
				   if ( $solutionSettings.ltrOrientation)
				   {
					   if (beanLayout.left == undefined || runtimeChanges && beanModel.location != undefined)
					   {	
						   beanLayout.left =  beanModel.location.x + 'px';
					   }
				   }
				   else
				   {
					   if (beanLayout.right == undefined || runtimeChanges && beanModel.location != undefined)
					   {	
						   beanLayout.right =  beanModel.location.x + 'px';
					   }
				   }
			   }
			   else if ( $solutionSettings.ltrOrientation)
			   {
				   delete beanLayout.left;
			   }
			   else
			   {
				   delete beanLayout.right;
			   }

			   if (anchoredRight)
			   {
				   if ( $solutionSettings.ltrOrientation)
				   {
					   if (beanLayout.right == undefined) beanLayout.right = (containerSize.width - beanModel.location.x - beanModel.size.width) + "px";
				   }
				   else
				   {
					   if (beanLayout.left == undefined) beanLayout.left = (containerSize.width - beanModel.location.x - beanModel.size.width) + "px";
				   }
			   }
			   else if ( $solutionSettings.ltrOrientation)
			   {
				   delete beanLayout.right;
			   }
			   else
			   {
				   delete beanLayout.left;
			   }

			   if (!anchoredLeft || !anchoredRight) beanLayout.width = beanModel.size.width + 'px';
			   else delete beanLayout.width;
		   }

		   //we set the following properties iff the bean doesn't have anchors
		   if (beanModel.anchors == undefined)
		   {
			   if (beanModel.location)
			   {
				   if ( $solutionSettings.ltrOrientation)
				   {
					   beanLayout.left = beanModel.location.x+'px';
				   }
				   else
				   {
					   beanLayout.right = beanModel.location.x+'px';
				   }
				   beanLayout.top = beanModel.location.y+'px';
			   }

			   if (beanModel.size)
			   {
				   beanLayout.width = beanModel.size.width+'px';
				   beanLayout.height = beanModel.size.height+'px';
			   }
		   }

		   if (beanModel.visible != undefined)
		   {
			   if (beanModel.visible == false)
			   {
				   beanLayout.display = 'none';
			   }
			   else
			   {
				   delete beanLayout.display;
			   }
		   }
	   }
	   
	   
		  
	   var ignoreChanges = false;
	   var wsSession = null;
	   function connect() {
		   // maybe do this with defer ($q)
		   var solName = decodeURIComponent((new RegExp('[?|&]s=' + '([^&;]+?)(&|#|;|$)').exec($window.location.search)||[,""])[1].replace(/\+/g, '%20'))||null
		   if (!solName) $solutionSettings.solutionName  = /.*\/(\w+)\/.*/.exec($window.location.pathname)[1];
		   else $solutionSettings.solutionName  = solName;
		   $solutionSettings.windowName = webStorage.session.get("windowid");
		   wsSession = $webSocket.connect('/solutions/'+$solutionSettings.solutionName, [webStorage.session.get("sessionid"), $solutionSettings.windowName, $solutionSettings.solutionName])
		   wsSession.onMessageObject = function (msg, conversionInfo) {
			   try {
				   // data got back from the server
				   if (msg.forms) {
					   $rootScope.$apply(function() {
						   ignoreChanges = true;
						   try {
							   for(var formname in msg.forms) {
								   // current model
								   var formState = formStates[formname];
								   // if the formState is on the server but not here anymore, skip it. 
								   // this can happen with a refresh on the browser.
								   if (!formState) continue;
								   var formModel = formState.model;
								   var layout = formState.layout;
								   var newFormData = msg.forms[formname];
								   var newFormProperties = newFormData['']; // f form properties
								   var newFormConversionInfo = (conversionInfo && conversionInfo.forms && conversionInfo.forms[formname]) ? conversionInfo.forms[formname] : undefined;

								   if(newFormProperties) {
									   if (newFormConversionInfo && newFormConversionInfo['']) $sabloConverters.convertFromServerToClient(newFormProperties, newFormConversionInfo[''], formModel['']);
									   if (!formModel['']) formModel[''] = {};
									   for(var p in newFormProperties) {
										   formModel[''][p] = newFormProperties[p]; 
									   } 
								   }

								   for (var beanname in newFormData) {
									   // copy over the changes, skip for form properties (beanname empty)
									   if (beanname != '') {
										   if (formModel[beanname]!= undefined && (newFormData[beanname].size != undefined ||  newFormData[beanname].location != undefined)) {	
											   //size or location were changed at runtime, we need to update components with anchors
											   newFormData[beanname].anchors = formModel[beanname].anchors;
										   }

										   var newBeanConversionInfo = newFormConversionInfo ? newFormConversionInfo[beanname] : undefined;
										   var beanConversionInfo = newBeanConversionInfo ? $utils.getOrCreateInDepthProperty(formStatesConversionInfo, formname, beanname) : undefined; // we could do a get instead of undefined, but normally that value is not needed if the new conversion info is undefined
										   applyBeanData(formModel[beanname], layout[beanname], newFormData[beanname], formState.properties.designSize, getChangeNotifier(formname, beanname), beanConversionInfo, newBeanConversionInfo);
										   for (var defProperty in deferredProperties) {
											   for(var key in newFormData[beanname]) {
												   if (defProperty == (formname + "_" + beanname + "_" + key)) {
													   deferredProperties[defProperty].resolve(newFormData[beanname][key]);
													   delete deferredProperties[defProperty];
												   }
											   }
										   } 
									   }
								   }
								   if(deferredformStates[formname]){
									   deferredformStates[formname].resolve(formStates[formname])
									   delete deferredformStates[formname]
								   }
								   
								   if (msg.initialdatarequest)
								   		formState.addWatches();
							   }
						   } finally {
							   ignoreChanges = false;
						   }
					   });
				   }

				   if (conversionInfo && conversionInfo.call) $sabloConverters.convertFromServerToClient(msg.call, conversionInfo.call);
				   if (msg.call) {
					   // {"call":{"form":"product","element":"datatextfield1","api":"requestFocus","args":[arg1, arg2]}, // optionally "viewIndex":1 
					   // "{ conversions: {product: {datatextfield1: {0: "Date"}}} }
					   var call = msg.call;
					   var formState = formStates[call.form];
					   if (call.viewIndex != undefined) {
						   var funcThis = formState.api[call.bean][call.viewIndex]; 
						   if (funcThis)
						   {
							   var func = funcThis[call.api];
						   }
						   else
						   {
							   console.warn("cannot call " + call.api + " on " + call.bean + " because viewIndex "+ call.viewIndex +" api is not found")
						   }
					   }
					   else if (call.parentComponentName != undefined)
					   {
						   // handle nested components
						   var funcThis = formState.model[call.parentComponentName][call.parentComponentProperty][call.parentComponentIndex].api;
						   var func = funcThis[call.api];
					   }
					   else {
						   var funcThis = formState.api[call.bean];
						   var func = funcThis[call.api];
					   }
					   if (!func) {
						   // if setFindMode not present, set editable/readonly state
						   if (call.api != "setFindMode") 
						   {
							   console.warn("bean " + call.bean + " did not provide the api: " + call.api)
						   }
						   else
						   {
							   if (call.args[0])
							   {
								   formState.model[call.bean].readOnlyBeforeFindMode = formState.model[call.bean].readOnly;
								   formState.model[call.bean].readOnly = true;
							   }
							   else
							   {
								   formState.model[call.bean].readOnly = formState.model[call.bean].readOnlyBeforeFindMode;
							   }
						   }
						   return;
					   }

					   return $rootScope.$apply(function() {
						   return func.apply(funcThis, call.args)
					   })
				   }
				   if (msg.sessionid) {
					   webStorage.session.add("sessionid",msg.sessionid);
				   }
				   if (msg.styleSheetPath) {
					   $solutionSettings.styleSheetPath = msg.styleSheetPath;
				   }	   
				   /**
				    * TODO sesionExpired should not be called forom the protocol , 
				    * it should be a direct call to a service (also check to see if  noLicense and maintenanceMode can be moved to a service)
				    * */
				   if(msg.noLicense){
					   $sessionService.setNoLicense(msg.noLicense)	        		
				   }	       
				   if(msg.maintenanceMode){
					   $sessionService.setMaintenanceMode(msg.maintenanceMode)    		
				   }

				   /** end TODO*/
				   if (msg.windowid) {
					   $solutionSettings.windowName = msg.windowid;
					   webStorage.session.add("windowid",msg.windowid);
				   }
			   } finally {
				   ignoreChanges = false;
			   }
		   };
	   }
	   function getSession() {
		   if (wsSession == null) throw "Session is not created yet, first call connect()";
		   return wsSession;
	   }
	   return {
		   connect: connect,
		   // used by custom property component[] to implement nested component logic
		   applyBeanData: applyBeanData,
		   getComponentChanges: getComponentChanges,
		   
		   getFormState: function(name){ 
			   var defered = null
			   if (!deferredformStates[name]) {
				   var defered = $q.defer()
				   deferredformStates[name] = defered;
			   } else {
				   defered = deferredformStates[name]
			   }

			   if (formStates[name]) {
				   defered.resolve(formStates[name]); // then handlers are called even if they are applied after it is resolved
			   }			   
			   return defered.promise;
		   },

		   clearformState: function(formName) {
			   delete formStates[formName];
		   },

		   initFormState: function(formName, beanDatas, formProperties) {
			   var state = formStates[formName];
			   // if the form is already initialized or if the beanDatas are not given, return that 
			   if (state != null || !beanDatas) return state; 

			   // init all the objects for the beans.
			   var model = {};
			   var api = {};
			   var layout = {};

			   state = formStates[formName] = { model: model, api: api, layout: layout,
					   style: {                         
						   left: "0px",
						   top: "0px",
						   minWidth : formProperties.size.width + "px",
						   minHeight : formProperties.size.height + "px",
						   right: "0px",
						   bottom: "0px",
						   border: formProperties.border},
						   properties: formProperties};

			   for(var beanName in beanDatas) {
				   // initialize with design nara
				   model[beanName] = {};
				   api[beanName] = {};
				   layout[beanName] = { position: 'absolute' }
				   
				   var newBeanConversionInfo = beanDatas[beanName].conversions;
				   var beanConversionInfo = newBeanConversionInfo ? $utils.getOrCreateInDepthProperty(formStatesConversionInfo, formName, beanName) : undefined; // we could do a get instead of undefined, but normally that value is not needed if the new conversion info is undefined
				   
				   applyBeanData(model[beanName], layout[beanName], beanDatas[beanName], formProperties.designSize, getChangeNotifier(formName, beanName), beanConversionInfo, newBeanConversionInfo)
			   }


			   $rootScope.updatingFormUrl = '';
			   return state;
		   },

		   getExecutor: function(formName) {
			   return {
				   on: function(beanName,eventName,property,args,rowId) {
					   // this is onaction, onfocuslost which is really configured in the html so it really 
					   // is something that goes to the server
					   var newargs = $utils.getEventArgs(args,eventName);
					   var data = {}
					   if (property) {
						   data[property] = formStates[formName].model[beanName][property];
					   }
					   var cmd = {cmd:'event',formname:formName,beanname:beanName,event:eventName,args:newargs,changes:data}
					   if (rowId) cmd.rowId = rowId
					   return getSession().sendDeferredMessage(cmd)
				   },
			   }
		   },

		   sendRequest: function(objToStringify) {
			   getSession().sendMessageObject(objToStringify);
		   },

		   // used by form template js
		   sendChanges: sendChanges,

		   // for example components that use nested elements/components such as portal can give here the new value
		   // based on the way they feed the model to child components - so they can use other objects then server known models
		   pushDPChange: function(formname, beanname, property, componentModel, rowId) {
			   var changes = {}

			   if (componentModel) {
				   // probably a nested component (inside another component); the component might even be linked to a different foundset
				   //changes[property] = $sabloUtils.convertClientObject(componentModel[property]);
				   if (rowId){
					   changes.rowId = rowId;
				   } else if (componentModel.rowId) {
					   changes.rowId = componentModel.rowId;
				   }
			   }
			   // default model, simple direct form child component
			   var conversionInfo = (formStatesConversionInfo[formname] ? formStatesConversionInfo[formname][beanname] : undefined);
			   if (conversionInfo && conversionInfo[property]){
				   changes[property] = $sabloConverters.convertFromClientToServer(formStates[formname].model[beanname][property], conversionInfo[property], undefined);
			   }else{
				   changes[property] = formStates[formname].model[beanname][property]
			   }
			   getSession().sendMessageObject({cmd:'svypush',formname:formname,beanname:beanname,property:property,changes:changes})
		   },

		   filterList: function(formname,beanname,property,filter)  {
			   var deferred = $q.defer();
			   deferredProperties[formname + "_" + beanname + "_" + property] = deferred;
			   getSession().sendMessageObject({cmd:'valuelistfilter',formname:formname,beanname:beanname,property:property,filter:filter})
			   return deferred.promise;
		   },

		   callService: function(serviceName, methodName, argsObject, async) {
			   return getSession().callService(serviceName, methodName, argsObject, async)
		   }
	   }
}).directive('svyLayoutUpdate', function($servoyInternal,$window,$timeout) {
    return {
      restrict: 'A', // only activate on element attribute
      controller: function($scope, $element, $attrs) {
    	  var compModel;
    	  if($attrs['svyLayoutUpdate'].length == 0) {
    		  compModel = $scope.formProperties;
    	  } else {
    		  compModel = $scope.model[$attrs['svyLayoutUpdate']];
    	  }
    	  if (!compModel) return; // not found, maybe a missing bean

    	  if(($attrs['svyLayoutUpdate'].length == 0) || (compModel.anchors !== undefined)) {
        	  var resizeTimeoutID = null;
        	  $window.addEventListener('resize',function() { 
        		  if(resizeTimeoutID) $timeout.cancel(resizeTimeoutID);
        		  resizeTimeoutID = $timeout( function() {
        			  if(compModel.location) {
        				  compModel.location.x = $element.prop('offsetLeft');
        				  compModel.location.y = $element.prop('offsetTop');
        			  }
        			  if(compModel.size) {
            			  compModel.size.width = $element.prop('offsetWidth');
            			  compModel.size.height = $element.prop('offsetHeight');  
        			  }
        		  }, 1000);
        	  });
    	  }
      }
    };   
}).value("$solutionSettings",  {
	mainForm: {},
	navigatorForm: {width:0},
	solutionTitle: "",
	defaultNavigatorState: {max:0,currentIdx:0,form:'<none>'},
	styleSheetPath: undefined,
	ltrOrientation : true
}).controller("MainController", function($scope, $solutionSettings, $servoyInternal, $windowService,$rootScope,webStorage) {
	$servoyInternal.connect();
	$scope.solutionSettings = $solutionSettings;
	$scope.getMainFormUrl = function() {
		return $solutionSettings.mainForm.templateURL?$windowService.getFormUrl($solutionSettings.mainForm.templateURL):"";
	}
	$scope.getNavigatorFormUrl = function() {
		if ( $solutionSettings.navigatorForm.templateURL && $solutionSettings.navigatorForm.templateURL.lastIndexOf("default_navigator_container.html") == -1) {
			return $windowService.getFormUrl($solutionSettings.navigatorForm.templateURL);
		}
		return $solutionSettings.navigatorForm.templateURL;
	}	
	$rootScope.updatingFormUrl = '';
	
	$scope.getSessionProblemView = function(){
		if($solutionSettings.noLicense) return $solutionSettings.noLicense.viewUrl;
		if($solutionSettings.maintenanceMode) return $solutionSettings.maintenanceMode.viewUrl;
		if($solutionSettings.sessionExpired) return $solutionSettings.sessionExpired.viewUrl;
		if($solutionSettings.internalServerError) return $solutionSettings.internalServerError.viewUrl;		
		return null;
	}
	
	$scope.getNavigatorStyle = function(ltrOrientation) {
		var orientationVar = ltrOrientation ? 'left':'right';
		var style = {'position':'absolute','top':'0px','bottom':'0px','width':$solutionSettings.navigatorForm.size.width+'px'}
		style[orientationVar] = '0px';
		return style;
	}
	$scope.getFormStyle = function(ltrOrientation) {
		var orientationVar1 = ltrOrientation ? 'right':'left';
		var orientationVar2 = ltrOrientation ? 'left':'right';
		var style = {'position':'absolute','top':'0px','bottom':'0px'}
		style[orientationVar1] = '0px';
		style[orientationVar2] = $solutionSettings.navigatorForm.size.width+'px';
		return style;
	}
}).controller("NoLicenseController",['$scope','$solutionSettings','$timeout','$window' ,function($scope, $solutionSettings,$timeout,$window) {
	
	$scope.redirectUrl = $solutionSettings.noLicense.redirectUrl;
	
	if($solutionSettings.noLicense.redirectTimeout >=0){
		$timeout(function(){			
			$window.location = $solutionSettings.noLicense.redirectUrl;
		},$solutionSettings.noLicense.redirectTimeout*1000)
	}
}]).controller("SessionExpiredController",['$scope','$solutionSettings',function($scope, $solutionSettings) {
	
	$scope.redirectUrl = $solutionSettings.sessionExpired.redirectUrl;

}])
.controller("InternalServerErrorController",['$scope','$solutionSettings',function($scope, $solutionSettings) {
	
	$scope.error = $solutionSettings.internalServerError

}])
.controller("MaintenanceModeController",['$scope','$solutionSettings','$timeout','$window' ,function($scope, $solutionSettings,$timeout,$window) {
	
	$scope.redirectUrl = $solutionSettings.maintenanceMode.redirectUrl;
	
	if($solutionSettings.maintenanceMode.redirectTimeout >=0){
		$timeout(function(){			
			$window.location = $solutionSettings.maintenanceMode.redirectUrl;
		},$solutionSettings.maintenanceMode.redirectTimeout*1000)
	}
}])
.controller("LoginController", function($scope, $modalInstance, $servoyInternal, $rootScope, webStorage) {
	$scope.model = {'remember' : true };
	$scope.doLogin = function() {
		var promise = $servoyInternal.callService("applicationServerService", "login", {'username' : $scope.model.username, 'password' : $scope.model.password, 'remember': $scope.model.remember}, false);
		promise.then(function(ok) {
			if(ok) {
				if(ok.username) webStorage.local.add('servoy_username', ok.username);
				if(ok.password) webStorage.local.add('servoy_password', ok.password);
				$modalInstance.close(ok);
			} else {
				$scope.model.message = 'Invalid username or password, try again';
			}
    	})
	}	
})
.factory('$sessionService',['$solutionSettings','$window','$rootScope',function($solutionSettings,$window,$rootScope){
	
	return {
		expireSession : function (sessionExpired){
			$rootScope.$apply(function(){
				var exp = { 
			    	viewUrl: 'templates/sessionExpiredView.html',
			    	redirectUrl : $window.location.href
			    }
			    if(sessionExpired.viewUrl)	exp.viewUrl= sessionExpired.viewUrl;
			    
			    $solutionSettings.sessionExpired = exp;
			})
		},
		setNoLicense: function (noLicense){
			$rootScope.$apply(function(){
				var noLic = {
						viewUrl : 'templates/serverTooBusyView.html',
						redirectUrl : $window.location.href,
						redirectTimeout : 0
				}
	        	if(noLicense.viewUrl) noLic.viewUrl = noLicense.viewUrl 
	        	if(noLicense.redirectUrl) noLic.redirectUrl = noLicense.redirectUrl;
	        	if(noLicense.redirectTimeout) noLic.redirectTimeout = noLicense.redirectTimeout;
	        	
	        	$solutionSettings.noLicense = noLic;
			})
		},
		setMaintenanceMode: function (maintenanceMode){
			$rootScope.$apply(function(){
				var ment = {
						viewUrl : 'templates/maintenanceView.html',
						redirectUrl : $window.location.href,
						redirectTimeout : 0
				}
	        	if(maintenanceMode.viewUrl) ment.viewUrl = mentenanceMode.viewUrl 
	        	if(msg.maintenanceMode.redirectUrl)	ment.redirectUrl = maintenanceMode.redirectUrl;
	        	if(msg.maintenanceMode.redirectTimeout)	ment.redirectTimeout = maintenanceMode.redirectTimeout;
	
	        	$solutionSettings.maintenanceMode = ment;
			})
		},
		setInternalServerError: function(internalServerError){
			$rootScope.$apply(function(){
				var error = {viewUrl:'templates/serverInternalErrorView.html'}
				if(internalServerError.viewUrl)  error.viewUrl = internalServerError.viewUrl;
				if(internalServerError.stack) error.stack = internalServerError.stack;
				
				$solutionSettings.internalServerError = error;					
			})
		}
	}
}])
.factory("$applicationService",['$window','$timeout','webStorage','$modal', '$servoyInternal', function($window,$timeout,webStorage,$modal,$servoyInternal) {
	var showDefaultLoginWindow = function() {
			$modal.open({
        	  templateUrl: '/templates/login.html',
         	  controller: 'LoginController',
       	      windowClass: 'login-window',
      	      backdrop: 'static',
      	      keyboard: false
			});				
		}
	return {
		getUserProperty: function(key) {
			var json = webStorage.local.get("userProperties");
			if (json) {
				return JSON.parse(json)[key];
			}
			return null;
		},
		setUserProperty: function(key,value) {
			var obj = {}
			var json = webStorage.local.get("userProperties");
			if (json) {
				obj = JSON.parse(json);
			}
			if (value == null) delete obj[key]
			else obj[key] = value;
			webStorage.local.add("userProperties", JSON.stringify(obj))
		},
		getUserPropertyNames: function() {
			var json = webStorage.local.get("userProperties");
			if (json) {
				return Object.getOwnPropertyNames(JSON.parse(json));
			}
			return [];
		},
		showUrl:function(url,target,targetOptions,timeout){
		    	 if(!target) target ='_blank';
		    	 if(!timeout) timeout = 0;	    	 
			    	$timeout(function(){
			    		$window.open(url,target,targetOptions)
			    	},timeout*1000)	    	
		},
		setStatusText:function(text){
			$window.status = text;  	
		},
		getScreenSize:function() {
			  if ($window.screen) {
				  return{width: $window.screen.width, height: $window.screen.height, orientation:$window.orientation};
			  }
			return null;
		},
		getLocation:function() {
			return $window.location.href;
		},
		getUserAgentAndPlatform:function() {
			return {userAgent:$window.navigator.userAgent,platform:$window.navigator.platform};
		},
		getUtcOffsetsAndLocale:function() {
			// TODO this navigator.language is not really the correct language (its the browser installed language not the preferred language thats in the accept header)
			// But chrome also doesn't send that header in the websocket request so we also can't get it from there: https://code.google.com/p/chromium/issues/detail?id=174956
			// a hack could be: http://stackoverflow.com/questions/1043339/javascript-for-detecting-browser-language-preference
			return {locale:$window.navigator.language?$window.navigator.language:$window.navigator.browserLanguage,utcOffset:(new Date(new Date().getFullYear(), 0, 1, 0, 0, 0, 0).getTimezoneOffset() / -60),utcDstOffset:(new Date(new Date().getFullYear(), 6, 1, 0, 0, 0, 0).getTimezoneOffset() / -60)};
		},
		showInfoPanel: function(url,w,h,t,closeText)
		{
			var infoPanel=document.createElement("div");
			infoPanel.innerHTML="<iframe marginheight=0 marginwidth=0 scrolling=no frameborder=0 src='"+url+"' width='100%' height='"+(h-25)+"'></iframe><br><a href='#' onClick='javascript:document.getElementById(\"infoPanel\").style.display=\"none\";return false;'>"+closeText+"</a>";
			infoPanel.style.zIndex="2147483647";  
			infoPanel.id="infoPanel"; 
			var width = window.innerWidth || document.body.offsetWidth; 
			infoPanel.style.position = "absolute"; 
			infoPanel.style.left = ((width - w) - 30) + "px";  
			infoPanel.style.top = "10px"; 
			infoPanel.style.height= h+"px"; 
			infoPanel.style.width= w+"px";
			document.body.appendChild(infoPanel);
			setTimeout('document.getElementById(\"infoPanel\").style.display=\"none\"',t);
		},
		showDefaultLogin: function() {
			if(webStorage.local.get('servoy_username') && webStorage.local.get('servoy_password')) {
				var promise = $servoyInternal.callService("applicationServerService", "login", {'username' : webStorage.local.get('servoy_username'), 'password' : webStorage.local.get('servoy_password'), 'encrypted': true}, false);
				promise.then(function(ok) {
					if(!ok) {
						webStorage.local.remove('servoy_username');
						webStorage.local.remove('servoy_password');
						showDefaultLoginWindow();
					}
		    	})				
			} else {
				showDefaultLoginWindow();
			}		
		}
	}
	
}])
.run(function($window, $servoyInternal) {
	$window.executeInlineScript = function(formname, script, params) {
		$servoyInternal.callService("formService", "executeInlineScript", {'formname' : formname, 'script' : script, 'params' : params},true)
	}
})