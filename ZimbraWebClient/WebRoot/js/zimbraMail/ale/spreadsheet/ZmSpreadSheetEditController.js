/*
 * 
 */

ZmSpreadSheetEditController = 	function() {

};

ZmSpreadSheetEditController.prototype.toString = function() {
    return "ZmSlideController";
};


ZmSpreadSheetEditController.prototype.setToolBar = function(toolbar) {
    this._toolbar = toolbar;
    this._initToolBar();
}

ZmSpreadSheetEditController.prototype.setCurrentView = function(view) {
    this._currentView = view;
}

ZmSpreadSheetEditController._VALUE = "value";

ZmSpreadSheetEditController.ACTION_SAVE = "save";


ZmSpreadSheetEditController.prototype._initToolBar = function () {

    var tb = this._toolbar;

    var listener = new AjxListener(this, this._actionListener);

    this._saveSlide = new DwtToolBarButton({parent:tb});
    this._saveSlide.setToolTipContent(ZmMsg.save);
    this._saveSlide.setImage("Save");
    this._saveSlide.setText(ZmMsg.save);
    this._saveSlide.setData(ZmSpreadSheetEditController._VALUE, ZmSpreadSheetEditController.ACTION_SAVE);
    this._saveSlide.addSelectionListener(listener);

    tb.setVisible(true);
}

ZmSpreadSheetEditController.prototype._actionListener =
function(ev) {
    var action = ev.item.getData(ZmSpreadSheetEditController._VALUE);
    if(action == ZmSpreadSheetEditController.ACTION_SAVE) {
        this._currentView.saveFile();
    }
}


