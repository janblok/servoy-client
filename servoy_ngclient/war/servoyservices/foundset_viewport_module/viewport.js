angular.module('foundset_viewport_module', ['webSocketModule'])
//Viewport reuse code module -------------------------------------------
.factory("$viewportModule", function ($sabloConverters, $foundsetTypeConstants, $sabloUtils) {

	var CONVERSIONS = "viewportConversions"; // data conversion info

	var CHANGE = 0;
	var INSERT = 1;
	var DELETE = 2;

	function addDataWatchToCell(columnName /*can be null*/, idx, viewPort, internalState, componentScope) {
		if (componentScope) {
			function queueChange(newData, oldData) {
				var r = {};
				
				if (angular.isDefined(internalState[$foundsetTypeConstants.FOR_FOUNDSET_PROPERTY])) {
					r[$foundsetTypeConstants.ROW_ID_COL_KEY] = internalState[$foundsetTypeConstants.FOR_FOUNDSET_PROPERTY]().viewPort.rows[idx][$foundsetTypeConstants.ROW_ID_COL_KEY];
				} else r[$foundsetTypeConstants.ROW_ID_COL_KEY] = viewPort[idx][$foundsetTypeConstants.ROW_ID_COL_KEY]; // if it doesn't have internalState[$foundsetTypeConstants.FOR_FOUNDSET_PROPERTY] then it's probably the foundset property's viewport directly which has those in the viewport
				r.dp = columnName;
				r.value = newData;

				// convert new data if necessary
				var conversionInfo = internalState[CONVERSIONS] ? internalState[CONVERSIONS][idx] : undefined;
				if (conversionInfo && conversionInfo[columnName]) r.value = $sabloConverters.convertFromClientToServer(r.value, conversionInfo[columnName], oldData);
				else r.value = $sabloUtils.convertClientObject(r.value);

				internalState.requests.push({viewportDataChanged: r});
				if (internalState.changeNotifier) internalState.changeNotifier();
			}
			
			function getCellValue() { return columnName == null ? viewPort[idx] : viewPort[idx][columnName] }; // viewport row can be just a value or an object of key/value pairs

			if (getCellValue() && getCellValue()[$sabloConverters.INTERNAL_IMPL] && getCellValue()[$sabloConverters.INTERNAL_IMPL].setChangeNotifier) {
				// smart propery value

				// watch for change-by reference
				internalState.unwatchData[idx].push(
						componentScope.$watch(getCellValue, function (newData, oldData) {
							if (newData !== oldData) { /* this doesn't seem to work correctly for 2 identical Date objects in Chrome when debugging; but it should */
								queueChange(newData, oldData);
							}
						})
				);

				getCellValue()[$sabloConverters.INTERNAL_IMPL].setChangeNotifier(function () {
					if (getCellValue()[$sabloConverters.INTERNAL_IMPL].isChanged()) queueChange(getCellValue(), getCellValue());
				});
			} else {
				// deep watch for change-by content / dumb value
				internalState.unwatchData[idx].push(
						componentScope.$watch(getCellValue, function (newData, oldData) {
							if (newData !== oldData) { /* this doesn't seem to work correctly for 2 identical Date objects in Chrome when debugging; but it should */
								var changed = false;
								if (typeof newVal == "object") {
									var conversionInfo = internalState[CONVERSIONS] ? internalState[CONVERSIONS][idx] : undefined;
									if ($sabloUtils.isChanged(newData, oldData, conversionInfo)) {
										changed = true;
									}
								} else {
									changed = true;
								}
								if (changed) queueChange(newData, oldData);
							}
						}, true)
				);
			}
		}
	};

	function addDataWatchesToRow(idx, viewPort, internalState, componentScope, simpleRowValue/*not key/value pairs in each row*/) {
		if (!angular.isDefined(internalState.unwatchData)) internalState.unwatchData = {};
		internalState.unwatchData[idx] = [];
		if (simpleRowValue) {
			addDataWatchToCell(null, idx, viewPort, internalState, componentScope);
		} else {
			var columnName;
			for (columnName in viewPort[idx]) {
				if (columnName !== $foundsetTypeConstants.ROW_ID_COL_KEY) addDataWatchToCell(columnName, idx, viewPort, internalState, componentScope);
			}
		}
	};

	function addDataWatchesToRows(viewPort, internalState, componentScope, simpleRowValue/*not key/value pairs in each row*/) {
		var i;
		for (i = viewPort.length - 1; i >= 0; i--) {
			addDataWatchesToRow(i, viewPort, internalState, componentScope, simpleRowValue);
		}
	};

	function removeDataWatchesFromRow(idx, internalState) {
		if (internalState.unwatchData) {
			for (j = internalState.unwatchData[idx].length - 1; j >= 0; j--)
				internalState.unwatchData[idx][j]();
			delete internalState.unwatchData[idx];
		}
	};

	function removeDataWatchesFromRows(rowCount, internalState) {
		var i;
		for (i = rowCount - 1; i >= 0; i--) {
			removeDataWatchesFromRow(i, internalState);
		}
	};

	// TODO we could keep only one row conversion instead of conversion info for all cells... 
	function removeRowConversionInfo(i, internalState) {
		if (angular.isDefined(internalState[CONVERSIONS]) && angular.isDefined(i)) {
			delete internalState[CONVERSIONS][i];
		}
	};

	function updateRowConversionInfo(idx, internalState, serverConversionInfo) {
		if (angular.isUndefined(internalState[CONVERSIONS])) {
			internalState[CONVERSIONS] = {};
		}
		internalState[CONVERSIONS][idx] = serverConversionInfo;
	};

	function updateAllConversionInfo(viewPort, internalState, serverConversionInfo) {
		internalState[CONVERSIONS] = {};
		var i;
		for (i = viewPort.length - 1; i >= 0; i--)
			updateRowConversionInfo(i, internalState, serverConversionInfo ? serverConversionInfo[i] : undefined);
	};

	function updateWholeViewport(viewPortHolder, viewPortPropertyName, internalState, viewPortUpdate, viewPortUpdateConversions, componentScope, componentModelGetter) {
		if (viewPortUpdateConversions) {
			// do the actual conversion
			viewPortUpdate = $sabloConverters.convertFromServerToClient(viewPortUpdate, viewPortUpdateConversions, viewPortHolder[viewPortPropertyName], componentScope, componentModelGetter);
		}
		viewPortHolder[viewPortPropertyName] = viewPortUpdate;
		// update conversion info
		updateAllConversionInfo(viewPortHolder[viewPortPropertyName], internalState, viewPortUpdateConversions);
	};

	function updateViewportGranularly(viewPort, internalState, rowUpdates, rowUpdateConversions, componentScope,
			componentModelGetter, simpleRowValue/*not key/value pairs in each row*/) {
		// partial row updates (remove/insert/update)

		// {
		//   "rows": rowData, // array again
		//   "startIndex": ...,
		//   "endIndex": ...,
		//   "type": ... // ONE OF CHANGE = 0; INSERT = 1; DELETE = 2;
		// }

		// apply them one by one
		var i;
		var j;
		for (i = 0; i < rowUpdates.length; i++) {
			var rowUpdate = rowUpdates[i];
			if (rowUpdate.type == CHANGE) {
				for (j = rowUpdate.startIndex; j <= rowUpdate.endIndex; j++) {
					// rows[j] = rowUpdate.rows[j - rowUpdate.startIndex];
					// because of a bug in ngGrid that doesn't detect array item changes if array length doesn't change
					// we will reuse the existing row object as a workaround for updating (a case was filed for that bug as it's breaking scenarios with
					// delete and insert as well)

					var dpName;
					var relIdx = j - rowUpdate.startIndex;

					// apply the conversions
					var rowConversionUpdate = (rowUpdateConversions && rowUpdateConversions[i] && rowUpdateConversions[i].rows) ? rowUpdateConversions[i].rows[relIdx] : undefined;
					if (rowConversionUpdate) $sabloConverters.convertFromServerToClient(rowUpdate.rows[relIdx], rowConversionUpdate, viewPort[j], componentScope, componentModelGetter);

					if (simpleRowValue) {
						viewPort[j] = rowUpdate.rows[relIdx];
						
						if (rowConversionUpdate) {
							// update conversion info
							if (angular.isUndefined(internalState[CONVERSIONS])) {
								internalState[CONVERSIONS] = {};
							}
							internalState[CONVERSIONS][j] = rowConversionUpdate;
						} else if (angular.isDefined(internalState[CONVERSIONS]) && angular.isDefined(internalState[CONVERSIONS][j]))
							delete internalState[CONVERSIONS][j];
					} else {
						// key/value pairs in each row
						// this might be a partial update (so only a column changed for example) - don't drop all other columns, just update the ones we received
						for (dpName in rowUpdate.rows[relIdx]) {
							// update value
							viewPort[j][dpName] = rowUpdate.rows[relIdx][dpName];
	
							if (rowConversionUpdate) {
								// update conversion info
								if (angular.isUndefined(internalState[CONVERSIONS])) {
									internalState[CONVERSIONS] = {};
								}
								if (angular.isUndefined(internalState[CONVERSIONS][j]))
								{
									internalState[CONVERSIONS][j] = {};
								}
								internalState[CONVERSIONS][j][dpName] = rowConversionUpdate[dpName];
							} else if (angular.isDefined(internalState[CONVERSIONS]) && angular.isDefined(internalState[CONVERSIONS][j])
									 && angular.isDefined(internalState[CONVERSIONS][j][dpName])) delete internalState[CONVERSIONS][j][dpName];
						}
					}
				}
			} else if (rowUpdate.type == INSERT) {
				if (rowUpdateConversions && rowUpdateConversions[i]) $sabloConverters.convertFromServerToClient(rowUpdate, rowUpdateConversions[i], undefined, componentScope, componentModelGetter);

				for (j = rowUpdate.rows.length - 1; j >= 0 ; j--) {
					viewPort.splice(rowUpdate.startIndex, 0, rowUpdate.rows[j]);
					updateRowConversionInfo(rowUpdate.startIndex, internalState, rowUpdateConversions ? rowUpdateConversions[j] : undefined);
				}
				// insert might have made obsolete some records in cache; remove those; for inserts
				// !!! rowUpdate.endIndex means the new length of the viewport
				if (viewPort.length > rowUpdate.endIndex) {
					// remove conversion info for these rows as well
					if (internalState[CONVERSIONS]) {
						for (j = rowUpdate.endIndex; j < viewPort.length; j++)
							removeRowConversionInfo(j, internalState);
					}

					viewPort.splice(rowUpdate.endIndex, viewPort.length - rowUpdate.endIndex);

//					// workaround follows for a bug in ng-grid (changing the row references while the array has the same length doesn't trigger a UI update)
//					// see https://github.com/angular-ui/ng-grid/issues/1279
//					viewPortHolder[viewPortPropertyName] = viewPort.splice(0); // changes array reference completely while keeping contents
//					viewPort = viewPortHolder[viewPortPropertyName];
				}
			} else if (rowUpdate.type == DELETE) {
				if (rowUpdateConversions && rowUpdateConversions[i]) $sabloConverters.convertFromServerToClient(rowUpdate, rowUpdateConversions[i], undefined, componentScope, componentModelGetter);

				var oldLength = viewPort.length;
				if (internalState[CONVERSIONS]) {
					// delete conversion info for deleted rows
					for (j = rowUpdate.startIndex; j <= rowUpdate.endIndex; j++)
						removeRowConversionInfo(j, internalState);
				}
				viewPort.splice(rowUpdate.startIndex, rowUpdate.endIndex - rowUpdate.startIndex + 1);
				for (j = 0; j < rowUpdate.rows.length; j++) {
					viewPort.push(rowUpdate.rows[j]);
					updateRowConversionInfo(viewPort.length - 1, internalState, rowUpdateConversions ? rowUpdateConversions[j] : undefined);
				}
//				if (oldLength == viewPort.length) {
//				// workaround follows for a bug in ng-grid (changing the row references while the array has the same length doesn't trigger a UI update)
//				// see https://github.com/angular-ui/ng-grid/issues/1279
//				viewPortHolder[viewPortPropertyName] = viewPort.splice(0); // changes array reference completely while keeping contents
//				viewPort = viewPortHolder[viewPortPropertyName];
//				}
			}
		}
	};

	return {
		updateWholeViewport: updateWholeViewport,
		updateViewportGranularly: updateViewportGranularly,

		addDataWatchesToRows: addDataWatchesToRows,
		removeDataWatchesFromRows: removeDataWatchesFromRows,
		updateAllConversionInfo: updateAllConversionInfo
	};

});
