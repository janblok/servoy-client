angular.module('window',['servoy'])
.factory("window",function($window,$services,$compile,$formService) {
	var scope = $services.getServiceScope('window');
	return {
		createShortcut: function(shortcutcombination,callback,contextFilter,args) {
			if (contextFilter instanceof Array)
			{
				args = contextFilter;
				contextFilter = null;
			}
			shortcut.add(this.translateSwingShortcut(shortcutcombination),this.getCallbackFunction(callback, contextFilter,args),{'propagate':true,'disable_in_input':false});
			if (!scope.model.shortcuts) scope.model.shortcuts = [];
			scope.model.shortcuts.push({'shortcut': shortcutcombination,'callback':callback,'contextFilter':contextFilter,'arguments':args});
			return true;
		},
		removeShortcut: function(shortcutcombination) {
			shortcut.remove(this.translateSwingShortcut(shortcutcombination));
			if (scope.model.shortcuts)
			{
				for (var i=0;i<scope.model.shortcuts.length;i++)
				{
					if (scope.model.shortcuts[i].shortcut == shortcutcombination)
					{
						scope.model.shortcuts.splice(i,1);
						break;
					}
				}
			}
			return true;
		},
		translateSwingShortcut: function(shortcutcombination)
		{
			var shortcutParts = shortcutcombination.split(" ");
			var translatedShortcut = '';
			for (var i=0;i<shortcutParts.length;i++)
			{
				if (i>0)
				{
					translatedShortcut +='+';
				}
				if (shortcutParts[i] == 'control' || shortcutParts[i] == 'ctrl')
				{
					translatedShortcut +='CTRL';
				}
				else if (shortcutParts[i] == 'meta')
				{
					translatedShortcut +='META';
				}
				else if (shortcutParts[i] == 'shift')
				{
					translatedShortcut +='SHIFT';
				}
				else if (shortcutParts[i] == 'alt')
				{
					translatedShortcut +='ALT';
				}
				else
				{
					translatedShortcut +=shortcutParts[i];
				}
			}
			return translatedShortcut;
		},
		getCallbackFunction: function(callback,contextFilter,args)
		{
			return function(e){
				if (contextFilter)
				{
					var element;
					if(e.target) element=e.target;
					else if(e.srcElement) element=e.srcElement;
					var parent = element;
					while (parent)
					{
						var form = parent.getAttribute("ng-controller");
						if (form)
						{
							if (form != contextFilter && form != 'MainController') return;
							break;
						}
						parent = parent.parentNode;
					}
				}
				$window.executeInlineScript(callback.formname,callback.script,args);
			};
		},
		showFormPopup : function(component,form,width,height)
		{
			$formService.showForm(form);
			scope.getFormUrl = function(){
				return $formService.getFormUrl(form);
			};
			var body = $('body');
			var style = 'position:absolute;z-index:999;';
			if (width && width > 0)
			{
				style+= 'width:'+width+'px;'
			}
			if (height && height > 0)
			{
				style+= 'height:'+height+'px;'
			}
			var left = $( window ).width() /2;
			var top = $( window ).height() /2;
			if (component)
			{
				var position = $('#'+component).offset();
				left = position.left;
				top = position.top+$('#'+component).outerHeight();
			}
			style+= 'left:'+left+'px;';
			style+= 'top:'+top+'px;';
			var popup = $compile('<div id=\'formpopup\' style="'+style+'" ng-include="getFormUrl()"></div>')(scope);
			body.append(popup);
		},
		cancelFormPopup : function()
		{
			if (scope.model.popupform)
			{
				$formService.hideForm(scope.model.popupform.form);
			}
			var popup = $("#formpopup");
			if (popup)
			{
				popup.remove();
			}
			scope.model.popupform = null;
		},
		generateMenu: function(items,oMenu)
		{
			for (var j=0;j<items.length;j++)
			{
				var groupCount = 1;
				if (items[j])
				{
					var text = items[j].text;
					if (items[j].visible == false || !text) continue;
					var cssClass = items[j].cssClass;
					if (cssClass == 'img_checkbox' && items[j].selected != true)
					{
						// not selected checkbox
						cssClass = null;
					}
					if (cssClass == 'img_radio_off' && items[j].selected == true)
					{
						// selected radio
						cssClass = "img_radio_on";
					}
					if (items[j].icon || cssClass)
					{
						var htmltext = "<html><table><tr>";
						if (items[j].cssClass) htmltext += "<td class=\"" + cssClass + "\">&nbsp;&nbsp;&nbsp;&nbsp;</td>"; 
						if (items[j].icon) htmltext += "<td><img src=\"" + items[j].icon + "\" style=\"border:none\"/></td>";
						htmltext += "<td>" + text + "</td>"; 
						htmltext += "</tr></table></html>";
						text = htmltext;
					}
					var mi = new YAHOO.widget.MenuItem(text);
					if (items[j].callback) {
						mi.cfg.setProperty('onclick', {fn:function(index) {return function(){
							$window.executeInlineScript(items[index].callback.formname,items[index].callback.script,items[index].args)
							}}(j)
						});
					}
					if (items[j].enabled == false)
					{
						mi.cfg.setProperty('disabled', true);
					}
					if (items[j].backgroundColor)
					{
						YAHOO.util.Dom.setStyle(mi.element, 'background-color', items[j].backgroundColor);
					}
					if (items[j].foregroundColor)
					{
						var menuLabel = YAHOO.util.Dom.getElementsByClassName('yuimenuitemlabel', 'a', mi.element);
						if(menuLabel) { YAHOO.util.Dom.setStyle(menuLabel, 'color', items[j].foregroundColor)};
					}
					oMenu.addItem(mi,groupCount);
					if (items[j].items && items[j].items.length >0)
					{
						var menu = new YAHOO.widget.Menu(items[j].text);
						this.generateMenu(items[j].items,menu);
						mi.cfg.setProperty('submenu', menu);
					}
				}
				else
				{
					// separator
					groupCount++;
				}
			}
		}
	}
})
.run(function($rootScope,$services,window,$window, $timeout)
{
	var scope = $services.getServiceScope('window');
	scope.$watch('model', function(newvalue,oldvalue) {
		if (newvalue && newvalue.shortcuts && newvalue.shortcuts.length > 0 && Object.keys(shortcut.all_shortcuts).length == 0)
		{
			// handle just refresh page case, need to reinstall all shortcuts again
			for (var i=0;i<newvalue.shortcuts.length;i++)
			{
				shortcut.add(window.translateSwingShortcut(newvalue.shortcuts[i].shortcut),window.getCallbackFunction(newvalue.shortcuts[i].callback,newvalue.shortcuts[i].contextFilter,newvalue.shortcuts[i].arguments),{'propagate':true,'disable_in_input':false});
			}
		}
		if (newvalue && newvalue.popupform)
		{
			window.showFormPopup(newvalue.popupform.component,newvalue.popupform.form,newvalue.popupform.width,newvalue.popupform.height);
		}
		if (newvalue && newvalue.popupMenuShowCommand)
		{
			var oMenu = new YAHOO.widget.Menu('basicmenu',{zIndex : 1000});
			oMenu.clearContent();
			oMenu.subscribe('hide', function (event) {
				$timeout(function(){
					scope.model.popupMenuShowCommand = null;
					oMenu.destroy();
				},1);
			});
			if (newvalue.popupMenus && newvalue.popupMenuShowCommand.popupName)
			{
				for (var i=0;i<newvalue.popupMenus.length;i++)
				{
					if (newvalue.popupMenuShowCommand.popupName == newvalue.popupMenus[i].name)
					{
						var popup = newvalue.popupMenus[i];
						window.generateMenu(popup.items,oMenu)
						var element;
						if (newvalue.popupMenuShowCommand.elementId)
						{
							element = document.getElementById(newvalue.popupMenuShowCommand.elementId);
						}
						if(element)
						{
							var parentReg = YAHOO.util.Dom.getRegion(element.offsetParent);
							var jsCompReg = YAHOO.util.Dom.getRegion(element);
							oMenu.render(document.body);
							var oMenuReg = YAHOO.util.Dom.getRegion(document.getElementById(oMenu.id));
							if(element.offsetParent && (jsCompReg.top + newvalue.popupMenuShowCommand.y + (oMenuReg.bottom - oMenuReg.top) > parentReg.top + (parentReg.bottom - parentReg.top)) && 
									(jsCompReg.top + newvalue.popupMenuShowCommand.y - (oMenuReg.bottom - oMenuReg.top) > parentReg.top))
							{
								oMenu.moveTo(jsCompReg.left  + newvalue.popupMenuShowCommand.x, jsCompReg.top -(oMenuReg.bottom - oMenuReg.top));
							}
							else
							{
								oMenu.moveTo(jsCompReg.left  + newvalue.popupMenuShowCommand.x, jsCompReg.top + newvalue.popupMenuShowCommand.y);
							}
						}
						else
						{
							oMenu.render(document.body);
							oMenu.moveTo(newvalue.popupMenuShowCommand.x,newvalue.popupMenuShowCommand.y);
						}
						oMenu.show();
						break;
					}
				}
			}
		}
	}, true);
})