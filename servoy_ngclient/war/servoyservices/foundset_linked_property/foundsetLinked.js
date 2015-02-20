angular.module('foundset_linked_property', ['webSocketModule', 'servoyApp', 'foundset_custom_property', 'foundset_viewport_module'])
// Foundset linked type ------------------------------------------
.run(function ($sabloConverters, $sabloUtils, $viewportModule, $servoyInternal, $log, $foundsetTypeConstants, $sabloUtils) {

	var SINGLE_VALUE = "sv";
	var SINGLE_VALUE_UPDATE = "svu";
	var VIEWPORT_VALUE = "vp";
	var VIEWPORT_VALUE_UPDATE = "vpu";
	var CONVERSION_NAME = "fsLinked";
	var PROPERTY_CHANGE = "propertyChange";

	var CONVERSIONS = 'conversions';
	
	/** Initializes internal state of a new value */
	function initializeNewValue(newValue) {
		$sabloConverters.prepareInternalState(newValue);
		var internalState = newValue[$sabloConverters.INTERNAL_IMPL];

		// implement what $sabloConverters need to make this work
		internalState.setChangeNotifier = function(changeNotifier) {
			internalState.changeNotifier = changeNotifier; 
		}
		internalState.isChanged = function() {
			return internalState.requests && (internalState.requests.length > 0);;
		}

		// private impl
		internalState.recordLinked = false;
		internalState.viewportSizeUnwatch = null;
		internalState.conversionInfo = [];
	}
	
	$sabloConverters.registerCustomPropertyHandler(CONVERSION_NAME, {
		fromServerToClient: function (serverJSONValue, currentClientValue, componentScope, componentModelGetter) {
			var newValue = (currentClientValue ? currentClientValue : []);

			// remove watches to avoid an unwanted detection of received changes
			if (currentClientValue != null && angular.isDefined(currentClientValue)) {
				var iS = currentClientValue[$sabloConverters.INTERNAL_IMPL];
				$viewportModule.removeDataWatchesFromRows(currentClientValue.length, currentClientValue[$sabloConverters.INTERNAL_IMPL]);
				if (iS.viewportSizeUnwatch) iS.viewportSizeUnwatch();
			}

			if (serverJSONValue != null && angular.isDefined(serverJSONValue)) {
				var didSomething = false;
				var internalState = newValue[$sabloConverters.INTERNAL_IMPL];
				if (!angular.isDefined(internalState)) {
					initializeNewValue(newValue);
					internalState = newValue[$sabloConverters.INTERNAL_IMPL];
				}

				if (angular.isDefined(serverJSONValue[$foundsetTypeConstants.FOR_FOUNDSET_PROPERTY])) {
					// the foundset that this property is linked to; keep that info in internal state; viewport.js needs it
					var forFoundsetPropertyName = serverJSONValue[$foundsetTypeConstants.FOR_FOUNDSET_PROPERTY];
					internalState[$foundsetTypeConstants.FOR_FOUNDSET_PROPERTY] = function() {
						return componentModelGetter()[forFoundsetPropertyName];
					};
					didSomething = true;
				}

				var childChangedNotifier;
				
				if (angular.isDefined(serverJSONValue[VIEWPORT_VALUE_UPDATE])) {
					internalState.recordLinked = true;
					$viewportModule.updateViewportGranularly(newValue, internalState, serverJSONValue[VIEWPORT_VALUE_UPDATE],
							$sabloUtils.getInDepthProperty(serverJSONValue, CONVERSIONS, VIEWPORT_VALUE_UPDATE),
							componentScope, componentModelGetter, true);
				} else {
					// the rest will always be treated as a full viewport update (single values are actually going to generate a full viewport of of 'the one' new value)
					var wholeViewport;
					var conversionInfos;
					
					function updateWholeViewport() {
						var viewPortHolder = { "tmp" : newValue };
						$viewportModule.updateWholeViewport(viewPortHolder, "tmp", internalState, wholeViewport, conversionInfos, componentScope, componentModelGetter);
						
						// updateWholeViewport probably changed "tmp" reference to value of "wholeViewport"...
						// update current value reference because that is what is present in the model
						newValue.splice(0, newValue.length);
						var tmp = viewPortHolder["tmp"];
						for (var tz = 0; tz < tmp.length; tz++) newValue.push(tmp[tz]);
					}
					
					if (angular.isDefined(serverJSONValue[SINGLE_VALUE]) || angular.isDefined(serverJSONValue[SINGLE_VALUE_UPDATE])) {
						// just update single value from server and make copies of it to duplicate
						internalState.recordLinked = false;
						var conversionInfo = $sabloUtils.getInDepthProperty(serverJSONValue, CONVERSIONS, SINGLE_VALUE);
						var singleValue = angular.isDefined(serverJSONValue[SINGLE_VALUE]) ? serverJSONValue[SINGLE_VALUE] : serverJSONValue[SINGLE_VALUE_UPDATE];
						
						function generateWholeViewportFromOneValue(vpSize) {
							if (angular.isUndefined(vpSize)) vpSize = 0;
							wholeViewport = [];
							conversionInfos = conversionInfo ? [] : undefined;
							
							for (var index = vpSize - 1; index >= 0; index--) {
								wholeViewport.push(singleValue);
								if (conversionInfo) conversionInfos.push(conversionInfo);
							}
						}
						function vpSizeGetter() { return $sabloUtils.getInDepthProperty(internalState[$foundsetTypeConstants.FOR_FOUNDSET_PROPERTY](), "viewPort", "size") };
						var initialVPSize = vpSizeGetter();
						generateWholeViewportFromOneValue(initialVPSize);
						
						// watch foundSet viewport size; when it changes generate a new viewport client side
						internalState.viewportSizeUnwatch = componentScope.$watch(vpSizeGetter,
								function (newV) {
									if (newV === initialVPSize) return;
									initialVPSize = -1;
									if (!angular.isDefined(newV)) newV = 0;
									
									$viewportModule.removeDataWatchesFromRows(newValue.length, internalState);
									generateWholeViewportFromOneValue(newV);
									updateWholeViewport();
									$viewportModule.addDataWatchesToRows(newValue, internalState, componentScope, true);
								});
					} else if (angular.isDefined(serverJSONValue[VIEWPORT_VALUE])) {
						internalState.recordLinked = true;
						wholeViewport = serverJSONValue[VIEWPORT_VALUE];
						conversionInfos = $sabloUtils.getInDepthProperty(serverJSONValue, CONVERSIONS, VIEWPORT_VALUE);
					}
					
					if (angular.isDefined(wholeViewport)) updateWholeViewport();
					else if (!didSomething) $log.error("Can't interpret foundset linked prop. server update correctly: " + JSON.stringify(serverJSONValue, undefined, 2));
				}
			}
			
			// restore/add model watch
			if (angular.isDefined(newValue) && newValue !== null) {
				var iS = newValue[$sabloConverters.INTERNAL_IMPL];
				$viewportModule.addDataWatchesToRows(newValue, iS, componentScope, true);
			}
			return newValue;
		},

		fromClientToServer: function(newClientData, oldClientData) {
			if (newClientData) {
				var internalState = newClientData[$sabloConverters.INTERNAL_IMPL];
				if (internalState.isChanged()) {
					if (!internalState.recordLinked) {
						// we don't need to send rowId to server in this case; we just need value
						for (var index in internalState.requests) {
							internalState.requests[index][PROPERTY_CHANGE] = internalState.requests[index].viewportDataChanged.value;
							delete internalState.requests[index].viewportDataChanged;
						}
					}
					var tmp = internalState.requests;
					internalState.requests = [];
					return tmp;
				}
			}
			return [];
		}
	});
	
});
