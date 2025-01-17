{
	"name": "bootstrapcomponents-progressbar",
	"displayName": "Progress Bar",
	"version": 1,
	"definition": "bootstrapcomponents/progressbar/progressbar.js",
	"libraries": [],
	"model":
	{
			"styleClass" : { "type" :"styleclass", "tags": { "scope" :"design" }, "default" : "progress-striped" , "values" :["progress-striped", "progress-striped active"]},
			"value": "float",
			"type": {"type": "string", "default":"info","values":["info", "success", "warning", "danger"]},
			"animate": {"type":"boolean", "default":true},
			"showValue" : {"type":"boolean", "default":true},
			"max" : {"type":"int", "default":100}
	},
	"handlers":
	{
	
	},
	"api":
	{   
	
	} 
	
}