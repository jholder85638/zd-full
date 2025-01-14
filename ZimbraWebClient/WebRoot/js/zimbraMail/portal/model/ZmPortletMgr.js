/*
 * 
 */

/**
 * Creates the portlet manager.
 * @class
 * This class represents the portlet manager.
 * 
 * @see		ZmPortalApp
 * @see		ZmPortlet
 */
ZmPortletMgr = function() {
    this._portlets = {};
    this._loadedZimlets = {};
    this._delayedPortlets = {};
};

//
// Public methods
//

/**
 * Creates the portlets.
 * 
 * @param	{Boolean}	global			if <code>true</code>, create global portlets
 * @param	{Object}	manifest		the portal manifest
 */
ZmPortletMgr.prototype.createPortlets = function(global, manifest) {
	global = global != null ? global : false;
	var portletsCreated = [];
    manifest = manifest || appCtxt.getApp(ZmApp.PORTAL).getManifest();
    if (manifest) {
        var portalDef = manifest.portal;
        var portletDefs = portalDef && portalDef.portlets;
        if (portletDefs) {
            for (var i = 0; i < portletDefs.length; i++) {
                var portletDef = portletDefs[i];
				var portletGlobal = portletDef.global == "true";
				if (portletGlobal != global) continue;
                
                var id = portletDef.panel && portletDef.panel.id;
                if (id && !this._portlets[id] && document.getElementById(id)) {
                    this.createPortlet(id, portletDef);
                    portletsCreated.push(id);
                }
            }
        }
    }
    return portletsCreated;
};

/**
 * Creates the portlet.
 * 
 * @param	{String}	id		the portlet id
 * @param	{Object}	portletDef		the portlet definition
 * 
 * @return	{ZmPortlet}	the newly created portlet
 */
ZmPortletMgr.prototype.createPortlet = function(id, portletDef) {
    // create portlet
    var portlet = new ZmPortlet(null, id, portletDef);
    this._portlets[id] = portlet;

    // notify portlet creation or add to list to notify later
	var name = portlet.zimletName;
	if (this._loadedZimlets[name]) {
		this._portletCreated(portlet);
	}
	else if (name) {
		if (!this._delayedPortlets[name]) {
			this._delayedPortlets[name] = [];
		}
		this._delayedPortlets[name].push(portlet);
	}

    return portlet;
};

/**
 * Gets the portlets.
 * 
 * @return	{Array}		an array of {@link ZmPortlet} objects
 */
ZmPortletMgr.prototype.getPortlets = function() {
    return this._portlets;
};

/**
 * Gets the portlet by id.
 * 
 * @param	{String}	id		the portlet id
 * @return	{ZmPortlet}	the portlet
 */
ZmPortletMgr.prototype.getPortletById = function(id) {
    return this._portlets[id];
};

/**
 * This method is called by ZmZimletContext after the source code for
 * the zimlet is loaded.
 * 
 * @private
 */
ZmPortletMgr.prototype.zimletLoaded = function(zimletCtxt) {
    this._loadedZimlets[zimletCtxt.name] = true;

    var delayedPortlets = this._delayedPortlets[zimletCtxt.name];
    if (delayedPortlets) {
        for (var i = 0; i < delayedPortlets.length; i++) {
            var portlet = delayedPortlets[i];
            this._portletCreated(portlet, zimletCtxt);
        }
    }
    delete this._delayedPortlets[zimletCtxt.name];
};

/**
 * This method is called after all of the zimlets have been loaded. It is
 * a way for the portlet manager to know that there are no more zimlets
 * expected.
 * 
 * @private
 */
ZmPortletMgr.prototype.allZimletsLoaded = function() {
	for (var name in this._portlets) {
		var portlet = this._portlets[name];
		if (!this._loadedZimlets[portlet.zimletName]) {
			// NOTE: We don't call setContent because there is no view object
			//       if no zimlet code was loaded.
			var el = document.getElementById(portlet.id);
			if (el) {
				el.innerHTML = "";
			}
		}
	}
};

//
// Protected methods
//

ZmPortletMgr.prototype._portletCreated = function(portlet, zimletCtxt) {
    // get zimlet context, if needed
    if (!zimletCtxt) {
        zimletCtxt = appCtxt.getZimletMgr().getZimletsHash()[portlet.zimletName];
    }

    // create view
    var parentEl = document.getElementById(portlet.id);
    var view = new ZmPortletView(parentEl, portlet);

    // call portlet handler
    var handler = zimletCtxt.handlerObject;
    portlet.zimlet = handler;
    if (handler) {
        handler.portletCreated(portlet);
    }
};