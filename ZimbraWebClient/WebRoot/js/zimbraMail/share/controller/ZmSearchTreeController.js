/*
 * 
 */

/**
 * @overview
 * This file defines the search tree controller.
 *
 */

/**
 * Creates a search tree controller.
 * @class
 * This class controls a tree display of saved searches.
 *
 * @author Conrad Damon
 * 
 * @extends		ZmFolderTreeController
 */
ZmSearchTreeController = function() {

	ZmFolderTreeController.call(this, ZmOrganizer.SEARCH);

	this._listeners[ZmOperation.RENAME_SEARCH] = new AjxListener(this, this._renameListener);
    this._listeners[ZmOperation.BROWSE] = new AjxListener(this, this._browseListener);
};

ZmSearchTreeController.prototype = new ZmFolderTreeController;
ZmSearchTreeController.prototype.constructor = ZmSearchTreeController;

ZmSearchTreeController.APP_JOIN_CHAR = "-";

// Public methods

/**
 * Returns a string representation of the object.
 * 
 * @return		{String}		a string representation of the object
 */
ZmSearchTreeController.prototype.toString = 
function() {
	return "ZmSearchTreeController";
};

/**
 * Shows the tree of this type.
 *
 * @param	{Hash}	params		a hash of parameters
 * @param	{String}	params.overviewId		the overview ID
 * @param	{Boolean}	params.showUnread		if <code>true</code>, unread counts will be shown
 * @param	{Array}	params.omit				a hash of organizer IDs to ignore
 * @param	{Boolean}	params.forceCreate	if <code>true</code>, tree view will be created
 * @param	{ZmZimbraAccount}	params.account	the account to show tree for (if not currently active account)
 * 
 */
ZmSearchTreeController.prototype.show =
function(params) {
	var id = params.overviewId;
	if (!this._treeView[id] || params.forceCreate) {
		this._treeView[id] = this._setup(id);
	}
	// mixed app should be filtered based on the previous app!
    var dataTree = this.getDataTree(params.account);
    if (dataTree) {
		params.dataTree = dataTree;
		params.searchTypes = {};
		params.omit = params.omit || {};
		params.omit[ZmFolder.ID_TRASH] = true;
		params.omitParents = true;
        var setting = ZmOrganizer.OPEN_SETTING[this.type];
        params.collapsed = !(!setting || (appCtxt.get(setting, null, params.account) !== false));
		this._setupNewOp(params);
		this._treeView[id].set(params);
		this._checkTreeView(id);
	}
	
	return this._treeView[id];
};

/**
 * Gets the tree style.
 * 
 * @return	{Object}	the tree style or <code>null</code> if not set
 */
ZmSearchTreeController.prototype.getTreeStyle =
function() {
	return null;
};

/**
* Resets and enables/disables operations based on context.
*
* @param {ZmControl}	parent		the widget that contains the operations
* @param {constant}	type		the type
* @param {String}	id			the currently selected/activated organizer
*/
ZmSearchTreeController.prototype.resetOperations =
function(parent, type, id) {
	parent.enableAll(true);
	var search = appCtxt.getById(id);
	parent.enable(ZmOperation.EXPAND_ALL, (search.size() > 0));
};

/**
 * @private
 */
ZmSearchTreeController.prototype._newListener =
function(ev){
	AjxDispatcher.require("Browse");
	appCtxt.getSearchController().showBrowseView();
};

/**
 * @private
 */
ZmSearchTreeController.prototype._browseListener =
function(ev){
    var search = this._getActionedOrganizer(ev);
    if (search) {
        AjxDispatcher.require("Browse");
        appCtxt.getSearchController().showBrowsePickers([ZmPicker.SEARCH]);
    }
};


// Private methods

/**
 * Returns ops available for "Searches" container.
 * 
 * @private
 */
ZmSearchTreeController.prototype._getHeaderActionMenuOps =
function() {
	return [ZmOperation.EXPAND_ALL,
            ZmOperation.BROWSE];
};

/**
 * Returns ops available for saved searches.
 * 
 * @private
 */
ZmSearchTreeController.prototype._getActionMenuOps =
function() {
	return [ZmOperation.DELETE,
			ZmOperation.RENAME_SEARCH,
			ZmOperation.MOVE,
			ZmOperation.EXPAND_ALL];
};

/**
 * override the ZmFolderTreeController override.
 * 
 * @private
 */
ZmSearchTreeController.prototype._getAllowedSubTypes =
function() {
	return ZmTreeController.prototype._getAllowedSubTypes.call(this);
};

/**
 * Returns a "New Saved Search" dialog.
 * 
 * @private
 */
ZmSearchTreeController.prototype._getNewDialog =
function() {
	return appCtxt.getNewSearchDialog();
};

/**
 * Called when a left click occurs (by the tree view listener). The saved
 * search will be run.
 *
 * @param {ZmSearchFolder}		searchFolder		the search that was clicked
 * 
 * @private
 */
ZmSearchTreeController.prototype._itemClicked =
function(searchFolder) {
	if (searchFolder._showFoldersCallback) {
		searchFolder._showFoldersCallback.run();
		return;
	}

	appCtxt.getSearchController().redoSearch(searchFolder.search, false, {getHtml: appCtxt.get(ZmSetting.VIEW_AS_HTML)});
};

/**
 * @private
 */
ZmSearchTreeController.prototype._getMoveParams =
function(dlg) {
	var params = ZmTreeController.prototype._getMoveParams.apply(this, arguments);
	params.overviewId = dlg.getOverviewId(this.type);
	params.treeIds = [ZmOrganizer.FOLDER, ZmOrganizer.SEARCH];
	return params;
};

// Miscellaneous

/**
 * Returns a title for moving a saved search.
 * 
 * @private
 */
ZmSearchTreeController.prototype._getMoveDialogTitle =
function() {
	return AjxMessageFormat.format(ZmMsg.moveSearch, this._pendingActionData.name);
};

/**
 * Shows or hides the tree view. It is hidden only if there are no saved
 * searches that belong to the owning app, and we have been told to hide empty
 * tree views of this type.
 * 
 * @param {constant}	overviewId		the overview ID
 * 
 * @private
 */
ZmSearchTreeController.prototype._checkTreeView =
function(overviewId) {
	var treeView = this._treeView[overviewId];
	if (!overviewId || !treeView) { return; }

	var account = this._opc.getOverview(overviewId).account;
	var rootId = (appCtxt.multiAccounts && !account.isMain)
		? (ZmOrganizer.getSystemId(ZmOrganizer.ID_ROOT, account))
		: ZmOrganizer.ID_ROOT;
	var hide = ZmOrganizer.HIDE_EMPTY[this.type] && !treeView.getTreeItemById(rootId).getItemCount();
	this._treeView[overviewId].setVisible(!hide);
};
