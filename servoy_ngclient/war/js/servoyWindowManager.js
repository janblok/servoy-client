angular.module('servoyWindowManager',['sabloApp'])	// TODO Refactor so that window is a component with handlers
.config(['$locationProvider', function($locationProvider) {
    $locationProvider.html5Mode(true);
}])
.factory('$servoyWindowManager', ['$timeout', '$rootScope','$http','$q','$templateCache','$injector','$controller','$compile','WindowType',
                                  function($timeout, $rootScope,$http,$q ,$templateCache,$injector,$controller,$compile,WindowType) {
	var WM = new WindowManager();
	var winInstances = {}
	return {
		BSWindowManager: WM,
		instances: winInstances,
		open : function (windowOptions) {
			var dialogOpenedDeferred = $q.defer();

			//prepare an instance of a window to be injected into controllers and returned to a caller
			var windowInstance =windowOptions.windowInstance;

			//merge and clean up options
			windowOptions.resolve = windowOptions.resolve || {};
			//verify options
			if (!windowOptions.template && !windowOptions.templateUrl) {
				throw new Error('One of template or templateUrl options is required.');
			}

			// wait for templateURL and resolve options
			var templateAndResolvePromise =
				$q.all([getTemplatePromise(windowOptions)].concat(getResolvePromises(windowOptions.resolve)));

			templateAndResolvePromise.then(function(tplAndVars){
				//initialize dialog scope and controller
				var windowScope = (windowOptions.scope || $rootScope).$new();
				windowScope.$close = windowInstance.close;
				windowScope.$dismiss = windowInstance.dismiss;
				windowInstance.$scope = windowScope;

				var ctrlLocals = {};
				var resolveIter = 1;

				//controllers
				if (windowOptions.controller) {
					ctrlLocals.$scope = windowScope;
					ctrlLocals.windowInstance = windowInstance;
					angular.forEach(windowOptions.resolve, function (value, key) {
						ctrlLocals[key] = tplAndVars[resolveIter++];
					});

					$controller(windowOptions.controller, ctrlLocals);
				}
				var isModal = (ctrlLocals.windowInstance.type == WindowType.MODAL_DIALOG);

				//resolve initial bounds
				var location = null;
				var size = null;
				if(windowInstance.initialBounds){
					var bounds = windowInstance.initialBounds;
					location = {x:bounds.x,
							y:bounds.y};
					size = {width:bounds.width,height:bounds.height}
				}
				if(windowInstance.location){
					location = windowInstance.location;
				}
				if(windowInstance.size){
					size = windowInstance.size;
				}
				//-1 means default size and location(center)
		        var formSize = size;
		        if (!formSize || (formSize.width === -1 && formSize.height === -1))
		          formSize = windowInstance.form.size;

				if(!location || (location.x <0 && location.y <0)) location = centerWindow(formSize);
				if(!size || size.width<0 || size.height<0) size =null;

				if (size)
				{
					// dialog shouldn't be bigger than viewport
					var browserWindow =  $(window);
					if (size.width && size.width > browserWindow.width())
					{
						size.width = browserWindow.width();
					}
					if (size.height && size.height > browserWindow.height())
					{
						size.height = browserWindow.height();
					}
				}
				//convert servoy x,y to library top , left
				var loc = {left:location.x,top:location.y}

				var compiledWin = $compile( tplAndVars[0])(windowScope);
				//create the bs window instance
				var win = WM.createWindow({
					id:windowInstance.name,
					fromElement: compiledWin,
					title: "Loading...",
					resizable:!!windowInstance.resizable,
					location:loc,
					size:size,
					isModal:isModal
				})

				//set servoy managed bootstrap-window Instance
				windowInstance.bsWindowInstance =win;
			},function resolveError(reason) {
				dialogOpenedDeferred.reject(reason);
			});

			//notify dialog opened or error
			templateAndResolvePromise.then(function () {
				dialogOpenedDeferred.resolve(true);
			}, function () {
				dialogOpenedDeferred.reject(false);
			});

			return dialogOpenedDeferred.promise;
		},
		destroyAllDialogs: function(){
			for(var dialog in this.instances)
			{
				if (this.instances[dialog] && this.instances[dialog].bsWindowInstance) this.instances[dialog].hide();
			}
			instances = {};
		}
	}


//	utiliy functions
	function getTemplatePromise(options) {
		return options.template ? $q.when(options.template) :
			$http.get(options.templateUrl, {cache: $templateCache}).then(function (result) {
				return result.data;
			});
	}

	function getResolvePromises(resolves) {
		var promisesArr = [];
		angular.forEach(resolves, function (value) {
			if (angular.isFunction(value) || angular.isArray(value)) {
				promisesArr.push($q.when($injector.invoke(value)));
			}
		});
		return promisesArr;
	}
	function centerWindow(formSize){
		var body = $('body');
		var browserWindow =  $(window);
		var top, left,
		bodyTop = parseInt(body.position().top, 10) + parseInt(body.css('paddingTop'), 10);
		left = (browserWindow.width() / 2) - (formSize.width / 2);
		top = (browserWindow.height() / 2) - (formSize.height / 2);
		if (top < bodyTop) {
			top = bodyTop;
		}
		if (left < 0) left = 0;
		if (top < 0) top = 0;
		return {x:left,y:top}
	};

}]).factory("$windowService", function($servoyWindowManager, $log, $rootScope, $solutionSettings, $window, $timeout, $formService, $sabloApplication, webStorage, WindowType,$servoyInternal,$templateCache, $location,$sabloLoadingIndicator) {
	var instances = $servoyWindowManager.instances;
	var formTemplateUrls = {};
	var storage = webStorage.local;
	var sol = $solutionSettings.solutionName+'.'

	// track main app window size change
	var mwResizeTimeoutID;
	$window.addEventListener('resize',function() {
		if(mwResizeTimeoutID) $timeout.cancel(mwResizeTimeoutID);
		mwResizeTimeoutID = $timeout( function() {
			$sabloApplication.callService("$windowService", "resize", {size:{width:$window.innerWidth,height:$window.innerHeight}},true);
		}, 500);
	});

	$window.addEventListener('unload', function(event) {
		$sabloApplication.disconnect();
	});

	function getFormUrl(formName) {
		var realFormUrl = formTemplateUrls[formName];
		if (realFormUrl == null || realFormUrl == undefined) {
			formTemplateUrls[formName] = "";
			$sabloApplication.callService("$windowService", "touchForm", {name:formName},true);
		}
		else if (realFormUrl.length == 0)
		{
			// waiting for updateForm to come
			return null;
		}
		return realFormUrl;
	}

	function prepareFormForUseInHiddenDiv(formName) {
		// the code should work even if we remove all the following timeouts, just execute directly - but these are mean as an optimization for the common cases
		$timeout(function() { // $timeout (a random number of multiple ones) are used to try to avoid cases in which a component already will use the template URL in which case we avoid loading it in hidden div unnecessarily
			$timeout(function() {
				$timeout(function() {
					$timeout(function() {
						if ($log.debugEnabled) $log.debug("svy * checking if prepareFormForUseInHiddenDiv needs to do something: " + formName);
						if (!$sabloApplication.hasResolvedFormState(formName)) {
							// in order to call web component API's for example we will create appropriate DOM and create the directives/scopes (but hidden) so that API call doesn't go to destroyed web component...
							var formURL = formTemplateUrls[formName];
							if (formURL && formURL.length > 0) $rootScope.updatingFormUrl = formURL; // normally the form URL is already there
							else {
								$log.error("svy * Trying to reload hidden form, but rel URL is empty; forcing reload... " + formName);
								$rootScope.updatingFormUrl = getFormUrl(formName);
							}
							if ($log.debugEnabled) $log.debug("svy * $rootScope.updatingFormUrl = " + $rootScope.updatingFormUrl + " [prepareFormForUseInHiddenDiv - " + formName + "]");
							$rootScope.updatingFormName = formName;
						}
					}, 0);
				}, 0);
			}, 0);
		}, 0);
	}

	$sabloApplication.contributeFormResolver({

		// makes sure the given form is prepared (so DOM/directives are ready for use, not necessarily with initial data)
		prepareUnresolvedFormForUse: prepareFormForUseInHiddenDiv

	});

	$rootScope.$watch(function () { return $location.url(); }, function (newURL, oldURL) {
	    if (newURL != oldURL) {
	        var formName =  $location.search().f;
	        if (formName && formName != $solutionSettings.mainForm.name )
	        {
	        	$formService.goToForm(formName);
	        }
	    }
	});

	return {
		create: function (name,type){
			// dispose old one
			if(instances[name]){

			}
			if(!instances[name]){
				var win =
				{name:name,
						type:type,
						title:'',
						opacity:1,
						undecorated:false,
						bsWindowInstance:null,  // bootstrap-window instance , available only after creation
						hide: function (result) {
							if(win.bsWindowInstance) win.bsWindowInstance.close();
							if(!this.storeBounds){
								delete this.location;
								delete this.size;
							}
							if (win.$scope) win.$scope.$destroy();
						},
						setLocation:function(location){
							this.location = location;
							if(win.bsWindowInstance){
								win.bsWindowInstance.$el.css('left',this.location.x+'px');
								win.bsWindowInstance.$el.css('top',this.location.y+'px');
							}
							if(this.storeBounds) storage.add(sol+name+'.storedBounds.location',location)
						},
						setSize:function(size){
							this.size = size;
							if(win.bsWindowInstance){
								win.bsWindowInstance.setSize(size);
							}
							if(this.storeBounds) storage.add(sol+name+'.storedBounds.size',size)
						},
						getSize: function(){
							return win.size;
						},
						onResize:function($event,size){
							win.size = size;
							if(win.storeBounds) storage.add(sol+name+'.storedBounds.size',size)
							$sabloApplication.callService("$windowService", "resize", {name:win.name,size:win.size},true);
						},
						onMove:function($event,location){
							win.location = {x:location.left,y:location.top};
							if(win.storeBounds) storage.add(sol+name+'.storedBounds.location',win.location)
							$sabloApplication.callService("$windowService", "move", {name:win.name,location:win.location},true);
						},
						toFront:function(){
							$servoyWindowManager.BSWindowManager.setFocused(this.bsWindowInstance)
						},
						toBack:function(){
							$servoyWindowManager.BSWindowManager.sendToBack(this.bsWindowInstance)
						},
						clearBounds: function(){
							storage.remove(sol+name+'.storedBounds.location')
							storage.remove(sol+name+'.storedBounds.size')
						}
				};

				instances[name] = win;
				return win;
			}

		},
		show: function(name,form, title) {
			var instance = instances[name];
			if (instance) {
				if(instance.bsWindowInstance){
					// do nothing switchform will switch the form.
					return;
				}
				if($(document).find('[svy-window]').length < 1) {
					$("#mainForm").trigger("disableTabseq");
				}
				instance.title = title;
				if(instance.storeBounds){
					instance.size = storage.get(sol+name+'.storedBounds.size')
					instance.location =  storage.get(sol+name+'.storedBounds.location')
				}
				$servoyWindowManager.open({
					animation: false,
					templateUrl: "templates/dialog.html",
					controller: "DialogInstanceCtrl",
					windowClass: "tester",
					windowInstance:instance
				}).then(function(){
					// test if it is modal dialog, then the request blocks on the server and we should hide the loading.
					if (instance.type == 1 && $sabloLoadingIndicator.isShowing()) {
						instance.loadingIndicatorIsHidden = true;
						$sabloLoadingIndicator.hideLoading();
					}
					instance.bsWindowInstance.$el.on('bswin.resize',instance.onResize)
					instance.bsWindowInstance.$el.on('bswin.move',instance.onMove)
					instance.bsWindowInstance.$el.on("setActive", function(ev, active) {
						$(ev.currentTarget).trigger(active ? "enableTabseq" : "disableTabseq");
					});
					instance.bsWindowInstance.setActive(true);
				},function(reason){
					throw reason;
				})
				if(instance.form.name != form) throw 'switchform should set the instances state before showing it'
			}
			else {
				$log.error("Trying to show window with name: '" + name + "' which is not created.");
			}
		},
		hide:function(name){
			var instance = instances[name];
			if (instance) {
				if (instance.loadingIndicatorIsHidden) {
					delete instance.loadingIndicatorIsHidden;
					$sabloLoadingIndicator.showLoading();
				}
				instance.hide();
				if($(document).find('[svy-window]').length < 2) {
					$("#mainForm").trigger("enableTabseq");
				}
			}else {
				$log.error("Trying to hide window : '" + name + "' which is not created. If this is due to a developer form change/save while dialog is open in client it is expected.");
			}
		},
		destroy: function(name) {
			var instance = instances[name];
			if (instance) {
				delete instances[name];
			} else {
				$log.error("Trying to destroy window : '" + name + "' which is not created. If this is due to a developer form change/save while dialog is open in client it is expected.");
			}
		},
		switchForm: function(name,form,navigatorForm) {
			// if first show of this form in browser window then request initial data (dataproviders and such)
			$formService.formWillShow(form.name, false); // false because form was already made visible server-side
			if (navigatorForm && navigatorForm.name && navigatorForm.name.lastIndexOf("default_navigator_container.html") == -1) {
				// if first show of this form in browser window then request initial data (dataproviders and such)
				$formService.formWillShow(navigatorForm.name, false); // false because form was already made visible server-side
			}

			if(instances[name] && instances[name].type != WindowType.WINDOW) {
				instances[name].form = form;
				instances[name].navigatorForm = navigatorForm;
			}
			else if ($solutionSettings.windowName == name) { // main window form switch
				$solutionSettings.mainForm = form;
				$solutionSettings.navigatorForm = navigatorForm;
				$location.url($location.path() + '?f=' + form.name);
			}
			if (!$rootScope.$$phase) $rootScope.$digest();
		},
		setTitle: function(name,title) {
			if(instances[name] && instances[name].type!= WindowType.WINDOW){
				instances[name].title =title;
			}else{
				$solutionSettings.solutionTitle = title;
				if (!$rootScope.$$phase) $rootScope.$digest();
			}
		},
		setInitialBounds:function(name,initialBounds){
			if(instances[name]){
				instances[name].initialBounds = initialBounds;
			}
		},
		setStoreBounds:function(name,storeBounds){
			if(instances[name]){
				instances[name].storeBounds = storeBounds;
			}
		},
		resetBounds:function(name){
			if(instances[name]){
				instances[name].storeBounds = false;
				instances[name].clearBounds()
			}
		},
		setLocation:function(name,location){
			if(instances[name]){
				instances[name].setLocation(location);
			}
		},
		setSize:function(name,size){
			if(instances[name]){
				instances[name].setSize(size);
			}
		},
		getSize:function(name){
			if(instances[name] && instances[name].bsWindowInstance){
				return instances[name].getSize();
			}
			else {
				return {width:$window.innerWidth,height:$window.innerHeight}
			}
		},
		setUndecorated:function(name,undecorated){
			if(instances[name]){
				instances[name].undecorated = undecorated;
			}
		},
		setOpacity:function(name,opacity){
			if(instances[name]){
				instances[name].opacity = opacity;
			}
		},
		setResizable:function(name,resizable){
			if(instances[name]){
				instances[name].resizable = resizable;
			}
		},
		setTransparent:function(name,transparent){
			if(instances[name]){
				instances[name].transparent = transparent;
			}
		},
		toFront:function(name){
			if(instances[name]){
				instances[name].toFront();
			}
		},
		toBack:function(name){
			if(instances[name]){
				instances[name].toBack();
			}
		},
		reload: function() {
			$window.location.reload(true);
		},
		updateController: function(formName,controllerCode, realFormUrl, forceLoad, html) {
			if ($log.debugEnabled) $log.debug("svy * updateController = " + formName + ", realFormUrl = " + realFormUrl);
			if (formTemplateUrls[formName] !== realFormUrl) {
				if (formTemplateUrls[formName])
				{
					$templateCache.remove(formTemplateUrls[formName]);
				}
				if (html) $templateCache.put(realFormUrl,html);
				var formState = $sabloApplication.getFormStateEvenIfNotYetResolved(formName);
				$sabloApplication.clearFormState(formName)
				eval(controllerCode);
				formTemplateUrls[formName] = realFormUrl;
				// if the form was already intialized and visible, then make sure it is initialized again.
				if (formState && formState.getScope != undefined)
				{
					$sabloApplication.getFormState(formName).then(function (formState) {
						if ($log.debugEnabled) $log.debug("svy * updateController; checking to see if requestInitialData is needed = " + formName + " (" + formState.initializing + ", " + formState.initialDataRequested + ")");
						if (formState.initializing && !formState.initialDataRequested) $servoyInternal.requestInitialData(formName, formState);
					});
				}
				// TODO can this be an else if the above if? will it always force load anyway?
				if(forceLoad) {
					$rootScope.updatingFormUrl = realFormUrl;
					$rootScope.updatingFormName = formName;
					if ($log.debugEnabled) $log.debug("svy * $rootScope.updatingFormUrl = " + $rootScope.updatingFormUrl + " [updateController FORCED - " + formName + "]");
				}
				if (!$rootScope.$$phase) $rootScope.$digest();
			} else if ($log.debugEnabled) {
				$log.warn("svy * updateController for form '" + formName + "' was ignored as the URLs are identical and we don't want to clear all kinds of states/caches without the form getting reloaded due to URL change");
			}
		},
		requireFormLoaded: function(formName) {
			// in case updateController was called for a form before with forceLoad == false, the form URL might not really be loaded by the bean that triggered it
			// because the bean changed it's mind, so when a new server side touchForm() comes for this form with forceLoad == true then we must make sure the
			// form URL is used to create the directives/DOM and be ready for use
			if ($log.debugEnabled) $log.debug("svy * requireFormLoaded: " + formName);
			prepareFormForUseInHiddenDiv(formName);
			if (!$rootScope.$$phase) $rootScope.$digest();
		},
		destroyController : function(formName){
			$sabloApplication.clearFormState(formName);
		},
		getFormUrl: getFormUrl
	}

}).value('WindowType',{
	DIALOG:0,
	MODAL_DIALOG:1,
	WINDOW:2
}).controller("DialogInstanceCtrl", function ($scope, windowInstance,$windowService, $servoyInternal,$sabloApplication,$formService) {

	// these scope variables can be accessed by child scopes
	// for example the default navigator watches 'win' to see if it changed the current form
	$scope.win =  windowInstance
	$scope.getFormUrl = function() {
		return $windowService.getFormUrl(windowInstance.form.name)
	}
	$scope.getNavigatorFormUrl = function() {
		if (windowInstance.navigatorForm.templateURL && windowInstance.navigatorForm.templateURL.lastIndexOf("default_navigator_container.html") == -1) {
			return $windowService.getFormUrl(windowInstance.navigatorForm.templateURL);
		}
		return windowInstance.navigatorForm.templateURL;
	}

	$scope.isUndecorated = function(){
		return $scope.win.undecorated || ($scope.win.opacity<1)
	}

	$scope.getBodySize = function(){
		var win = $scope.win;
		var width = win.size ? win.size.width:win.form.size.width;
		var height = win.form.size.height;
		if(!win.size && win.navigatorForm.size){
			width += win.navigatorForm.size.width;
		}
		return {'width':width+'px','height':height+'px'}
	}

	$formService.formWillShow(windowInstance.form.name, false);

	$scope.cancel = function () {
		var promise = $sabloApplication.callService("$windowService", "windowClosing", {window:windowInstance.name},false);
		// close is handled server side
//		promise.then(function(ok) {
//			if (ok) {
//				$windowService.hide(windowInstance.name);
//			}
//		})
	};
});
