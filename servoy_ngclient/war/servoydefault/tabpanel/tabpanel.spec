{
	"name": "svy-tabpanel",
	"displayName": "Tab Panel",
	"icon": "servoydefault/tabpanel/tabs.gif",
	"definition": "servoydefault/tabpanel/tabpanel.js",
	"serverscript": "servoydefault/tabpanel/tabpanel_server.js",
	"libraries": [{"name":"accordionpanel.css", "version":"1", "url":"servoydefault/tabpanel/accordionpanel.css", "mimetype":"text/css"}],
	"model":
	{
	        "background" : "color", 
	        "borderType" : "border", 
	        "enabled" : {"type":"boolean", "default":true}, 
	        "fontType" : "font", 
	        "foreground" : "color", 
	        "horizontalAlignment" : {"type" :"int", "scope" :"design", "values" :[{"LEFT":2}, {"CENTER":0},{"RIGHT":4}],"default" : -1}, 
	        "location" : "point", 
	        "readOnly" : "boolean", 
	        "selectedTabColor" : "color", 
	        "size" : {"type" :"dimension",  "default" : {"width":300, "height":300}}, 
	        "styleClass" : { "type" :"styleclass", "scope" :"design", "values" :[]}, 
	        "tabIndex" : "int", 
	        "tabOrientation" : {"type" :"int", "scope" :"design", "values" :[{"default" :0}, {"TOP":1}, {"HIDE":-1}]}, 
	        "tabSeq" : {"type" :"tabseq", "scope" :"design"}, 
	        "tabs" : "tab[]", 
	        "transparent" : "boolean", 
	        "visible" : {"type":"boolean", "default":true} 
	},
	"handlers":
	{
	        "onChangeMethodID" : "function", 
	        "onTabChangeMethodID" : "function" 
	},
	"api":
	{
	        "addTab": {
	            "returns": "boolean",
				"parameters":[
								{                                                                 
 								"name":"form/formname",
								"type":"object []"
			                	},
             					{                                                                 
 								"name":"name",
								"type":"object",
			            		"optional":"true"
			            		},
             					{                                                                 
 								"name":"tabText",
								"type":"object",
			            		"optional":"true"
			            		},
             					{                                                                 
 								"name":"tooltip",
								"type":"object",
			            		"optional":"true"
			            		},
             					{                                                                 
 								"name":"iconURL",
								"type":"object",
			            		"optional":"true"
			            		},
             					{                                                                 
 								"name":"fg",
								"type":"object",
			            		"optional":"true"
			            		},
             					{                                                                 
 								"name":"bg",
								"type":"object",
			            		"optional":"true"
			            		},
             					{                                                                 
 								"name":"relatedfoundset/relationname",
								"type":"object",
			            		"optional":"true"
			            		},
             					{                                                                 
 								"name":"index",
								"type":"object",
			            		"optional":"true"
			            		}             
							 ]
	        },
	        "getMaxTabIndex": {
	            "returns": "int"
	        },
	        "getMnemonicAt": {
	            "returns": "string",
				"parameters":[
								{                                                                 
 								"name":"i",
								"type":"int"
			                	}             
							 ]
	        },
	        "getSelectedTabFormName": {
	            "returns": "string"
	        },
	        "getTabBGColorAt": {
	            "returns": "string",
				"parameters":[
								{                                                                 
 								"name":"unnamed_0",
								"type":"int"
			                	}             
							 ]
	        },
	        "getTabFGColorAt": {
	            "returns": "string",
				"parameters":[
								{                                                                 
 								"name":"i",
								"type":"int"
			                	}             
							 ]
	        },
	        "getTabFormNameAt": {
	            "returns": "string",
				"parameters":[
								{                                                                 
 								"name":"i",
								"type":"int"
			                	}             
							 ]
	        },
	        "getTabNameAt": {
	            "returns": "string",
				"parameters":[
								{                                                                 
 								"name":"i",
								"type":"int"
			                	}             
							 ]
	        },
	        "getTabRelationNameAt": {
	            "returns": "string",
				"parameters":[
								{                                                                 
 								"name":"i",
								"type":"int"
			                	}             
							 ]
	        },
	        "getTabTextAt": {
	            "returns": "string",
				"parameters":[
								{                                                                 
 								"name":"i",
								"type":"int"
			                	}             
							 ]
	        },
	        "isTabEnabled": {
	            "returns": "boolean",
				"parameters":[
								{                                                                 
 								"name":"unnamed_0",
								"type":"int"
			                	}             
							 ]
	        },
	        "isTabEnabledAt": {
	            "returns": "boolean",
				"parameters":[
								{                                                                 
 								"name":"i",
								"type":"int"
			                	}             
							 ]
	        },
	        "removeAllTabs": {
	            "returns": "boolean"
	        },
	        "removeTabAt": {
	            "returns": "boolean",
				"parameters":[
								{                                                                 
 								"name":"index",
								"type":"int"
			                	}             
							 ]
	        },
	        "setMnemonicAt": {
				"parameters":[
								{                                                                 
 								"name":"index",
								"type":"int"
			                	},
             					{                                                                 
 								"name":"text",
								"type":"string"
			                	}             
							 ]
	        },
	        "setTabBGColorAt": {
				"parameters":[
								{                                                                 
 								"name":"unnamed_0",
								"type":"int"
			                	},
             					{                                                                 
 								"name":"unnamed_1",
								"type":"string"
			                	}             
							 ]
	        },
	        "setTabEnabled": {
				"parameters":[
								{                                                                 
 								"name":"unnamed_0",
								"type":"int"
			                	},
             					{                                                                 
 								"name":"unnamed_1",
								"type":"boolean"
			                	}             
							 ]
	        },
	        "setTabEnabledAt": {
				"parameters":[
								{                                                                 
 								"name":"i",
								"type":"int"
			                	},
             					{                                                                 
 								"name":"b",
								"type":"boolean"
			                	}             
							 ]
	        },
	        "setTabFGColorAt": {
				"parameters":[
								{                                                                 
 								"name":"i",
								"type":"int"
			                	},
             					{                                                                 
 								"name":"s",
								"type":"string"
			                	}             
							 ]
	        },
	        "setTabTextAt": {
				"parameters":[
								{                                                                 
 								"name":"index",
								"type":"int"
			                	},
             					{                                                                 
 								"name":"text",
								"type":"string"
			                	}             
							 ]
	        }
	},
"types": {
  "tab": {
  	"model": {
  		"name": "string",
  		"containsFormId": "form",
  		"text": "tagstring",
  		"relationName": "relation",
  		"active": "boolean",
  		"foreground": "color",
  		"disabled": "boolean",
  		"imageMediaID": "string",
  		"mnemonic": "string"
  	}
  }
}
	 
}