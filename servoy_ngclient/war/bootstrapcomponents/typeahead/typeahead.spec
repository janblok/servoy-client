{
	"name": "bootstrapcomponents-typeahead",
	"displayName": "Type Ahead ",
	"version": 1,
	"icon": "servoydefault/typeahead/bhdropdownlisticon.gif",
	"definition": "bootstrapcomponents/typeahead/typeahead.js",
	"libraries": [],
	"model":
	{
	        "dataProviderID" : { "type":"dataprovider", "tags": { "scope" :"design" }, "ondatachange": { "onchange":"onDataChangeMethodID", "callback":"onDataChangeCallback"}}, 
	        "format" : {"for":"dataProviderID" , "type" :"format"}, 
	        "styleClass" : { "type" :"styleclass", "tags": { "scope" :"design" }, "default" : "form-control"}, 
	        "valuelistID" : { "type" : "valuelist", "tags": { "scope" :"design" }, "for": "dataProviderID", "default":"autoVL", "canOptimize":false}
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