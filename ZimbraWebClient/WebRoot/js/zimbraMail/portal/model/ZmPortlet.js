/*
 * 
 */

/**
 * @overview
 * This file defines the portlet.
 */

/**
 * @class
 * This class provides the implementation for portlet for use in the portal application.
 * 
 * @param	{ZmList}	list	the list that contains this item (may be <code>null</code>)
 * @param	{String}	id		the portlet id
 * @param	{Object}	def		the portlet definition
 *
 * @extends	ZmItem
 * 
 * @see		ZmPortalApp
 */
ZmPortlet = function(list, id, def) {
    ZmItem.call(this, ZmItem.PORTLET, id, list);

    // save zimlet
    var zimletMgr = appCtxt.getZimletMgr();
    this.zimletName = def.zimlet;
    this.zimletCtxt = zimletMgr.getZimletsHash()[this.zimletName];
    this.zimlet = this.zimletCtxt && this.zimletCtxt.handlerObject;

    // save data
	this.global = /^true|on|yes$/i.test(def.global);
    this.icon = def.icon;
    this.title = def.title;
    if (this.title) {
        this.title = this.zimletCtxt ? this.zimletCtxt.processMessage(def.title) : def.zimlet;
    }
    var portlet = this.zimletCtxt && this.zimletCtxt.portlet;
    this.actionUrl = portlet && portlet.actionUrl;

    // merge default and specified properties
    this.properties = {};
    var defaultProps = portlet && portlet.portletProperties;
    for (var i in defaultProps) {
        var prop = defaultProps[i];
        this.properties[prop.name] = prop.value;
    }
    if (def.properties) {
        for (var i = 0; i < def.properties.length; i++) {
            var prop = def.properties[i];
            this.properties[prop.name] = prop._content;
        }
    }

	// string replacement
	if (this.zimletCtxt) {
		for (var pname in this.properties) {
			this.properties[pname] = this.zimletCtxt.replaceObj(ZmZimletContext.RE_SCAN_APP, this.properties[pname], ZmZimletContext.APP);
			// TODO: replace msg,obj,etc...
		}
	}

    // setup refresh interval
    if (this.actionUrl) {
        this.setRefreshInterval(this.actionUrl.refresh);
    }
}
ZmPortlet.prototype = new ZmItem;
ZmPortlet.prototype.constructor = ZmPortlet;

ZmPortlet.prototype.toString = function() { return "ZmPortlet"; }

//
// Data
//

/**
 * The view associated to this portlet. Type is ZmPortletView.
 *
 * @private
 */
ZmPortlet.prototype.view;

/**
 * @private
 */
ZmPortlet.prototype._refreshActionId = -1;

//
// Public methods
//

/**
 * Refreshes the portlet.
 * 
 */
ZmPortlet.prototype.refresh = function() {
    if (this.view) {
        this._refreshTime = new Date().getTime();
        if (this.actionUrl) {
            this.view.setContentUrl(this.actionUrl.target);
        }
        else if (this.zimlet instanceof ZmZimletBase) {
            this.zimlet.portletRefreshed(this);
        }
        else if (this.zimlet) {
            var text = AjxMessageFormat.format(ZmMsg.zimletNotLoaded, this.zimletName);
            this.setContent(text);
        }
        else {
            var text = AjxMessageFormat.format(ZmMsg.zimletUnknown, this.zimletName);
            this.setContent(text);
        }
    }
};

/**
 * Sets the refresh interval.
 * 
 * @param	{int}		interval		the refresh interval (in milliseconds)
 */
ZmPortlet.prototype.setRefreshInterval = function(interval) {
    if (this._refreshActionId != -1) {
        clearInterval(this._refreshActionId);
        this._refreshActionId = -1;
    }
    this._refreshInterval = interval;
    if (interval) {
        if (!this._refreshAction) {
            this._refreshAction = AjxCallback.simpleClosure(this.refresh, this);
        }
        this._refreshActionId = setInterval(this._refreshAction, interval);
    }
};

/**
 * Sets the content.
 * 
 * @param	{String}	content		the content
 */
ZmPortlet.prototype.setContent = function(content) {
    if (this.view) {
        this.view.setContent(content);
    }
    else {
        DBG.println("no view to set content ("+this.id+")");
    }
};

/**
 * Sets the content url.
 * 
 * @param	{String}	url		the content url
 */
ZmPortlet.prototype.setContentUrl = function(url) {
    if (this.view) {
        this.view.setContentUrl(url);
    }
    else {
        DBG.println("no view to set content url ("+this.id+")");
    }
};

/**
 * Sets the portlet to "paused".
 * 
 * @param	{Boolean}	paused		if <code>true</code>, pause the portlet
 */
ZmPortlet.prototype.setPaused = function(paused) {
    if (this._refreshActionId != -1 && paused) {
        this._pauseTime = new Date().getTime();
        clearInterval(this._refreshActionId);
        this._refreshActionId = -1;
    }
    else if (this._refreshInterval && !paused) {
        var delta = this._refreshInterval - (this._pauseTime - this._refreshTime);
        var delay = delta < this._refreshInterval ? delta : 0;
        var resumeAction = AjxCallback.simpleClosure(this._resumeRefresh, this);
        setTimeout(resumeAction, delay);
    }
};

//
// Protected methods
//

/**
 * @private
 */
ZmPortlet.prototype._resumeRefresh = function() {
    this.refresh();
    this._refreshActionId = setInterval(this._refreshAction, this._refreshInterval);
};