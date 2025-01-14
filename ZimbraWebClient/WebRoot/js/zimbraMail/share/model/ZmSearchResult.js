/*
 * 
 */

/**
 * @overview
 * This file contains the search result class.
 */

/**
 * Creates the search result
 * @class
 * This class represents a search result.
 * 
 * @param	{ZmSearch}	search		the search
 */
ZmSearchResult = function(search) {
	if (!search) { return; }
	this._results = {};
	this.search = search;
};

/**
 * Returns a string representation of the object.
 * 
 * @return		{String}		a string representation of the object
 */
ZmSearchResult.prototype.toString = 
function() {
	return "ZmSearchResult";
};

/**
 * @private
 */
ZmSearchResult.prototype.dtor = 
function() {
	for (var type in this._results) {
		if (this._results[type].clear) {
			this._results[type].clear();
			this._results[type] = null;
		}
	}
	this._results = null;
};

/**
 * Gets the results.
 * 
 * @param	{constant}	type	the type
 * @return	{Array}	an array of results
 */
ZmSearchResult.prototype.getResults =
function(type) {

	type = type || this.type;
	if (!this._results) {
		// probably got an exception - return an empty list
		return ZmItem.RESULTS_LIST[type](this.search);
	}
	if (type == ZmItem.MIXED) {
		// mail list - lazy way to make it easy to do mail ops on items
		var list = new ZmMailList(ZmItem.MIXED, this.search);
		for (var type in this._results) {
			var results = this._results[type];
			if (results && results.size()) {
				var a = results.getArray();
				for (var j = 0; j < a.length; j++) {
					list.add(a[j]);
				}
			}
		}
		return list;
	} else if (this.search.idsOnly) {
		return this._results;
	} else {
		// if we don't have results for the requested type, the search was probably for the wrong type
		return this._results[type] ? this._results[type] : type && ZmItem.RESULTS_LIST[type](this.search);
	}
};

/**
 * Gets the attribute.
 * 
 * @param	{String}	name		the attribute name
 * @return	{Object}	the attribute
 */
ZmSearchResult.prototype.getAttribute = 
function(name) {
	return this._respEl ? this._respEl[name] : null;
};

/**
 * Sets the response.
 * 
 * @private
 */
ZmSearchResult.prototype.set =
function(respEl) {

	if (!this.search) { return; }

	this._respEl = respEl;

	// <match> objects are returned for autocomplete search, not items; let caller handle them
	if (this.search.isAutocompleteSearch) { return; }

	var foundType = {};
	var numTypes = 0;
	var currentType, defaultType;
	var isGalSearch = this.search.isGalSearch;
	
	var _st = new Date();
	var count = 0;
	if (isGalSearch || this.search.isCalResSearch) {
		// process JS eval result for SearchGalRequest
		currentType = defaultType = isGalSearch ? ZmItem.CONTACT : ZmItem.RESOURCE;
		var data = isGalSearch ? respEl.cn : respEl.calresource;
		if (data) {
			if (!this._results[currentType]) {
				// create list as needed - may invoke package load
				this._results[currentType] = ZmItem.RESULTS_LIST[currentType](this.search);
			}
			for (var j = 0; j < data.length; j++) {
				this._results[currentType].addFromDom(data[j]);
			}

			count = data.length;
		}
	} else if (this.search.idsOnly) {
		this._results = respEl.hit || [];
		return;
	} else {
		// process JS eval result for SearchResponse
		var types = this.search.types.getArray();
		defaultType = types[0];

		// bug fix #44232 - resolve default type if none provided
		if (!defaultType) {
			var allTypes = AjxUtil.values(ZmList.NODE);
			for (var i = 0; i < allTypes.length; i++) {
				var t = allTypes[i];
				if (respEl[t]) {
					defaultType = ZmList.ITEM_TYPE[t];
					if (types && types.length == 0) {
						types = [defaultType];
					}
					break;
				}
			}
		}

		if (!defaultType) {
			var curApp = appCtxt.getCurrentAppName();
			var types = ZmApp.SEARCH_TYPES[curApp];
			defaultType = types && types.length && types[0];
		}

		for (var i = 0; i < types.length; i++) {
			var type = types[i];
			var data = respEl[ZmList.NODE[type]];

			// A chat isa message. Futz with the types to deal with this.
			// (Eventually we'll avoid this problem by displying chat history in im app.)
			if (!data && (type == ZmItem.MSG)) {
				data = respEl["chat"];
			}

			// do a bunch of sanity checks
			if (data && data.length) {
				count += data.length;
				if (!this._results[type]) {
					// create list as needed - may invoke package load
					this._results[type] = ZmItem.RESULTS_LIST[type](this.search);
				}
				for (var j = 0; j < data.length; j++) {
					var item = data[j];
					item._type = type;
					this._results[type].addFromDom(item);
				}

				if (!foundType[type]) {
					foundType[type] = true;
					numTypes++;
					currentType = type;
				}
			}
		}
	}
	if (!count && defaultType) {
		this._results[defaultType] = ZmItem.RESULTS_LIST[defaultType](this.search);
	}
	if ((isGalSearch || this.search.isGalAutocompleteSearch) && this._results[ZmItem.CONTACT]) {
		this._results[ZmItem.CONTACT].setIsGal(true);
	}
	if (this.search.isGalAutocompleteSearch) {
		this.isTokenized = (this._respEl.tokenizeKey != null);
	}
	
	var _en = new Date();
	DBG.println(AjxDebug.DBG1, "TOTAL PARSE TIME for " + count + " NODES: " + (_en.getTime() - _st.getTime()));

	currentType = currentType || defaultType;
	if (numTypes <= 1) {
		this.type = currentType;
	} else if (numTypes == 2 && (foundType[ZmItem.PAGE] || foundType[ZmItem.DOCUMENT])) {
		this.type = ZmItem.PAGE;	// notebook search can return either/both
	} else {
		this.type = appCtxt.get(ZmSetting.MIXED_VIEW_ENABLED) ? ZmItem.MIXED : currentType;
	}

	return this.type;
};

/**
 * @private
 */
ZmSearchResult._sortGalResults =
function(a, b) {
	var af = a.getFileAs && a.getFileAs().toLowerCase();
	var bf = b.getFileAs && b.getFileAs().toLowerCase();
	return af < bf ? -1 : (af > bf ? 1 : 0);
};
