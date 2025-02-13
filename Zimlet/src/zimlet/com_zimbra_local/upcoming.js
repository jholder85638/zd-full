/*
 * 
 */
UpComingEvents = function() {
};

UpComingEvents.DEFAULT_RADIUS = 50;

UpComingEvents.prototype.searchEvents =
function(params) {
	var reqParams = ["api_key=ae7d801cfb&method=event.search"];

	if (params.query) {
		reqParams.push("search_text="+params.query);
	}

	if (params.latitude && params.longitude) {
		reqParams.push("location=" + params.latitude + "," + params.longitude);
	}

	reqParams.push("radius=" + (params.radius || UpComingEvents.DEFAULT_RADIUS));

	if (params.mindate) {
		reqParams.push("min_date=" + params.mindate);
	} else {
		var date = new Date();
		var minDate = [
			"min_date=",
			date.getFullYear(),
			"-",
			(date.getMonth() < 9) ? ("0"+(date.getMonth()+1)) : (date.getMonth()+1),
			"-",
			(date.getDate() < 10) ? ("0"+date.getDate()) : (date.getDate())
		];
		reqParams.push(minDate.join(""));
	}

	if (params.page) {
		reqParams.push("page=" + params.page);
	}

	var url = "http://upcoming.yahooapis.com/services/rest/?" + reqParams.join("&");
	var proxyURL = ZmZimletBase.PROXY + AjxStringUtil.urlComponentEncode(url) ;
	var callback = new AjxCallback(this, this._processSearchEventsResponse, params.callback);
	AjxRpc.invoke(reqParams, proxyURL, null, callback, true);
};

UpComingEvents.prototype._processSearchEventsResponse =
function(callback, result) {
	var events = this.xmlToObject(result).event;
	events = events.length ? events : [events];
	if (callback) {
		callback.run(events);
	}
};

UpComingEvents.prototype.xmlToObject =
function(result) {
	try {
		var xd = new AjxXmlDoc.createFromDom(result.xml).toJSObject(true, false, true);
	} catch(ex) {
		//this.displayErrorMessage(ex, result.text, "Problem contacting Snapfish");
	}
	return xd;
};
