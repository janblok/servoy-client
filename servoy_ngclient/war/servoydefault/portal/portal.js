angular.module('servoydefaultPortal',['sabloApp','servoy','ui.grid','ui.grid.selection','ui.grid.moveColumns','ui.grid.resizeColumns','ui.grid.infiniteScroll','ui.grid.cellNav'])
.directive('servoydefaultPortal', ["$sabloUtils", '$utils', '$foundsetTypeConstants', '$componentTypeConstants',
                                   '$timeout', '$solutionSettings', '$anchorConstants',
                                   'gridUtil','uiGridConstants','$scrollbarConstants',"uiGridMoveColumnService","$sce","$apifunctions","$log","$q", "$sabloApplication","$sabloConstants","$applicationService",
                                   '$svyProperties', '$sabloConstants','$window','i18nService',
                                   function($sabloUtils, $utils, $foundsetTypeConstants, $componentTypeConstants,
                                		   $timeout, $solutionSettings, $anchorConstants,
                                		   gridUtil, uiGridConstants, $scrollbarConstants, uiGridMoveColumnService, $sce, $apifunctions, $log, $q, $sabloApplication, $sabloConstants, $applicationService, $svyProperties, $sabloConstants,$window,i18nService) {
	return {
		restrict: 'E',
		scope: {
			model: "=svyModel",
			handlers: "=svyHandlers",
			api: "=svyApi",
			servoyApi: "=svyServoyapi"
		},
		link: function($scope, $element, $attrs) {
			// START TEST MODELS

			// rowProxyObjects[pk][elementIndex].
			//                                  .mergedCellModel  - is the actual cell element svyModel cache
			//                                  .cellApi          - is the actual cell element API cache
			//                                  .cellHandlers     - is the actual cell element handlers cache
			//                                  .cellServoyApi 	  - is the actual cell element Servoy Api cache
			// cellAPICaches[renderedRowIndex][elementIndex]       - is the actual cell element API cache
			// cellChangeNotifierCaches[renderedRowIndex][elementIndex]       - is cache of the $sabloConstants.modelChangeNotifier after cell cache is destroyed, so it can be reused
			var rowProxyObjects = {};
			var cellAPICaches = {};
			var cellChangeNotifierCaches = {};
			
			var locale = $sabloApplication.getLocale();
			if (locale.language) {
				i18nService.setCurrentLang(locale.language)
			}
			var pageSizeFactor = $applicationService.getUIProperty("ngClientPageSizeFactor");
			if (!pageSizeFactor || pageSizeFactor <= 1) pageSizeFactor = 2;
			$scope.pageSize = 25;
			$scope.transferFocus = function() {
				$($element.find(".ui-grid-focuser")[0]).focus();
			}
			
			var EMPTY =  {viewPort:{rows:[]}};

			$scope.foundset = EMPTY;
			var elements = $scope.model.childElements;
			var foundsetSetTimeOut1 = null;
			var foundsetSetTimeOut2 = null;
			$scope.$watch('model.relatedFoundset', function(newVal, oldVal) {
				if(foundsetSetTimeOut1) {
					$timeout.cancel(foundsetSetTimeOut1);
					foundsetSetTimeOut1 = null;
				}
				if (foundsetSetTimeOut2) {
					$timeout.cancel(foundsetSetTimeOut2);
					foundsetSetTimeOut2 = null;
				}
				if ($scope.foundset === EMPTY && newVal && newVal.viewPort && newVal.viewPort.size > 0){
					// this is the first time after a tab switch, there is data but the portal is not showing yet.
					foundsetSetTimeOut1 = $timeout(function() {
						if (foundsetSetTimeOut2) {
							$timeout.cancel(foundsetSetTimeOut2);
							foundsetSetTimeOut2 = null;
						}
						foundsetSetTimeOut2 = $timeout(function() {
							$scope.foundset = newVal;
						},1);
					},1);
				}
				else {
					$scope.foundset = newVal;
				}
			})

			function disposeOfRowProxies(rowProxy,renderedRowIndex) {
				if (renderedRowIndex !== undefined)
				{
					cellChangeNotifierCaches[renderedRowIndex] = []
				}
				for (var elIdx in rowProxy) {
					if (renderedRowIndex !== undefined && rowProxy[elIdx].mergedCellModel.hasOwnProperty($sabloConstants.modelChangeNotifier))
					{
						cellChangeNotifierCaches[renderedRowIndex][elIdx] = rowProxy[elIdx].mergedCellModel[$sabloConstants.modelChangeNotifier];
					}	
					if (rowProxy[elIdx].unwatchFuncs) {
						rowProxy[elIdx].unwatchFuncs.forEach(function (f) { f(); });
					}
				}
			}

			$scope.$watchCollection('model.childElements', function(newVal, oldVal) {
				elements = $scope.model.childElements;
				if (newVal != oldVal) {
					for(var i=0;i<oldVal.length;i++) {
						delete oldVal[i].model[$sabloConstants.modelChangeNotifier];
					}
					// either a component was added/removed/changed or the whole array changed
					// we can optimize this in the future but for now just dump all model/api/handlers for them to get auto recreated
					for (var someKey in rowProxyObjects)
					{
						var renderedRowIndex = undefined;
						if ($scope.foundset && $scope.foundset.viewPort && $scope.foundset.viewPort.rows) {
							for (var idx in $scope.foundset.viewPort.rows) 
							{
								if ($scope.foundset.viewPort.rows[idx][$foundsetTypeConstants.ROW_ID_COL_KEY] === someKey)
								{
									renderedRowIndex = idx;
									break;
								}
							}
						}
						disposeOfRowProxies(rowProxyObjects[someKey],renderedRowIndex);
					}	

					rowProxyObjects = {};
				}
			})

			$scope.rowHeight = $scope.model.rowHeight;

			var rowTemplate = ''
			var rowWidth = 0;

			$scope.columnDefinitions = [];

			function applyColumnTitle(columnDefinition, titleString) {
				if (!titleString || titleString.trim().indexOf('<html>') != 0) {
					columnDefinition.displayName = titleString;
					columnDefinition.displayNameHTML = undefined;
				} else {
					columnDefinition.displayName = ".";
					columnDefinition.displayNameHTML = $sce.trustAsHtml(titleString);
				}
			}

			function getColumnTitle(columnHeaderIdx, elementIdx) {
				var columnTitle = null;
				if ($scope.model.columnHeaders && columnHeaderIdx < $scope.model.columnHeaders.length)
				{
					columnTitle = $scope.model.columnHeaders[columnHeaderIdx];
				}
				if (!columnTitle) columnTitle = elements[elementIdx].model.text; // here we should not use 'text' of label and buttons as that doesn't have the same meaning as 'text' that maps to 'titleText' on other fields
				if (!columnTitle && elements[elementIdx].modelViewport && elements[elementIdx].modelViewport.length > 0) columnTitle = elements[elementIdx].modelViewport[0].text;
	//			if (!columnTitle) {
	//				// TODO use beautified dataProvider id or whatever other clients use as default, not directly the dataProvider id
	//				if (el.foundsetConfig && el.foundsetConfig.recordBasedProperties && el.foundsetConfig.recordBasedProperties.length > 0) {
	//					columnTitle = el.foundsetConfig.recordBasedProperties[0];
	//					if (columnTitle && columnTitle.indexOf('.') >= 0) {
	//						columnTitle = columnTitle.substring(columnTitle.lastIndexOf('.'));
	//					}
	//				}
	//				if (!columnTitle) columnTitle = "";
	//			}
				if (!columnTitle) columnTitle = "";
				return columnTitle;
			}

			var allowCellFocus = $scope.model.readOnlyMode !== undefined ? $scope.model.readOnlyMode : $applicationService.getUIProperty("ngClientOptimizedReadonlyMode");
			if (elements)
			{
				for (var idx = 0; idx < elements.length; idx++) {
					var el = elements[idx];
					var elY = el.model.location.y - $scope.model.location.y;
					var elX = el.model.location.x - $scope.model.location.x;
					var columnTitle = getColumnTitle(el.componentIndex ? el.componentIndex : idx, idx);
					var cellTemplate
					if(allowCellFocus && (el.componentDirectiveName === "servoydefault-textfield" || el.componentDirectiveName === "servoydefault-typeahead")) {
						var handlers = ""
						if (el.handlers.onActionMethodID) {
							handlers= ' svy-handlers="grid.appScope.cellHandlerWrapper(row, ' + idx + ')"'
						}
						cellTemplate = '<div class="ui-grid-cell-contents svy-textfield svy-field form-control input-sm" style="white-space:nowrap" cell-helper="grid.appScope.getMergedCellModel(row, ' + idx + ', rowRenderIndex)"' + handlers + ' tabIndex="-1"></div>';
					}
					else {
						var portal_svy_name = $element[0].getAttribute('data-svy-name');
						cellTemplate = '<' + el.componentDirectiveName + ' name="' + el.name
							+ '" svy-model="grid.appScope.getMergedCellModel(row, ' + idx
							+ ', rowRenderIndex)" svy-api="grid.appScope.cellApiWrapper(row, ' + idx
							+ ', rowRenderIndex, rowElementHelper)" svy-handlers="grid.appScope.cellHandlerWrapper(row, ' + idx
							+ ')" svy-servoyApi="grid.appScope.cellServoyApiWrapper(row, ' + idx + ')"';
						if (portal_svy_name) cellTemplate += " data-svy-name='" + portal_svy_name + "." + el.name + "'";
						cellTemplate += '/>';
					}
					if($scope.model.multiLine) {
						if($scope.rowHeight == undefined || (!$scope.model.rowHeight && ($scope.rowHeight < elY + el.model.size.height))) {
							$scope.rowHeight = $scope.model.rowHeight ? $scope.model.rowHeight : elY + el.model.size.height;
						}
						if (rowWidth < (elX + el.model.size.width) ) {
							rowWidth = elX + el.model.size.width;
						}
						rowTemplate = rowTemplate + '<div ng-style="grid.appScope.getMultilineComponentWrapperStyle(' + idx + ')" >' + cellTemplate + '</div>';
					}
					else {
						if($scope.rowHeight == undefined || ($scope.model.rowHeight == 0 && $scope.rowHeight < el.model.size.height)) {
							$scope.rowHeight = el.model.size.height;
						}
						var isResizable = ((el.model.anchors & $anchorConstants.EAST) != 0) && ((el.model.anchors & $anchorConstants.WEST) != 0)
						var isMovable = ((el.model.anchors & $anchorConstants.NORTH) === 0) || ((el.model.anchors & $anchorConstants.SOUTH) === 0)
						var isSortable = $scope.model.sortable && el.foundsetConfig.recordBasedProperties.length > 0; // TODO update uigrid when recordBasedProperties change
						var headerCellClass = null;
						var headerAction = null;
						if ($scope.model.headersClasses)
						{
							var headerIndex = el.componentIndex ? el.componentIndex : idx;
							headerCellClass = $scope.model.headersClasses[headerIndex];
						}
						if ($scope.model.headersAction) {
							var headerIndex = el.componentIndex ? el.componentIndex : idx;
							if ($scope.model.headersAction[headerIndex]) {
								isSortable = false;
								isMovable = false;
								var getHandler = function(callback) {
									return function(event) {
										var jsEvent = {svyType: 'JSEvent', formName: callback.form, elementName: callback.element};
										var argsWithEvent = [jsEvent];// append args
										$window.executeInlineScript(callback.formname,callback.script,argsWithEvent);
									}
								}
								headerAction = getHandler($scope.model.headersAction[headerIndex]);
							}
						}
						var newL = $scope.columnDefinitions.push({
							name:el.name,
							cellTemplate: cellTemplate,
							visible: el.model.visible,
							width: el.model.size.width,
							minWidth: el.model.size.width,
							cellEditableCondition: false,
							enableColumnMoving: isMovable,
							enableColumnResizing: isResizable,
							enableColumnMenu: isSortable,
							enableSorting:isSortable,
							enableHiding: false,
							allowCellFocus: allowCellFocus,
							headerCellClass: headerCellClass,
							svyHeaderAction: headerAction,
							type: "string", // just put a type here, we don't know the type and we dont use the edit feature of ui-grid
							svyColumnIndex: el.componentIndex ? el.componentIndex : idx
						});
						applyColumnTitle($scope.columnDefinitions[newL - 1], columnTitle);
						updateColumnDefinition($scope, idx);
					}
				}
			}


			if($scope.model.multiLine) {
				if ($scope.model.formview)
				{
					// needed for anchoring
					rowWidth = '100%';
				}
				$scope.columnDefinitions.push({
					width: rowWidth,
					cellTemplate: rowTemplate,
					name: "unique",
					cellEditableCondition: false,
					type: "object", // just put a type here to avoid a console warning, we don't know the type and we dont use the edit feature of ui-grid
					allowCellFocus: allowCellFocus
				});
			}

			function updateColumnDefinition(scope, idx) {
				scope.$watch('model.childElements[' + idx + '].model.visible', function (newVal, oldVal) {
					scope.columnDefinitions[idx].visible = scope.model.childElements[idx].model.visible;
				}, false);

				scope.$watch('model.childElements[' + idx + '].model.size.width', function (newVal, oldVal) {
					if(newVal !== oldVal)
					{
						scope.columnDefinitions[idx].width = scope.model.childElements[idx].model.size.width;
					}
				}, false);

				var columnHeaderIdx = scope.columnDefinitions[idx].svyColumnIndex ? scope.columnDefinitions[idx].svyColumnIndex : idx;
				// NOTE: below !scope.model.columnHeaders[columnHeaderIdx] is also true for !"" - in case html or tastrings are used for columnHeaders
				if (!scope.model.columnHeaders || columnHeaderIdx >= scope.model.columnHeaders.length || !scope.model.columnHeaders[columnHeaderIdx]) {
					// that means component titleText matters for headers
					function getTitleTextForWatch() {
						return getColumnTitle(columnHeaderIdx, idx);
					};

					scope.$watch(getTitleTextForWatch, function (newVal, oldVal) {
						if(newVal !== oldVal && scope.columnDefinitions[idx])
						{
							applyColumnTitle(scope.columnDefinitions[idx], getColumnTitle(columnHeaderIdx, idx));
							scope.gridApi.grid.buildColumns();
						}
					});
				}
			}

			function getOrCreateRowProxies(rowId) {
				var proxies = rowProxyObjects[rowId];
				if (!proxies) rowProxyObjects[rowId] = proxies = [];
				return proxies;
			}

			function getOrCreateElementProxies(rowId, elementIndex) {
				var rowProxies = getOrCreateRowProxies(rowId);
				if (!rowProxies[elementIndex]) rowProxies[elementIndex] = {};
				return rowProxies[elementIndex];
			}

			function removeRowAPICacheWatches(rowAPICache) {
				for (var unw in rowAPICache.unwatchFuncs) rowAPICache.unwatchFuncs[unw]();
			}

			var changinOrUnstableAPIPromise;
			var unstableAPIStabilizedListener;

			// api objects are kept linked to the real dom element (so linked to render index), so the the api can work with the correct "$element"
			// after the component contributed it (so try not to give the api object contributed by a web component to a different web component...)
			// hopefully ui-grid always uses the same directive/webcomponent at the same 'rowRenderIndex'
			function getOrCreateRowAPICache(ngGridRow, renderedRowIndex, rowElementHelper) {
				var rowAPICache = cellAPICaches[renderedRowIndex];
				if (!rowAPICache || (rowAPICache.rowElement !== rowElementHelper.getRowElement())) {
					// a new or different rowElement is being rendered at this renderRowIndex; cache value must change
					cellAPICaches[renderedRowIndex] = rowAPICache = [];
					rowAPICache.unwatchFuncs = [];
					rowAPICache.rowElement = rowElementHelper.getRowElement();

					rowAPICache.rowElement.on('$destroy', function () {
						removeRowAPICacheWatches(rowAPICache);
						if (cellAPICaches[renderedRowIndex] && cellAPICaches[renderedRowIndex].rowElement == rowAPICache.rowElement) delete cellAPICaches[renderedRowIndex];
						// is .off needed for $destroy?
					});
				}

				// API calls that need scroll need to know when the API <-> rowId relation is stable enough because of a bug in ngGrid that doesn't keep for example focus
				// on the correct row when scrolling; unfortunately the scrolled promises returned by ui-grid api are not that useful as they trigger too early, before data - row
				// mapping stabilizes
				if (rowAPICache.rowId !== ngGridRow.entity[$foundsetTypeConstants.ROW_ID_COL_KEY]) {
					if (changinOrUnstableAPIPromise) $timeout.cancel(changinOrUnstableAPIPromise);
					changinOrUnstableAPIPromise = $timeout(function() {
						changinOrUnstableAPIPromise = undefined;
						if (unstableAPIStabilizedListener) unstableAPIStabilizedListener();
					}, 1000); // is 1000 ms too much or too little? didn't find a way to avoid this as after scroll ui-grid still decides sometimes to scroll a bit more
				}

				rowAPICache.rowId = ngGridRow.entity[$foundsetTypeConstants.ROW_ID_COL_KEY];
				return rowAPICache;
			}

			var rowCache = {};
			function rowIdToViewportRelativeRowIndex(rowId) {
				var result = rowCache[rowId];
				var rows = $scope.foundset.viewPort.rows;
				if (result === undefined || !rows[result] || rows[result][$foundsetTypeConstants.ROW_ID_COL_KEY] !== rowId) {
					rowCache = {};
					for (var i = rows.length - 1; i >= 0; i--)
						rowCache[rows[i][$foundsetTypeConstants.ROW_ID_COL_KEY]] = i;
					result = rowCache[rowId];
				}
				if (result === undefined) result = -1;
				return result;
			}

			function rowIdToAbsoluteRowIndex(rowId) {
				return viewPortToAbsolute(rowIdToViewportRelativeRowIndex(rowId));
			}

			function viewPortToAbsolute(rowIndex) {
				return rowIndex >= 0 ? rowIndex + $scope.foundset.viewPort.startIndex : -1;
			}

			function absoluteToViewPort(absoluteRowIndex) {
				return absoluteRowIndex - $scope.foundset.viewPort.startIndex;
			}

			function absoluteRowIndexToRowId(absoluteRowIndex) {
				return $scope.foundset.viewPort.rows[absoluteToViewPort(absoluteRowIndex)][$foundsetTypeConstants.ROW_ID_COL_KEY];
			}

			function isRowIndexSelected(rowIndex) {
				return $scope.foundset.selectedRowIndexes.indexOf(rowIndex) >= 0;
			}

			function isInViewPort(absoluteRowIndex) {
				return (absoluteRowIndex >= $scope.foundset.viewPort.startIndex && absoluteRowIndex < ($scope.foundset.viewPort.startIndex + $scope.foundset.viewPort.size));
			}

			$scope.getMultilineComponentWrapperStyle = function(elementIndex) {
				var elModel = elements[elementIndex].model;
				var containerModel = $scope.model;
				var elLayout = {position: 'absolute'};

				if(elModel.anchors && $solutionSettings.enableAnchoring) {
					var anchoredTop = (elModel.anchors & $anchorConstants.NORTH) != 0; // north
					var anchoredRight = (elModel.anchors & $anchorConstants.EAST) != 0; // east
					var anchoredBottom = (elModel.anchors & $anchorConstants.SOUTH) != 0; // south
					var anchoredLeft = (elModel.anchors & $anchorConstants.WEST) != 0; //west

					if (!anchoredLeft && !anchoredRight) anchoredLeft = true;
					if (!anchoredTop && !anchoredBottom) anchoredTop = true;

					if (anchoredTop)
					{
						elLayout.top = elModel.location.y + 'px';
					}

					if (anchoredBottom)
					{
						elLayout.bottom = containerModel.size.height - elModel.location.y - elModel.size.height + "px";
					}

					if (!anchoredTop || !anchoredBottom) elLayout.height = elModel.size.height + 'px';

					if (anchoredLeft)
					{
						if ($solutionSettings.ltrOrientation)
						{
							elLayout.left =  elModel.location.x + 'px';
						}
						else
						{
							elLayout.right =  elModel.location.x + 'px';
						}
					}

					if (anchoredRight)
					{
						if ($solutionSettings.ltrOrientation)
						{
							elLayout.right = (containerModel.size.width - elModel.location.x - elModel.size.width) + "px";
						}
						else
						{
							elLayout.left = (containerModel.size.width - elModel.location.x - elModel.size.width) + "px";
						}
					}

					if (!anchoredLeft || !anchoredRight) elLayout.width = elModel.size.width + 'px';
				}
				else {
					if($solutionSettings.ltrOrientation)
					{
						elLayout.left = (elModel.location.x - $scope.model.location.x) + 'px';
					}
					else
					{
						elLayout.right = (elModel.location.x - $scope.model.location.x) + 'px';
					}
					elLayout.top = (elModel.location.y - $scope.model.location.y) + 'px';
					elLayout.width = elModel.size.width + 'px';
					elLayout.height = elModel.size.height + 'px';
				}

				if (typeof elModel.visible !== "undefined" && !elModel.visible) elLayout.display = 'none'; // undefined is considered visible by default

				return elLayout;
			}

			// merges component model and modelViewport (for record dependent properties like dataprovider/tagstring/...) the cell's element's model
			$scope.getMergedCellModel = function(ngGridRow, elementIndex, renderedRowIndex) {
				// TODO - can we avoid using ngGrid undocumented "row.entity"? that is what ngGrid uses internally as model for default cell templates...
				var rowId = ngGridRow.entity[$foundsetTypeConstants.ROW_ID_COL_KEY];

				var relativeRowIndex = rowIdToViewportRelativeRowIndex(rowId);
				if(relativeRowIndex < 0) {
					return {}
				}

				var cellProxies = getOrCreateElementProxies(rowId, elementIndex);
				var cellModel = cellProxies.mergedCellModel;

				if (!cellModel) {
					var element = elements[elementIndex];

					function CellData() {
					}
					CellData.prototype = element.model;

					var key;
					var cellData = new CellData();

					// some properties that have default values might only be sent later to client;
					// if that happens, we need to then bind them two-way as well
					if (!angular.isDefined(cellProxies.unwatchFuncs)) {
						cellProxies.unwatchFuncs = [];
					}

					// properties like tagstring and dataprovider are not set directly on the component but are more linked to the current record
					// so we will take these from the foundset record and apply them to child elements
					if (element.foundsetConfig && element.foundsetConfig.recordBasedProperties) {
						for (var i in element.foundsetConfig.recordBasedProperties) {
							var propertyName = element.foundsetConfig.recordBasedProperties[i];
							if (angular.isDefined(element.modelViewport) && angular.isDefined(element.modelViewport[relativeRowIndex]))
								cellData[propertyName] = element.modelViewport[relativeRowIndex][propertyName];
							// 2 way data link between element record based properties from modelViewport and the merged cell model
							cellProxies.unwatchFuncs = cellProxies.unwatchFuncs.concat($utils.bindTwoWayObjectProperty(cellData, propertyName, elements, [elementIndex, "modelViewport", function() { return rowIdToViewportRelativeRowIndex(rowId); }, propertyName], false, $scope));
						}
					}
					// attach the model change notifier from the parent column model so that all calls are relayed to the cell.
					if (!element.model[$sabloConstants.modelChangeNotifier]) {
						Object.defineProperty(element.model,$sabloConstants.modelChangeNotifier, {configurable : true,value:function(property,value) {
							for(var key in rowProxyObjects) {
								// test if there is a column at this point for that index, it could be hidden and not created yet.
								if (rowProxyObjects[key][elementIndex]) {
									var mergedCellModel = rowProxyObjects[key][elementIndex].mergedCellModel
									// test if it has its own modelChangeNotifier, if so call it else skip the rest (all cells in a column should be the same)
									if (mergedCellModel.hasOwnProperty($sabloConstants.modelChangeNotifier))
										mergedCellModel[$sabloConstants.modelChangeNotifier](property,value);
									else return;
								}
							}
						}});
					}
					if (!cellData.hasOwnProperty($sabloConstants.modelChangeNotifier) && cellChangeNotifierCaches[renderedRowIndex] && cellChangeNotifierCaches[renderedRowIndex].length > elementIndex && cellChangeNotifierCaches[renderedRowIndex][elementIndex])
					{
						Object.defineProperty(cellData,$sabloConstants.modelChangeNotifier, {configurable : true,value:cellChangeNotifierCaches[renderedRowIndex][elementIndex]});
						cellChangeNotifierCaches[renderedRowIndex][elementIndex] = null;
					}	
					cellProxies.mergedCellModel = cellModel = cellData;
				}
				return cellModel;
			}

			var deferredAPICallExecution;
			function linkAPIToAllCellsInColumn(apiFunctionName, elementIndex) {
				// returns a column level API function that will call that API func. on all cell elements of that column
				return function(event)
				{
					var retVal;
					var functionArguments = arguments;
					var callOnFirstSelectedCellOnly = true;
					if (elements[elementIndex].foundsetConfig && elements[elementIndex].foundsetConfig.apiCallTypes) {
						callOnFirstSelectedCellOnly = (elements[elementIndex].foundsetConfig.apiCallTypes[apiFunctionName] != $componentTypeConstants.CALL_ON_ONE_SELECTED_ROW);
					}

					// so if callOnFirstSelectedCellOnly is true, then it will be called only once for the first of the selected rows;
					// otherwise it will be called on the entire column, and the return value will be of a selected row if possible
					if (callOnFirstSelectedCellOnly) {
						if ($scope.foundset.selectedRowIndexes.length > 0) {
							var apiCallTargetRowIndex = $scope.foundset.selectedRowIndexes[0];
							if (event && (event.selectedIndex || event.selectedIndex === 0))
								apiCallTargetRowIndex = event.selectedIndex;// if the server also sent a selectedIndex, then we use that index

							function callAPIAfterScroll(rowIdToCall) {
								// the grid reports that it scrolled very fast - so it did actually scroll, but no content is created/visible yet
								// so we see if the content is actually loaded or if not we must wait for those to render so that the correct scrolled rows are in the rendered array

								function getCellAPIToUse() {
									for (var renderRowIndex in cellAPICaches) {
										var cellAPI = cellAPICaches[renderRowIndex][elementIndex];
										if (cellAPI && cellAPI[apiFunctionName]) {
											var rowId = cellAPICaches[renderRowIndex].rowId;
											if (rowIdToCall === rowId) return cellAPI[apiFunctionName];
										}
									}
								}

								var cellAPIToUse = getCellAPIToUse();
								var retVal;
								if (cellAPIToUse && !changinOrUnstableAPIPromise) retVal = cellAPIToUse.apply(cellAPI, functionArguments);
								else {
									// cannot find it yet - it probably didn't actually load scrolled contents yet; it just scrolled; delay a bit more
									if ($log.debugEnabled) $log.debug("API method call - waiting for scrolled contents to load. Api call: '" + apiFunctionName + "' on column " + elementIndex);
									var deferred = $q.defer();
									var removeListener = $scope.gridApi.core.on.scrollEnd($scope, function () {
										removeListener();
										// some additional timeouts as ui-grid keeps jumping around for a while with that scroll for some reason sometimes
										function executeWhenAPIRowMappingStable() {
											if (!changinOrUnstableAPIPromise) {
												cellAPIToUse = getCellAPIToUse();

												if (cellAPIToUse) deferred.resolve(cellAPIToUse.apply(cellAPI, arguments));
												else deferred.reject("Cannot find API object for first selected index (" + renderRowIndex + ") row after scroll. Failing. Api call: '" + apiFunctionName + "' on column " + elementIndex);
											} else {
												unstableAPIStabilizedListener = executeWhenAPIRowMappingStable;
											}
										}
										executeWhenAPIRowMappingStable();
									});

									retVal = deferred.promise;
								}
								return retVal;
							}

							if (isInViewPort(apiCallTargetRowIndex)) {
								retVal = $scope.gridApi.grid.scrollToIfNecessary($scope.gridApi.grid.getRow($scope.foundset.viewPort.rows[absoluteToViewPort(apiCallTargetRowIndex)]), null /* must be null here, can't be undefined */).then(function () {
									var newFirstSelected = ($scope.foundset.selectedRowIndexes.length > 0 ? $scope.foundset.selectedRowIndexes[0] : -1);
									if ((apiCallTargetRowIndex !== newFirstSelected) && !(event && (event.selectedIndex || event.selectedIndex === 0)))
										return $q.reject("First selected index changed for some reason (" + apiCallTargetRowIndex + " -> " + newFirstSelected + ") while scrolling for loader row API call. Api call: '" + apiFunctionName + "' on column " + elementIndex);
									return callAPIAfterScroll(absoluteRowIndexToRowId(apiCallTargetRowIndex));
								});
							} else {
								// updateGridSelectionFromFoundset will scroll to it anyway, so wait for that to happen
								// and then call API
								deferredAPICallExecution = $q.defer();
								retVal = deferredAPICallExecution.promise.then(function() {
									// success
									deferredAPICallExecution = undefined;
									var newFirstSelected = ($scope.foundset.selectedRowIndexes.length > 0 ? $scope.foundset.selectedRowIndexes[0] : -1);
									if ((apiCallTargetRowIndex !== newFirstSelected)&& !(event && (event.selectedIndex || event.selectedIndex === 0)))
										return $q.reject("First selected index changed for some reason (" + apiCallTargetRowIndex + " -> " + newFirstSelected + ") while scrolling for non-loaded API call. Api call: '" + apiFunctionName + "' on column " + elementIndex);
									return callAPIAfterScroll(absoluteRowIndexToRowId(apiCallTargetRowIndex));
								}, function(reason) {
									// error
									deferredAPICallExecution = undefined;
									$log.error("API method call (deferred until scroll to selection) failed. Api call: '" + apiFunctionName + "' on column " + elementIndex);
									return $q.reject(reason);
								});
							}
						} else if ($scope.foundset.serverSize == 0) {
							if ($log.debugEnabled) $log.debug("API method called when there was no record selected (foundset size is 0). Api call: '" + apiFunctionName + "' on column " + elementIndex);
						} else $log.error("API method called when there was no record selected although foundset size is: " + $scope.foundset.serverSize + ". Api call: '" + apiFunctionName + "' on column " + elementIndex);
					} else {
						var retValForSelectedStored = false;
						var retValForSelectedCell;
						for (var renderRowIndex in cellAPICaches) {
							var cellAPI = cellAPICaches[renderRowIndex][elementIndex];
							if (cellAPI && cellAPI[apiFunctionName]) {
								var rowId = cellAPICaches[renderRowIndex].rowId;
								var rowIsSelected = isRowIndexSelected(rowIdToAbsoluteRowIndex(rowId));
								retVal = cellAPI[apiFunctionName].apply(cellAPI, arguments);

								// give back return value from a selected row cell if possible
								if (!retValForSelectedStored && rowIsSelected) {
									retValForSelectedCell = retVal;
									retValForSelectedStored = true;
								}
							}
						}
						if (retValForSelectedStored) retVal = retValForSelectedCell;
					}
					return retVal;
				}
			}

			// cells provide API calls; one API call (from server) should execute on all cells of that element's column or only on selected based on type.
			// so any API provided by a cell is added to the server side controlled API object; when server calls that API method,
			// it will execute on all cells or only on selected
			$scope.cellApiWrapper = function(ngGridRow, elementIndex, renderedRowIndex, rowElementHelper) {
				var rowAPICache = getOrCreateRowAPICache(ngGridRow, renderedRowIndex, rowElementHelper);
				var cellAPICache = rowAPICache[elementIndex];

				if (!cellAPICache) {
					var columnApi = elements[elementIndex].api;
					rowAPICache[elementIndex] = cellAPICache = {}; // new cell API object
					rowAPICache.unwatchFuncs.push($scope.$watchCollection(function() { return cellAPICache; }, function(newCellAPI) {
						updateColumnAPIFromCell(columnApi, newCellAPI, elementIndex);
					}));
				}
				return cellAPICache;
			}

			function updateColumnAPIFromCell(columnApi, cellAPI, elementIndex) {
				// update column API object with new cell available methods
				for (var p in cellAPI) {
					if (!columnApi[p]) columnApi[p] = linkAPIToAllCellsInColumn(p, elementIndex);
				}
				for (var p in columnApi) {
					if (!cellAPI[p]) delete columnApi[p];
				}
			}

			$scope.cellServoyApiWrapper = function(ngGridRow, elementIndex) {
				var rowId = ngGridRow.entity[$foundsetTypeConstants.ROW_ID_COL_KEY];
				if(rowIdToViewportRelativeRowIndex(rowId) < 0) {
					return {}
				}

				var cellProxies = getOrCreateElementProxies(rowId, elementIndex);

				if (!cellProxies.cellServoyApi) {
					var columnServoyApi = elements[elementIndex].servoyApi;
					cellProxies.cellServoyApi = {
							formWillShow: $scope.servoyApi.formWillShow,
							hideForm: $scope.servoyApi.hideForm,
							getFormUrl: $scope.servoyApi.getFormUrl,
							startEdit: function(property) {
								return columnServoyApi.startEdit(property,rowId);
							},
							apply: function(property)
							{
								return columnServoyApi.apply(property,cellProxies.mergedCellModel,rowId);
							}
					}
				}
				return cellProxies.cellServoyApi;
			}

			var updatingGridSelection = false;

			// bind foundset.selectedRowIndexes to what nggrid has to offer
			function updateFoundsetSelectionFromGrid(newNGGridSelectedItems) {
				if (updatingGridSelection) return;
				if (newNGGridSelectedItems.length == 0 && $scope.model.relatedFoundset.serverSize > 0) return; // we shouldn't try to set no selection if there are records; it will be an invalid request as server doesn't allow that
				// update foundset object selection when it changes in ngGrid
				var tmpSelectedRowIdxs = {};
				for (var idx = 0; idx < newNGGridSelectedItems.length; idx++) {
					var absRowIdx = rowIdToAbsoluteRowIndex(newNGGridSelectedItems[idx][$foundsetTypeConstants.ROW_ID_COL_KEY]);
					if (!isRowIndexSelected(absRowIdx)) $scope.foundset.selectedRowIndexes.push(absRowIdx);
					tmpSelectedRowIdxs['_' + absRowIdx] = true;
				}
				for (var idx = $scope.foundset.selectedRowIndexes.length-1; idx >= 0; idx--) {
					if (!tmpSelectedRowIdxs['_' + $scope.foundset.selectedRowIndexes[idx]]) {
						// here we also handle the case when in multiselect there are records selected outside of viewport
						// in that case if CTRL was pressed then this $watch should not remove those records from selection
						// TODO if CTRL was not pressed in multi-selection, the outside-of-viewPort-selectedIndexes should be cleared as well - but not by this $watch code
						if (!$scope.foundset.multiSelect || isInViewPort(idx)) $scope.foundset.selectedRowIndexes.splice(idx, 1);
					}
				}
				updateGridSelectionFromFoundset(false);
				// it is important that at the end of this function, the two arrays are in sync; otherwise, watch loops may happen
			}
			var updateGridSelectionFromFoundset = function(scrollToSelection) {
				$scope.$evalAsync(function () {
					try {
						if ($scope.foundset)
						{
							var rows = $scope.foundset.viewPort.rows;
							updatingGridSelection = true;
							
							// need to clear else cellNav will set the selection back to lastRowCol
							var oldSelection = $scope.gridApi.selection.getSelectedRows();
							var isNewSelection = !oldSelection || !$scope.foundset.selectedRowIndexes || (oldSelection.length != $scope.foundset.selectedRowIndexes.length);
							if(!isNewSelection) {
								for (var idx = 0;  idx < $scope.foundset.selectedRowIndexes.length; idx++) {
									isNewSelection = $scope.foundset.selectedRowIndexes[idx] != oldSelection[idx];
									if(isNewSelection) break;
								}
							}
							
							if(isNewSelection) {
								$scope.gridApi.grid.cellNav.lastRowCol = null;
							}											
							
							if (rows.length > 0 && $scope.foundset.selectedRowIndexes.length > 0) {
								var scrolledToSelection = !scrollToSelection;
								var oldSelection = $scope.gridApi.selection.getSelectedRows();
								// if first item in the old selection is the same as the first in the new selection,
								// then ignore scrolling; if an API call is waiting for scrolling then we still must scroll...
								if(!deferredAPICallExecution && oldSelection.length > 0 && rows[$scope.foundset.selectedRowIndexes[0]] &&
										(oldSelection[0]._svyRowId == rows[$scope.foundset.selectedRowIndexes[0]]._svyRowId)) {
									scrolledToSelection = true;
								}
								$scope.ignoreSelection = true;
								$scope.gridApi.selection.clearSelectedRows();
								$scope.ignoreSelection = false;
								for (var idx = 0;  idx < $scope.foundset.selectedRowIndexes.length; idx++) {
									var rowIdx = $scope.foundset.selectedRowIndexes[idx];
									if (isInViewPort(rowIdx)) {
										$scope.ignoreSelection = true;
										$scope.gridApi.selection.selectRow(rows[rowIdx]);
										$scope.ignoreSelection = false;
										if(!scrolledToSelection) {
											scrolledToSelection = true;
											var addTimeOut = function(timeoutIdx,rowId) {
												$timeout(function() {
													var row = rows[timeoutIdx];
													if (row && row._svyRowId == rowId) {
														// hack for ui bug: https://github.com/angular-ui/ui-grid/issues/4204
														// it calculates with the header height but it should calculate with just the rowHeigt
														// fake the headerHeight to set it equal to the rowHeigth so that it does scroll up enough
														var headIsZero = $scope.gridApi.grid.headerHeight == 0;
														if (headIsZero) {
															$scope.gridApi.grid.headerHeight = $scope.gridApi.grid.options.rowHeight;
														}
														$scope.gridApi.grid.scrollToIfNecessary($scope.gridApi.grid.getRow(row), null /* must be null here, can't be undefined */).then(function() {
															if (deferredAPICallExecution) deferredAPICallExecution.resolve();
															deferredAPICallExecution = undefined;
														});
														if (headIsZero) {
															$scope.gridApi.grid.headerHeight  = 0;
														}
													}
												}, 0);
											}
											addTimeOut(rowIdx,rows[rowIdx]._svyRowId);

										}
									} else if(!scrolledToSelection) {
										var nrRecordsToLoad = 0;
										if(rowIdx < $scope.foundset.viewPort.startIndex) {
											nrRecordsToLoad = rowIdx - $scope.foundset.viewPort.startIndex;
										} else {
											nrRecordsToLoad = rowIdx - $scope.foundset.viewPort.startIndex - $scope.foundset.viewPort.size +  $scope.gridOptions.infiniteScrollRowsFromEnd +1;
										}

										var tmp;
										if (deferredAPICallExecution) {
											$scope.$evalAsync(function() { // allow one more digest cycle so that changes with lower prio are sent before
												tmp = $sabloUtils.getCurrentEventLevelForServer();
												$sabloUtils.setCurrentEventLevelForServer($sabloUtils.EVENT_LEVEL_SYNC_API_CALL); // to allow it to load records despite the blocking server-side API call on the event thread
												try
												{
													$scope.foundset.loadExtraRecordsAsync(nrRecordsToLoad);
												}
												finally
												{
													$sabloUtils.setCurrentEventLevelForServer(tmp);
												}
											});
										} else {
											$scope.foundset.loadExtraRecordsAsync(nrRecordsToLoad);
										}
										break;
									}
								}
							} else if (rows.length > 0) {
								$scope.ignoreSelection = true;
								$scope.gridApi.selection.selectRow(rows[0]);
								$scope.ignoreSelection = false;
								$scope.gridApi.grid.scrollTo($scope, rows[0], null);
							}
							updatingGridSelection = false;
						}
					} catch (e) {
						if (deferredAPICallExecution) deferredAPICallExecution.reject(e);
						deferredAPICallExecution = undefined;
						throw e;
					}
				});
				// it is important that at the end of this function, the two arrays are in sync; otherwise, watch loops may happen
			};
			$scope.$watchCollection('foundset.selectedRowIndexes', function() {
				updateGridSelectionFromFoundset(true);
			});

			$scope.gridOptions = {
					data: 'foundset.viewPort.rows',
					enableRowSelection: true,
					enableRowHeaderSelection: false,
					excludeProperties: [$foundsetTypeConstants.ROW_ID_COL_KEY],
					multiSelect: false,
					modifierKeysToMultiSelect: true,
					noUnselect: true,
					enableColumnMoving : $scope.model.reorderable,
					enableVerticalScrollbar: uiGridConstants.scrollbars.WHEN_NEEDED,
					enableHorizontalScrollbar: uiGridConstants.scrollbars.WHEN_NEEDED,
					followSourceArray:true,
					useExternalSorting: true,
					primaryKey: $foundsetTypeConstants.ROW_ID_COL_KEY, // not currently documented in ngGrid API but is used internally and useful - see ngGrid source code
					rowTemplate: 'svy-ui-grid/ui-grid-row',
					columnDefs: $scope.columnDefinitions,
					rowHeight: $scope.rowHeight ? $scope.rowHeight : 20,
					showHeader:$scope.model.headerHeight != 0 && !$scope.model.multiLine,
					headerRowHeight: $scope.model.multiLine ? 0 : $scope.model.headerHeight,
					rowIdentity: function(o) {
						return o._svyRowId;
					},
					rowEquality: function(row1,row2) {
						return row1._svyRowId == row2._svyRowId;
					},
					infiniteScrollRowsFromEnd: $scope.pageSize
			};

			if ($scope.model.scrollbars & $scrollbarConstants.VERTICAL_SCROLLBAR_ALWAYS)
					$scope.gridOptions.enableVerticalScrollbar = uiGridConstants.scrollbars.ALWAYS;
			else if ( $scope.model.scrollbars & $scrollbarConstants.VERTICAL_SCROLLBAR_NEVER)
					$scope.gridOptions.enableVerticalScrollbar = uiGridConstants.scrollbars.NEVER;

			if ($scope.model.scrollbars & $scrollbarConstants.HORIZONTAL_SCROLLBAR_ALWAYS)
					$scope.gridOptions.enableHorizontalScrollbar = uiGridConstants.scrollbars.ALWAYS;
			else if ( $scope.model.scrollbars & $scrollbarConstants.HORIZONTAL_SCROLLBAR_NEVER)
					$scope.gridOptions.enableHorizontalScrollbar = uiGridConstants.scrollbars.NEVER;
			

			$scope.gridOptions.onRegisterApi = function(gridApi) {
				var shouldCallDataLoaded = false;
				var focusedRowId;
				var focusedElementId;
				
				$scope.gridApi = gridApi;
				
				$scope.gridApi.core.on.scrollBegin($scope, function () {
					focusedRowId = null;
					focusedElementId = null;
					var focusedElement = $(':focus');
					if(focusedElement.get(0) && focusedElement.get(0).id) {
						var focusedRow = focusedElement.closest(".ui-grid-row");
						if(focusedRow.length > 0) {
							focusedRowId = focusedElement.closest(".ui-grid-row").scope().row.entity._svyRowId;
							focusedElementId = focusedElement.get(0).id;
						}
					}
				});
				
				$scope.gridApi.core.on.scrollEnd($scope, function () {
					if(focusedRowId) {
						for (var renderRowIndex in cellAPICaches) {
							if (cellAPICaches[renderRowIndex].rowId == focusedRowId) {
								var rowElement = cellAPICaches[renderRowIndex].rowElement;
								var focusElement = rowElement.find("#" + focusedElementId);
								focusElement.focus();
								if(focusElement.is('input')) {
									focusElement.select();
								}											
								break;
							}
						}
					}
				});
				
				$scope.gridApi.grid.registerDataChangeCallback(function() {
					updateGridSelectionFromFoundset(true);
				},[uiGridConstants.dataChange.ROW]);
				gridApi.selection.on.rowSelectionChanged($scope,function(row){
					
					if ($scope.ignoreSelection) return;
					
					var newNGGridSelectedItems =  gridApi.selection.getSelectedRows();
					var tmpSelectedRowIdxs = [];
					for (var idx = 0; idx < newNGGridSelectedItems.length; idx++) {
						var absRowIdx = rowIdToAbsoluteRowIndex(newNGGridSelectedItems[idx][$foundsetTypeConstants.ROW_ID_COL_KEY]);
						tmpSelectedRowIdxs.push(absRowIdx);
					}
					if (tmpSelectedRowIdxs.length === 0 && newNGGridSelectedItems.length > 0) return;
					
					requestSelectionUpdate(tmpSelectedRowIdxs);
				});
				
				function requestSelectionUpdate(tmpSelectedRowIdxs) {
					$scope.foundset.requestSelectionUpdate(tmpSelectedRowIdxs).then(
							function(serverRows){
								//success
							},
							function(serverRows){
								//canceled 
								if (serverRows === 'canceled'){
									return;
								}
								//reject
								var i = 0;
								for (i = 0; i < serverRows.length; i++) {
									var rowid = absoluteRowIndexToRowId(serverRows[i]);
									var selection = {};
									selection[$foundsetTypeConstants.ROW_ID_COL_KEY] = rowid;
									$scope.ignoreSelection = true;
									$scope.gridApi.selection.selectRow(selection);
									$scope.ignoreSelection = false;
								}
								document.activeElement.blur();
							}
						);
				};

				gridApi.cellNav.on.navigate($scope,function(newRowCol, oldRowCol){
					var tmpSelectedRowIdxs = [];					
					var absRowIdx = rowIdToAbsoluteRowIndex(newRowCol.row.entity[$foundsetTypeConstants.ROW_ID_COL_KEY]);
					tmpSelectedRowIdxs.push(absRowIdx);
					requestSelectionUpdate(tmpSelectedRowIdxs);
		        });

				gridApi.infiniteScroll.on.needLoadMoreData($scope,function(){
					var extraSize = Math.min($scope.pageSize, $scope.foundset.serverSize - $scope.foundset.viewPort.size);
					if (extraSize !== 0)
					{
						shouldCallDataLoaded = true;
						$scope.foundset.loadExtraRecordsAsync(extraSize);
					}
					else
					{
						// nothing to load, mark data as loaded
						$scope.gridApi.infiniteScroll.dataLoaded(false,false);
					}
				});
			    gridApi.infiniteScroll.on.needLoadMoreDataTop($scope,function(){
			    	$scope.gridApi.infiniteScroll.dataLoaded();
				});
				gridApi.colMovable.on.columnPositionChanged ($scope, function(colDef, originalPosition, newPosition) {
					var reorderedColumns = $scope.gridApi.grid.columns;
					for (var k = 0; k < reorderedColumns.length; k++) {
						for(var i = 0; i < $scope.model.childElements.length; i++) {
							if(reorderedColumns[k].name == $scope.model.childElements[i].name) {
								$scope.model.childElements[i].model.location.x = k;
								break;
							}
						}
					}
				});
				gridApi.core.on.sortChanged ($scope, function( grid, sortColumns ) {
					// call the server (through foundset type)
					var columns = [];
					for (var i = 0; i < sortColumns.length; i++)
					{
						columns[i] = {name: sortColumns[i].name, direction : sortColumns[i].sort.direction};
					}
					$scope.foundset.sort(columns);
				});
				gridApi.colResizable.on.columnSizeChanged ($scope, function(colDef, deltaChange) {
					for(var i = 0; i < $scope.model.childElements.length; i++) {
						if(colDef.name == $scope.model.childElements[i].name) {
							$scope.model.childElements[i].model.size.width += deltaChange;
							break;
						}
					}
				});

				var requestViewPortSize = -1;
				var preferredViewportSize = -1;
				function testNumberOfRows() {
					if ($scope.foundset && $scope.foundset.setPreferredViewportSize && !isNaN($scope.gridApi.grid.gridHeight))
					{
						var numberOfRows = Math.ceil($scope.gridApi.grid.gridHeight / $scope.gridOptions.rowHeight);
						var totalPreferedViewportSize = numberOfRows*pageSizeFactor; 
						if (preferredViewportSize != totalPreferedViewportSize) {
							// make the viewport always X (pageSizeFactor) times the size then the number of rows.
							preferredViewportSize = totalPreferedViewportSize;
							$scope.pageSize = Math.max(totalPreferedViewportSize-numberOfRows, 25);
							$scope.gridOptions.infiniteScrollRowsFromEnd = numberOfRows;
							$scope.foundset.setPreferredViewportSize(preferredViewportSize)
						}
						if (requestViewPortSize == -1 && $scope.foundset.serverSize > $scope.foundset.viewPort.size) {
							if ($scope.foundset.viewPort.size == 0) {
								// its a full reload because viewPort size = 0
								requestViewPortSize = Math.min($scope.foundset.serverSize, numberOfRows+$scope.pageSize);
								$scope.foundset.loadRecordsAsync(0, requestViewPortSize);
							}
							else if ($scope.foundset.viewPort.size < (numberOfRows+$scope.pageSize)) {
								// only add extra needed records; note: this is not for scrolling; just for ensuring initial content + a small scroll window;
								// requesting new data when scrolling is done in another place
								var extraRecords = Math.min($scope.foundset.serverSize- $scope.foundset.viewPort.size, (numberOfRows + $scope.pageSize) - $scope.foundset.viewPort.size);
								requestViewPortSize = $scope.foundset.viewPort.size + extraRecords;
								$scope.foundset.loadExtraRecordsAsync(extraRecords);
							}
						}
					}
				}

				function layoutColumnsAndGrid() {
					$scope.gridApi.grid.gridWidth = gridUtil.elementWidth($element);
					$scope.gridApi.grid.gridHeight = gridUtil.elementHeight($element);
					if (($scope.model.scrollbars & $scrollbarConstants.HORIZONTAL_SCROLLBAR_NEVER) == $scrollbarConstants.HORIZONTAL_SCROLLBAR_NEVER)
					{
						if(!$scope.model.multiLine) {
							var totalWidth = 0;
							var resizeWidth = 0;
							for(var i = 0; i < $scope.model.childElements.length; i++)
							{
								totalWidth += $scope.model.childElements[i].model.size.width;
								var isResizable = (($scope.model.childElements[i].model.anchors & $anchorConstants.EAST) != 0) && (($scope.model.childElements[i].model.anchors & $anchorConstants.WEST) != 0);
								if (isResizable)
								{
									resizeWidth += $scope.model.childElements[i].model.size.width;
								}
							}
							totalWidth = $scope.gridApi.grid.gridWidth - totalWidth;							
						    totalWidth = totalWidth - 17; //make sure possible vertical scroll does now overlap last column
						    
							if (resizeWidth > 0 && totalWidth > 0)
							{
								for(var i = 0; i < $scope.model.childElements.length; i++)
								{
									var isResizable = (($scope.model.childElements[i].model.anchors & $anchorConstants.EAST) != 0) && (($scope.model.childElements[i].model.anchors & $anchorConstants.WEST) != 0);
									if (isResizable)
									{
										// calculate new width based on weight
										var elemWidth = $scope.model.childElements[i].model.size.width;
										var newWidthDelta = elemWidth * totalWidth / resizeWidth;										
										$scope.columnDefinitions[i].width = elemWidth + newWidthDelta;
										$scope.gridApi.grid.columns[i].width = $scope.columnDefinitions[i].width;
									}
								}
							}	
						}
						else {
							$scope.columnDefinitions[0].width = $scope.gridApi.grid.gridWidth - 17;
							$scope.gridApi.grid.columns[0].width = $scope.columnDefinitions[0].width; //make sure possible vertical scroll does now overlap last column
						}
					}
					
					$scope.gridApi.grid.refreshCanvas(true).then(function() {
						// make sure the columns are all rendered that are in the viewport (SVY-8638)
						$scope.gridApi.grid.redrawInPlace();
					})
					
				}

				$timeout(function(){

					// this is only needed because of the $timeout, it can happen that the form
					// is already destroyed, so, we do an extra check here to avoid further exceptions
					if($scope.$parent.formname != undefined && !$sabloApplication.hasFormState($scope.$parent.formname)) {
						// form is already destroyed
						return;
					}

					layoutColumnsAndGrid();

					// watch for resize and re-layout when needed - but at most once every 200 ms
					function justToIsolateScope() {
						var minRelayoutPeriodPassed = true;
						var pendingLayout = false;
						var elementSize = null;
						var timeoutPromise = null;
						function getNewSize() {
							var newSize = [ gridUtil.elementWidth($element), gridUtil.elementHeight($element) ];
							if (!elementSize || (newSize[0] != elementSize[0] || newSize[1] != elementSize[1])){
								elementSize = newSize;
								$scope.$apply();
							}
						}
						$scope.$watch(function() {
							if (timeoutPromise) $timeout.cancel(timeoutPromise);
							timeoutPromise = $timeout(getNewSize,500, false);
							return elementSize;
						  },
							function(oldV, newV) {
							if (oldV != newV) {
								// the portal resized (browser window resize or split pane resize for example)
								if (pendingLayout) return; // will layout later anyway
								if (minRelayoutPeriodPassed) {
									layoutColumnsAndGrid();

									minRelayoutPeriodPassed = false;
									function wait200ms() {
										if (pendingLayout) {
											pendingLayout = false;
											layoutColumnsAndGrid();
											$timeout(wait200ms, 200);
										} else minRelayoutPeriodPassed = true;
									}
									$timeout(wait200ms, 200);
								} else pendingLayout = true;
							}
						})
					}
					justToIsolateScope();

					testNumberOfRows();
					// reset what ui-grid did if somehow the row height was smaller then the elements height because it didn't layout yet
					$element.children(".svyPortalGridStyle").height('');

					$scope.$watch('foundset.multiSelect', function(newVal, oldVal) {
						// if the server foundset changes and that results in startIndex change (as the viewport will follow first record), see if the page number needs adjusting
						$scope.gridApi.selection.setMultiSelect(newVal);
						if (newVal) {
							var i = 0;
							for (i=0; i<$scope.columnDefinitions.length; i++){
								$scope.columnDefinitions[i].allowCellFocus = false;
							}
						}
					});

					// size can change serverside if records get deleted by someone else and there are no other records to fill the viewport with (by sliding)
//					$scope.$watch('foundset.viewPort.size', function(newVal, oldVal) {
//						if (requestViewPortSize != newVal) requestViewPortSize = -1;
//						testNumberOfRows();
//					});
					$scope.$watch('foundset', function(newVal, oldVal) {
						if (!$scope.foundset.viewPort[$foundsetTypeConstants.UPDATE_SIZE_CALLBACK]){
							$scope.foundset.viewPort[$foundsetTypeConstants.UPDATE_SIZE_CALLBACK] = function (newValue) {
								if (requestViewPortSize != newValue) requestViewPortSize = -1;
								testNumberOfRows();
							}
							if (requestViewPortSize != $scope.foundset.viewPort.size) requestViewPortSize = -1;
							testNumberOfRows();
						}
					});

					$scope.$watch('foundset.serverSize', function(newVal, oldVal) {
						if (requestViewPortSize === 0) requestViewPortSize = -1
						var numberOfRows = Math.ceil($scope.gridApi.grid.gridHeight / $scope.gridOptions.rowHeight);
						if (requestViewPortSize < (numberOfRows+$scope.pageSize) && newVal > oldVal)
						{
							// we need to load extra rows
							requestViewPortSize = -1;
						}	
						testNumberOfRows();
					});

					$scope.$watchCollection('foundset.viewPort.rows', function(newVal, oldVal) {
						rowCache = {};
						// check to see if we have obsolete columns in rowProxyObjects[...] - and clean them up + remove two way binding and any other watches
						var newRowIDs = {};
						if ($scope.foundset && $scope.foundset.viewPort && $scope.foundset.viewPort.rows) {
							for (var idx in $scope.foundset.viewPort.rows) {
								newRowIDs[$scope.foundset.viewPort.rows[idx][$foundsetTypeConstants.ROW_ID_COL_KEY]] = true;
							}
						}
						for (var oldRowId in rowProxyObjects) {
							if (!newRowIDs[oldRowId]) {
								var renderedRowIndex = undefined;
								if (oldVal && newVal && newVal.length > 0)
								{
									for (var i = 0; i < oldVal.length && i < newVal.length ;i++) {
										if(oldVal[i][$foundsetTypeConstants.ROW_ID_COL_KEY] == oldRowId)
										{
											renderedRowIndex = i;
											break;
										}	
									}
								}	
								disposeOfRowProxies(rowProxyObjects[oldRowId],renderedRowIndex);
								delete rowProxyObjects[oldRowId];
							}
						}

						// allow nggrid to update it's model / selected items and make sure selection didn't fall/remain on a wrong item because of that update...
						updateGridSelectionFromFoundset(true);
						if (shouldCallDataLoaded) {
							shouldCallDataLoaded = false;
							var scrollUp = $scope.foundset.viewPort.startIndex > 0;
							var scrollDown = ($scope.foundset.viewPort.startIndex + $scope.foundset.viewPort.size <  $scope.foundset.serverSize);
							$scope.gridApi.infiniteScroll.dataLoaded(scrollUp,scrollDown);
						}
					});
				},0)
			};
			$scope.styleClass = 'svyPortalGridStyle';

			function linkHandlerToRowIdWrapper(handler, rowId) {
				return function() {
//					var entity = $scope.foundset.viewPort.rows[rowIdToViewportRelativeRowIndex(rowId)]
//					$scope.gridApi.selection.selectRow(entity); // TODO for multiselect - what about modifiers such as CTRL? then it might be false
//					updateFoundsetSelectionFromGrid($scope.gridApi.selection.getSelectedRows()); // otherwise the watch/digest will update the foundset selection only after the handler was triggered...
					var recordHandler = handler.selectRecordHandler(rowId);
					return recordHandler.apply(recordHandler, arguments);
				}
			}
			// each handler at column level gets it's rowId from the cell's wrapper handler below (to
			// make sure that the foundset's selection is correct server-side when cell handler triggers)
			$scope.cellHandlerWrapper = function(ngGridRow, elementIndex) {
				var rowId = ngGridRow.entity[$foundsetTypeConstants.ROW_ID_COL_KEY];
				if(rowIdToViewportRelativeRowIndex(rowId) < 0) {
					return {}
				}

				var cellProxies = getOrCreateElementProxies(rowId, elementIndex);

				if (!cellProxies.cellHandlers) {
					var columnHandlers = elements[elementIndex].handlers;
					cellProxies.cellHandlers = {};
					for (var p in columnHandlers) {
						cellProxies.cellHandlers[p] = linkHandlerToRowIdWrapper(columnHandlers[p], rowId);
					}

				}
				return cellProxies.cellHandlers;
			}

			$scope.api.getWidth = $apifunctions.getWidth($element[0]);
			$scope.api.getHeight = $apifunctions.getHeight($element[0]);
			$scope.api.getLocationX = $apifunctions.getX($element[0]);
			$scope.api.getLocationY = $apifunctions.getY($element[0]);



			var className = null;
			Object.defineProperty($scope.model, $sabloConstants.modelChangeNotifier, {
				configurable : true,
				value : function(property, value) {
					switch (property) {
					case "borderType":
						$svyProperties.setBorder($element, value);
						break;
					case "styleClass":
						if (className)
							$element.removeClass(className);
						className = value;
						if (className)
							$element.addClass(className);
						break;
					case "background":
					case "transparent":
						$svyProperties.setCssProperty($element, "backgroundColor", $scope.model.transparent ? "transparent" : $scope.model.background);
						break;
					}
				}
			});
			var destroyListenerUnreg = $scope.$on("$destroy", function() {
				destroyListenerUnreg();
				delete $scope.model[$sabloConstants.modelChangeNotifier];
				for(var i=0;i<elements.length;i++) {
					delete elements[i].model[$sabloConstants.modelChangeNotifier];
				}
			});
			// data can already be here, if so call the modelChange function so that it is initialized correctly.
			var modelChangFunction = $scope.model[$sabloConstants.modelChangeNotifier];
			for (key in $scope.model) {
				modelChangFunction(key, $scope.model[key]);
			}

			// // special method that servoy calls when this component goes into find mode.
			// $scope.api.setFindMode = function(findMode, editable) {
			// 	$scope.model.svy_findMode = findMode;
			// 	$scope.model.svy_editable = editable;
			// 	setElementsFindMode($scope.model.svy_findMode, $scope.model.svy_editable);
			// };
		},
		templateUrl: 'servoydefault/portal/portal.html',
		replace: true
	};
}])
.run(['$templateCache', function($templateCache) {

  $templateCache.put('svy-ui-grid/ui-grid-row',
		  "<div row-element-helper sablo-tabseq=\"rowRenderIndex + 1\" sablo-tabseq-config=\"{container: true}\"><div ng-repeat=\"(colRenderIndex, col) in colContainer.renderedColumns track by col.colDef.name\" class=\"ui-grid-cell\" ng-class=\"{ 'ui-grid-row-header-cell': col.isRowHeader }\" ui-grid-cell></div></div>"
  );

  $templateCache.put('ui-grid/uiGridHeaderCell',
		  "<div ng-class=\"{ 'sortable': sortable }\"><!-- <div class=\"ui-grid-vertical-bar\">&nbsp;</div> --><div class=\"ui-grid-cell-contents\" col-index=\"renderIndex\" svy-header-click=\"col.colDef.svyHeaderAction\"><span ng-if=\"!!col.colDef.displayNameHTML\"><span ng-bind-html=\"col.colDef.displayNameHTML CUSTOM_FILTERS\"></span></span><span ng-if=\"!col.colDef.displayNameHTML\"><span>{{ col.displayName CUSTOM_FILTERS }}</span></span> <span ui-grid-visible=\"col.sort.direction\" ng-class=\"{ 'ui-grid-icon-up-dir': col.sort.direction == asc, 'ui-grid-icon-down-dir': col.sort.direction == desc, 'ui-grid-icon-blank': !col.sort.direction }\">&nbsp;</span></div><div class=\"ui-grid-column-menu-button\" ng-if=\"grid.options.enableColumnMenus && !col.isRowHeader  && col.colDef.enableColumnMenu !== false\" ng-click=\"toggleMenu($event)\" ng-class=\"{'ui-grid-column-menu-button-last-col': isLastCol && grid.options.enableGridMenu}\"><i class=\"ui-grid-icon-angle-down\">&nbsp;</i></div><div ng-if=\"filterable\" class=\"ui-grid-filter-container\" ng-repeat=\"colFilter in col.filters\"><div ng-if=\"colFilter.type !== 'select'\"><input type=\"text\" class=\"ui-grid-filter-input\" ng-model=\"colFilter.term\" ng-attr-placeholder=\"{{colFilter.placeholder || ''}}\"><div class=\"ui-grid-filter-button\" ng-click=\"colFilter.term = null\"><i class=\"ui-grid-icon-cancel\" ng-show=\"!!colFilter.term\">&nbsp;</i><!-- use !! because angular interprets 'f' as false --></div></div><div ng-if=\"colFilter.type === 'select'\"><select class=\"ui-grid-filter-select\" ng-model=\"colFilter.term\" ng-attr-placeholder=\"{{colFilter.placeholder || ''}}\" ng-options=\"option.value as option.label for option in colFilter.selectOptions\"></select><div class=\"ui-grid-filter-button-select\" ng-click=\"colFilter.term = null\"><i class=\"ui-grid-icon-cancel\" ng-show=\"!!colFilter.term\">&nbsp;</i><!-- use !! because angular interprets 'f' as false --></div></div></div></div>"
  );

}])
.directive('rowElementHelper', [function() {
	return {
		restrict: 'A',
	    link: function($scope, $element, attrs, ctrl, transclude) {
			$scope.rowElementHelper = {
					getRowElement : function() {
						return $element;
					}
			}
		}
	}
}]).directive('cellHelper', ["$parse","$formatterUtils","$svyProperties",function($parse,$formatterUtils,$svyProperties) {
	return {
		scope: {
			model: "=cellHelper",
			handlers: "=svyHandlers"
		},
		restrict: 'A',
	    link: function($scope, $element, attrs, ctrl, transclude) {
	    	$scope.$watch("model.dataProviderID",function(){
    			var svyFormat = $scope.model.format;
    			var data = $scope.model.dataProviderID;
    			
    			if ($scope.model.valuelistID) {
    				var valueList = $scope.model.valuelistID;
					for (var i=0;i<valueList.length;i++)
					{
						if(valueList[i].realValue == data)
						{
							data = valueList[i].displayValue;
							break;
						}
					}
    			}

    			if(svyFormat){
			    	var type = svyFormat ? svyFormat.type: null;
			    	var format = svyFormat.display? svyFormat.display : svyFormat.edit
			    	try {
			    		data = $formatterUtils.format(data,format,type);
			    	}catch(e){
			    		console.log(e)
			    	}
			    	if (data && svyFormat.type == "TEXT") {
			    		if (svyFormat.uppercase) data = data.toUpperCase();
			    		else if(svyFormat.lowercase) data = data.toLowerCase();
					}
		    	}
    			//.text(null) really sets null, see http://bugs.jquery.com/ticket/13666
    			if (data === null) data = "";
    			$element.text(data);
	    	})
	    	if ($scope.model.styleClass) {
	    		 $element.addClass($scope.model.styleClass);
	    	}
	    	if ($scope.model.background || $scope.model.transparent)
	    	{
	    		$svyProperties.setCssProperty($element,"backgroundColor",$scope.model.transparent?"transparent":$scope.model.background);
	    	}
	    	if ($scope.handlers) {
		    	if ($scope.handlers.onActionMethodID) {
		    		$element.on("click", function(event) {
		    			$scope.$evalAsync(function() {
		    				$scope.handlers.onActionMethodID(event);
		    			});
		    		});
		    	}
	    	}
		}
	}
}]).directive('svyHeaderClick',  ["$parse",function($parse) {
	return {
		restrict: 'A',
		link: function (scope, element, attrs) {
			var x = $parse(attrs['svyHeaderClick'])(scope)
			if (x) {
				element.on("click",x);
			}
		}
	}
}]);
