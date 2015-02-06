{
	"name": "bootstrapcomponents-select",
	"displayName": "Combobox",
	"version": 1,
	"icon": "servoydefault/combobox/SELECT16.png",
	"definition": "bootstrapcomponents/select/select.js",
	"libraries": [],
	"model":
	{
	        "dataProviderID" : { "type":"dataprovider", "tags": { "scope" :"design" }, "ondatachange": { "onchange":"onDataChangeMethodID", "callback":"onDataChangeCallback"}}, 
	        "styleClass" : { "type" :"styleclass", "tags": { "scope" :"design" }}, 
	        "valuelistID" : { "type" : "valuelist", "tags": { "scope" :"design" }, "for": "dataProviderID"} 
	},
	"handlers":
	{
	        "onActionMethodID" : "function", 
	        "onDataChangeMethodID" : "function"
	},
	"api":
	{
	       
	}
	 
}