{
	"name": "servoydefault-radio",
	"displayName": "Radio",
	"version": 1,
	"icon": "servoydefault/radio/RADIO16.png",
	"definition": "servoydefault/radio/radio.js",
	"libraries": [],
	"model":
	{
	        "background" : "color", 
	        "borderType" : {"type":"border","stringformat":true}, 
	        "dataProviderID" : { "type":"dataprovider", "tags": { "scope" :"design" }, "ondatachange": { "onchange":"onDataChangeMethodID", "callback":"onDataChangeCallback"}}, 
	        "displaysTags" : { "type" : "boolean", "tags": { "scope" : "design" } }, 
	        "editable" : { "type": "protected", "blockingOn": false, "default": true,"for": ["dataProviderID","onActionMethodID","onDataChangeMethodID","onFocusGainedMethodID","onFocusLostMethodID","onRightClickMethodID"] }, 
	        "enabled" : { "type": "protected", "blockingOn": false, "default": true, "for": ["dataProviderID","onActionMethodID","onDataChangeMethodID","onFocusGainedMethodID","onFocusLostMethodID","onRightClickMethodID"] }, 
	        "fontType" : {"type":"font","stringformat":true}, 
	        "foreground" : "color", 
	        "horizontalAlignment" : {"type" :"int", "tags": { "scope" :"design" }, "values" :[{"LEFT":2}, {"CENTER":0},{"RIGHT":4}],"default" : -1}, 
	        "location" : "point", 
	        "margin" : {"type" :"insets", "tags": { "scope" :"design" }}, 
	        "placeholderText" : { "type" : "tagstring", "displayTagsPropertyName" : "displaysTags" }, 
	        "size" : {"type" :"dimension",  "default" : {"width":140, "height":20}}, 
	        "styleClass" : { "type" :"styleclass", "tags": { "scope" :"design" }, "values" :["radio"]}, 
	        "tabSeq" : {"type" :"tabseq", "tags": { "scope" :"design" }}, 
	        "text" : { "type" : "tagstring", "displayTagsPropertyName" : "displaysTags" }, 
	        "toolTipText" : { "type" : "tagstring", "displayTagsPropertyName" : "displaysTags" }, 
	        "transparent" : "boolean", 
	        "valuelistID" : { "type" : "valuelist", "tags": { "scope" :"design" }, "for": "dataProviderID"}, 
	        "visible" : "visible" 
	},
	"handlers":
	{
	        "onActionMethodID" : "function", 
	        "onDataChangeMethodID" : "function", 
	        "onFocusGainedMethodID" : "function", 
	        "onFocusLostMethodID" : "function", 
	        "onRightClickMethodID" : "function" 
	},
	"api":
	{
	        "requestFocus": {
				"parameters":[
								{                                                                 
 								"name":"mustExecuteOnFocusGainedMethod",
								"type":"boolean",
			            		"optional":true
			            		}             
							 ]
	        },
	        "setValueListItems": {
				"parameters":[
								{                                                                 
 								"name":"value",
								"type":"dataset"
			                	}             
							 ]
	        }
	}
	 
}