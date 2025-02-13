/*
 * 
 */

//Author: Raja Rao DV (rrao@zimbra.com)

function com_zimbra_socialDigg(zimlet) {
	this.zimlet = zimlet;
}

com_zimbra_socialDigg.prototype.getDiggCategories =
function() {
	this.allDiggCats = new Array();
	this.allDiggCats.push({query:"Popular in 24hours", name:this.zimlet.getMessage("popularIn24Hours")});
	this.allDiggCats.push({query:"technology", name:this.zimlet.getMessage("technology")});
	this.allDiggCats.push({query:"science", name:this.zimlet.getMessage("science")});
	this.allDiggCats.push({query:"sports", name:this.zimlet.getMessage("sports")});
	this.allDiggCats.push({query:"entertainment", name:this.zimlet.getMessage("entertainment")});

	if (this.zimlet.preferences.social_pref_diggPopularIsOn) {
		for (var i = 0; i < 1; i++) {
			var folder = this.allDiggCats[i];
			var tableId = this.zimlet._showCard({headerName:folder.name, type:"DIGG", autoScroll:false});
			this.diggSearch({query:folder.query, tableId:tableId});
		}
	}
	this.zimlet._updateAllWidgetItems({updateDiggTree:true});
};

com_zimbra_socialDigg.prototype._getQueryFromHeaderName =
function(headerName) {
	for(var i =0; i < this.allDiggCats.length; i++) {
		var cat = this.allDiggCats[i];
		if(cat.name == headerName) {
			return cat.query;
		}
	}
	return "Popular in 24hours";
};

com_zimbra_socialDigg.prototype.diggSearch =
function(params) {
	var headerName = params.headerName;
	var query = this._getQueryFromHeaderName(headerName);
	var url = "";
	var tmp = new Date();
	var time = ((new Date(tmp.getFullYear(), tmp.getMonth(), tmp.getDate())).getTime() - 3600 * 24 * 1000) / 1000;
	var args = "min_promote_date=" + time + "&sort=digg_count-desc&appkey=http%3A%2F%2Fwww.zimbra.com&count=20&type=json";
	if (query == "Popular in 24hours")
		url = "http://services.digg.com/stories/popular?" + args;
	else
		url = "http://services.digg.com/stories/container/" + query + "/popular?" + args;

	var entireurl = ZmZimletBase.PROXY + AjxStringUtil.urlComponentEncode(url);
	AjxRpc.invoke(null, entireurl, null, new AjxCallback(this, this._DiggSearchCallback, params), true);
};

com_zimbra_socialDigg.prototype._DiggSearchCallback =
function(params, response) {
	var jsonObj = this.zimlet._extractJSONResponse(params.tableId, this.zimlet.getMessage("diggError"), response);
	if(jsonObj.stories) {
		jsonObj = jsonObj.stories;
	}
	this.zimlet.createCardView({tableId:params.tableId, items:jsonObj, type:"DIGG"});
};