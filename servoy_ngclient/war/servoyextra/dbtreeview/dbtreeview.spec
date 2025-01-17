{
	"name": "servoyextra-dbtreeview",
	"displayName": "DBTreeview",
	"version": 1,
	"definition": "servoyextra/dbtreeview/dbtreeview.js",
	"serverscript": "servoyextra/dbtreeview/dbtreeview_server.js",
	"libraries": [{"name":"jquery-ui.min.js", "version":"1.11.4", "url":"servoyextra/dbtreeview/js/jquery-ui.min.js", "mimetype":"text/javascript"}, {"name":"jquery.fancytree-all.min.js", "version":"2.9.0", "url":"servoyextra/dbtreeview/js/jquery.fancytree-all.min.js", "mimetype":"text/javascript"},{"name":"ui.fancytree.css", "version":"2.9.0", "url":"servoyextra/dbtreeview/css/skin-win8/ui.fancytree.css", "mimetype":"text/css"}, {"name":"dbtreeview.css", "version":"1", "url":"servoyextra/dbtreeview/css/dbtreeview.css", "mimetype":"text/css"}],
	"model":
	{
	    "roots": "foundsetref[]",
	    "bindings" : "binding[]",
	    "visibleNodeLevel" : "int",
	    "visibleNodeState" : "boolean"
	},	
	"api":
	{
        "addRoots": {
			"parameters":[
							{                                                                 
							"name":"root",
							"type": "foundsetref"
		                	}             
						 ]
        },
        "removeAllRoots": {
        },
        "refresh": {
        	"delayUntilFormLoad": true
        },  
        "isNodeExpanded": {
			"parameters":[
							{                                                                 
							"name":"pk",
							"type": "object[]"
		                	}
						 ],
			"returns": "boolean",
			"delayUntilFormLoad": true
        },
        "setExpandNode": {
			"parameters":[
							{                                                                 
							"name":"pk",
							"type": "object[]"
		                	},
							{                                                                 
							"name":"state",
							"type": "boolean"
		                	}		                	
						 ],
			"delayUntilFormLoad": true						 
        },
        "setNodeLevelVisible": {
			"parameters":[
							{                                                                 
							"name":"level",
							"type": "int"
		                	},
							{                                                                 
							"name":"visible",
							"type": "boolean"
		                	}		                	
						 ],
			"delayUntilFormLoad": true
        },
        "setTextDataprovider": {
			"parameters":[
							{                                                                 
							"name":"datasource",
							"type": "string"
		                	},
							{                                                                 
							"name":"textdataprovider",
							"type": "string"
		                	}	
						 ]
        },
        "setNRelationName": {
			"parameters":[
							{                                                                 
							"name":"datasource",
							"type": "string"
		                	},
							{                                                                 
							"name":"nrelationname",
							"type": "string"
		                	}	
						 ]
        },                
		"setHasCheckBoxDataprovider": {
			"parameters":[
							{                                                                 
							"name":"datasource",
							"type": "string"
		                	},
							{                                                                 
							"name":"hascheckboxdataprovider",
							"type": "string"
		                	}	
						 ]
        },
		"setCallBackInfo": {
			"parameters":[
							{                                                                 
							"name":"datasource",
							"type": "string"
		                	},
							{                                                                 
							"name":"callbackfunction",
							"type": "function"
		                	},
		                	{                                                                 
							"name":"param",
							"type": "string"
		                	}
						 ]
        },
		"setCheckBoxValueDataprovider": {
			"parameters":[
							{                                                                 
							"name":"datasource",
							"type": "string"
		                	},
							{                                                                 
							"name":"checkboxvaluedataprovider",
							"type": "string"
		                	}	
						 ]
        },
		"setMethodToCallOnCheckBoxChange": {
			"parameters":[
							{                                                                 
							"name":"datasource",
							"type": "string"
		                	},
							{                                                                 
							"name":"callbackfunction",
							"type": "function"
		                	},
		                	{                                                                 
							"name":"param",
							"type": "string"
		                	}
						 ]
        },
		"setToolTipTextDataprovider": {
			"parameters":[
							{                                                                 
							"name":"datasource",
							"type": "string"
		                	},
							{                                                                 
							"name":"tooltiptextdataprovider",
							"type": "string"
		                	}	
						 ]
        },
        "setImageURLDataprovider": {
			"parameters":[
							{                                                                 
							"name":"datasource",
							"type": "string"
		                	},
							{                                                                 
							"name":"imageurldataprovider",
							"type": "string"
		                	}	
						 ]
        },
        "setChildSortDataprovider": {
			"parameters":[
							{                                                                 
							"name":"datasource",
							"type": "string"
		                	},
							{                                                                 
							"name":"childsortdataprovider",
							"type": "string"
		                	}	
						 ]
        },        
		"setMethodToCallOnDoubleClick": {
			"parameters":[
							{                                                                 
							"name":"datasource",
							"type": "string"
		                	},
							{                                                                 
							"name":"callbackfunction",
							"type": "function"
		                	},
		                	{                                                                 
							"name":"param",
							"type": "string"
		                	}
						 ]
        }
	},
	"types": {
	  "callback": {
	  		"f": "function",
	  		"param": "string"
	  },
	  "binding": {
	  		"datasource": "string",
	  		"textdataprovider": "string",
	  		"nrelationname": "string",
	  		"hascheckboxdataprovider": "string",
	  		"checkboxvaluedataprovider": "string",
	  		"tooltiptextdataprovider": "string",
	  		"imageurldataprovider": "string",
	  		"childsortdataprovider": "string",
	  		"callbackinfo": "callback",
	  		"methodToCallOnCheckBoxChange": "callback",
	  		"methodToCallOnDoubleClick": "callback"
	  }
	}
}