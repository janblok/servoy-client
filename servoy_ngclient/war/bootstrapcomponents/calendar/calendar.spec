{
	"name": "bootstrapcomponents-calendar",
	"displayName": "Calendar",
	"version": 1,
	"icon": "servoydefault/calendar/Calendar_C16.png",
	"definition": "bootstrapcomponents/calendar/calendar.js",
	"libraries": [{"name":"moment", "version":"2.6.0", "url": "bootstrapcomponents/calendar/bootstrap-datetimepicker/js/moment.min.js", "mimetype":"text/javascript"},{"name":"moment-jdateformatparser", "version":"0.1.1", "url":"bootstrapcomponents/calendar/bootstrap-datetimepicker/js/moment-jdateformatparser.js", "mimetype":"text/javascript"},{"name":"bootstrap-datetimepicker", "version":"4.0.0b", "url":"bootstrapcomponents/calendar/bootstrap-datetimepicker/js/bootstrap-datetimepicker.js", "mimetype":"text/javascript"},{"name":"bootstrap-datetimepicker", "version":"4.0.0b", "url":"bootstrapcomponents/calendar/bootstrap-datetimepicker/css/bootstrap-datetimepicker.css", "mimetype":"text/css"}],
	"model":
	{
	        "dataProviderID" : { "type":"dataprovider", "tags": { "scope" :"design" }, "ondatachange": { "onchange":"onDataChangeMethodID", "callback":"onDataChangeCallback"}}, 
	        "format" : {"for":"dataProviderID" , "type" :"format"}, 
	        "styleClass" : { "type" :"styleclass", "tags": { "scope" :"design" }, "default" : "form-control"}
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