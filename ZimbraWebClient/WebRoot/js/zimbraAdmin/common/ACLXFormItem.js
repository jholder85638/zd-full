/*
 * 
 */

/**
* XFormItem class: "acl (composite item)
* this item is used in the Admin UI to display ACL fields like Notebook folder access rights
* @class ACLXFormItem
* @constructor ACLXFormItem
* @author Greg Solovyev
**/
ACLXFormItem = function() {}
XFormItemFactory.createItemType("_ACL_", "acl", ACLXFormItem, Composite_XFormItem);
ACLXFormItem.prototype.numCols = 6;
ACLXFormItem.prototype.nowrap = true;
ACLXFormItem.prototype.visibleBoxes = {r:true,w:true,a:true,i:true,d:true,x:true};
ACLXFormItem.prototype.initializeItems = 
function () {
	var visibleBoxes = this.getInheritedProperty("visibleBoxes");
	this.items = [];
	if(visibleBoxes.r)
		this.items.push(	
			{type:_CHECKBOX_,width:"40px",containerCssStyle:"width:40px", forceUpdate:true, ref:".", 
				labelLocation:_RIGHT_, label:ZaMsg.ACL_R, 
				visibilityChecks:[],enableDisableChecks:[],
				getDisplayValue:function (itemval) {
					return (itemval && itemval["r"]==1);
				},
				elementChanged:function(isChecked, instanceValue, event) {
					var newVal = Object();

					if(instanceValue) {
						newVal["w"] = instanceValue["w"];
						newVal["a"] = instanceValue["a"];
						newVal["i"] = instanceValue["i"];
						newVal["d"] = instanceValue["d"];			
						newVal["x"] = instanceValue["x"];			
					} else {
						newVal = {r:0,w:0,i:0,d:0,a:0,x:0};
					}
					newVal["r"] = isChecked ? 1 : 0;
					this.getForm().itemChanged(this.getParentItem(), newVal, event);
				}
			}
		);

	if(visibleBoxes.w)
		this.items.push(	
			{type:_CHECKBOX_,width:"40px",containerCssStyle:"width:40px", forceUpdate:true, ref:".", 
				labelLocation:_RIGHT_, label:ZaMsg.ACL_W,
				visibilityChecks:[],enableDisableChecks:[],
				getDisplayValue:function (itemval) {
					return (itemval && itemval["w"]==1);
				},
				elementChanged:function(isChecked, instanceValue, event) {
					var newVal = Object();
					if(instanceValue) {					
						newVal["r"] = instanceValue["r"];
						newVal["a"] = instanceValue["a"];
						newVal["i"] = instanceValue["i"];
						newVal["d"] = instanceValue["d"];			
						newVal["x"] = instanceValue["x"];			
					} else {
						newVal = {r:0,w:0,i:0,d:0,a:0,x:0};
					}
					newVal["w"] = isChecked ? 1 : 0;
					this.getForm().itemChanged(this.getParentItem(), newVal, event);
				}
			}
		);

	if(visibleBoxes.d)
		this.items.push(	
			{type:_CHECKBOX_,width:"40px",containerCssStyle:"width:40px", forceUpdate:true, ref:".", 
				labelLocation:_RIGHT_, label:ZaMsg.ACL_D,
				visibilityChecks:[],enableDisableChecks:[],
				getDisplayValue:function (itemval) {
					return (itemval && itemval["d"]==1);
				},
				elementChanged:function(isChecked, instanceValue, event) {
					var newVal = Object();

					if(instanceValue) {										
						newVal["w"] = instanceValue["w"];
						newVal["a"] = instanceValue["a"];
						newVal["i"] = instanceValue["i"];
						newVal["r"] = instanceValue["r"];			
						newVal["x"] = instanceValue["x"];			
					} else {
						newVal = {r:0,w:0,i:0,d:0,a:0,x:0};
					}
					newVal["d"] = isChecked ? 1 : 0;
					this.getForm().itemChanged(this.getParentItem(), newVal, event);
				}
			}
		);
		
	if(visibleBoxes.i)
		this.items.push(	
			{type:_CHECKBOX_,width:"40px",containerCssStyle:"width:40px", forceUpdate:true, ref:".", 
				labelLocation:_RIGHT_, label:ZaMsg.ACL_I,
				visibilityChecks:[],enableDisableChecks:[],
				getDisplayValue:function (itemval) {
					return (itemval && itemval["i"]==1);
				},
				elementChanged:function(isChecked, instanceValue, event) {
					var newVal = Object();

					if(instanceValue) {				
						newVal["w"] = instanceValue["w"];
						newVal["a"] = instanceValue["a"];
						newVal["r"] = instanceValue["r"];
						newVal["d"] = instanceValue["d"];			
						newVal["x"] = instanceValue["x"];			
					} else {
						newVal = {r:0,w:0,i:0,d:0,a:0,x:0};
					}
					newVal["i"] = isChecked ? 1 : 0;
					this.getForm().itemChanged(this.getParentItem(), newVal, event);
				}
			}
		);		
		
	if(visibleBoxes.x)
		this.items.push(	
			{type:_CHECKBOX_,width:"40px",containerCssStyle:"width:40px", forceUpdate:true, ref:".", 
				labelLocation:_RIGHT_, label:ZaMsg.ACL_X, 
				visibilityChecks:[],enableDisableChecks:[],
				getDisplayValue:function (itemval) {
					return (itemval && itemval["x"]==1);
				},
				elementChanged:function(isChecked, instanceValue, event) {
					var newVal = Object();
					if(instanceValue) {				
						newVal["w"] = instanceValue["w"];
						newVal["a"] = instanceValue["a"];
						newVal["i"] = instanceValue["i"];
						newVal["d"] = instanceValue["d"];			
						newVal["r"] = instanceValue["r"];			
					} else {
						newVal = {r:0,w:0,i:0,d:0,a:0,x:0};
					}
					newVal["x"] = isChecked ? 1 : 0;
					this.getForm().itemChanged(this.getParentItem(), newVal, event);
				}
			}
		);	
		
	if(visibleBoxes.a)
		this.items.push(	
			{type:_CHECKBOX_,width:"40px",containerCssStyle:"width:40px", forceUpdate:true, ref:".", 
				labelLocation:_RIGHT_, label:ZaMsg.ACL_A, 
				visibilityChecks:[],enableDisableChecks:[],
				getDisplayValue:function (itemval) {
					return (itemval && itemval["a"]==1);
				},
				elementChanged:function(isChecked, instanceValue, event) {
					var newVal = Object();
					if(instanceValue) {				
						newVal["w"] = instanceValue["w"];
						newVal["r"] = instanceValue["r"];
						newVal["i"] = instanceValue["i"];
						newVal["d"] = instanceValue["d"];			
						newVal["x"] = instanceValue["x"];			
					} else {
						newVal = {r:0,w:0,i:0,d:0,a:0,x:0};
					}
					newVal["a"] = isChecked ? 1 : 0;
					this.getForm().itemChanged(this.getParentItem(), newVal, event);
				}
			}
		);				
	Composite_XFormItem.prototype.initializeItems.call(this);
};

ACLXFormItem.prototype.items = [];

