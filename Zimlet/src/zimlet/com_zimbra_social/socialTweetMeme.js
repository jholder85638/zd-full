/*
 * 
 */

//Author: Raja Rao DV (rrao@zimbra.com)

function com_zimbra_socialTweetMeme(zimlet) {
	this.zimlet = zimlet;
}

com_zimbra_socialTweetMeme.prototype.loadTweetMemeCategories =
function() {
	this.allTweetMemeCats = new Array();
	this.allTweetMemeCats.push({query:"__MOST_POPULAR__", name:this.zimlet.getMessage("mostPopular")});
	this.allTweetMemeCats.push({query:"__MOST_RECENT__", name:this.zimlet.getMessage("mostRecent")});
	this.allTweetMemeCats.push({query:"Technology", name:this.zimlet.getMessage("technology")});
	this.allTweetMemeCats.push({query:"Entertainment", name:this.zimlet.getMessage("entertainment")});
	this.allTweetMemeCats.push({query:"Science", name:this.zimlet.getMessage("science")});
	this.allTweetMemeCats.push({query:"Sports", name:this.zimlet.getMessage("sports")});

	if (this.zimlet.preferences.social_pref_tweetmemePopularIsOn) {
		for (var i = 0; i < 1; i++) {
			var folder = this.allTweetMemeCats[i];
			var tableId = this.zimlet._showCard({headerName:folder.name, type:"TWEETMEME", autoScroll:false});
			this.tweetMemeSearch({query:folder.query, tableId:tableId});
		}
	}
	this.zimlet._updateAllWidgetItems({updateTweetMemeTree:true});
};

com_zimbra_socialTweetMeme.prototype._getQueryFromHeaderName =
function(headerName) {
	for(var i =0; i < this.allTweetMemeCats.length; i++) {
		var cat = this.allTweetMemeCats[i];
		if(cat.name == headerName) {
			return cat.query;
		}
	}
	return "__MOST_POPULAR__";
};

com_zimbra_socialTweetMeme.prototype.tweetMemeSearch =
function(params) {
	var headerName = params.headerName;
	var query = this._getQueryFromHeaderName(headerName);
	var url = "";
	if (query == "__MOST_POPULAR__")
		url = "http://api.tweetmeme.com/stories/popular.json?";
	else if (query == "__MOST_RECENT__")
		url = "http://api.tweetmeme.com/stories/recent.json";
	else
		url = "http://api.tweetmeme.com/stories/popular.json?category=" + AjxStringUtil.urlComponentEncode(query);

	var entireurl = ZmZimletBase.PROXY + AjxStringUtil.urlComponentEncode(url);
	AjxRpc.invoke(null, entireurl, null, new AjxCallback(this, this._tweetMemeSearchCallback, params), true);
};
com_zimbra_socialTweetMeme.prototype._tweetMemeSearchCallback =
function(params, response) {
	var jsonObj = this.zimlet._extractJSONResponse(params.tableId, this.zimlet.getMessage("tweetMemeError"), response);
	if(jsonObj.stories) {
		jsonObj = jsonObj.stories;
	}
	this.zimlet.createCardView({tableId:params.tableId, items:jsonObj, type:"TWEETMEME"});
};