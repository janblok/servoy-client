{
	"name": "bootstrapcomponents-checkbox",
	"displayName": "CheckBox",
	"version": 1,
	"icon": "servoydefault/check/CHECKBOX16.png",
	"definition": "bootstrapcomponents/checkbox/checkbox.js",
	"libraries": [],
	"model":
	{
	        "dataProviderID" : { "type":"dataprovider", "pushToServer": "allow", "tags": { "scope" :"design" }, "ondatachange": { "onchange":"onDataChangeMethodID", "callback":"onDataChangeCallback"}}, 
	        "styleClass" : { "type" :"styleclass", "tags": { "scope" :"design" }, "default":"checkbox"}, 
	        "text" : { "type" : "tagstring" ,"default": "Checkbox" } 
	},
	"handlers":
	{
	         "onActionMethodID" : {
	         	
	        	"parameters":[
								{
						          "name":"event",
								  "type":"JSEvent"
								} 
							 ]
	        }, 
	        "onDataChangeMethodID" : {
	          "returns": "Boolean", 
	         	
	        	"parameters":[
								{
						          "name":"oldValue",
								  "type":"${dataproviderType}"
								}, 
								{
						          "name":"newValue",
								  "type":"${dataproviderType}"
								}, 
								{
						          "name":"event",
								  "type":"JSEvent"
								} 
							 ]
	        }
	},
	"api":
	{
	       
	}
	 
}