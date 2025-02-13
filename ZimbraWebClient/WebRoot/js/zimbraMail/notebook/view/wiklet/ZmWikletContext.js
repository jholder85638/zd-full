/*
 * 
 */

ZmWikletContext = function(notebookCache) {
	this._notebookCache = notebookCache;
	this._items = [];
}

// Data

ZmWikletContext.prototype._notebookCache;
ZmWikletContext.prototype._processor;

ZmWikletContext.prototype._items;

// Public methods

ZmWikletContext.prototype.pushItem = function(item) {
	this._items.push(item);
	return this._items.length;
};
ZmWikletContext.prototype.getItem = function() {
	var length = this._items.length;
	return length ? this._items[length - 1] : null;
};
ZmWikletContext.prototype.getItemAt = function(index) {
	return this._items[index];
};
ZmWikletContext.prototype.getItemCount = function() {
	return this._items.length;
};
ZmWikletContext.prototype.setItemCount = function(count) { 
	this._items.length = count;
};

ZmWikletContext.prototype.getPages = function(notebookId) {
	return this._notebookCache.getPagesInFolder(notebookId);
};

ZmWikletContext.prototype.getPageByName = function(notebookId, name, recurseUp) {
	return this._notebookCache.getPageByName(notebookId, name, recurseUp);
};

ZmWikletContext.prototype.getNotebookById = function(id) {
	return appCtxt.getById(id);
};
ZmWikletContext.prototype.getSections = function(notebookId) {
	var notebook = this.getNotebookById(notebookId);
	return notebook ? notebook.children.getArray() : [];
};
ZmWikletContext.prototype.getFiles = function(notebookId) {
	return []; // TODO
};
ZmWikletContext.prototype.getCache = function() {
	return this._notebookCache;
};

ZmWikletContext.prototype.getTagById = function(tagId) {
	return appCtxt.getById(tagId);
};

ZmWikletContext.prototype.process = function(content) {
	return ZmWikletProcessor._process(content);
};