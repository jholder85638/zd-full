/*
 * 
 */

/**
 * Simple dialog allowing user to choose between an Instance or Series for an appointment
 * @constructor
 * @class
 *
 * @author Parag Shah
 * @param parent			the element that created this view
 * 
 * @extends		DwtDialog
 * 
 * @private
 */
ZmCalItemTypeDialog = function(parent) {

	DwtDialog.call(this, {parent:parent});

	var content = AjxTemplate.expand("calendar.Calendar#TypeDialog", {id:this._htmlElId});
	this.setContent(content);

	// cache fields
	this._defaultRadio = document.getElementById(this._htmlElId + "_defaultRadio");
	this._questionCell = document.getElementById(this._htmlElId + "_question");
	this._instanceMsg = document.getElementById(this._htmlElId + "_instanceMsg");
	this._seriesMsg = document.getElementById(this._htmlElId + "_seriesMsg");
};

ZmCalItemTypeDialog.prototype = new DwtDialog;
ZmCalItemTypeDialog.prototype.constructor = ZmCalItemTypeDialog;

// Public methods

ZmCalItemTypeDialog.prototype.toString =
function() {
	return "ZmCalItemTypeDialog";
};

ZmCalItemTypeDialog.prototype.initialize =
function(calItem, mode, type) {
	this.calItem = calItem;
	this.mode = mode;
	this._defaultRadio.checked = true;

	var m;
	if (type == ZmItem.APPT) {
		m = (calItem instanceof Array)
			? ZmMsg.isRecurringApptList
			: AjxMessageFormat.format(ZmMsg.isRecurringAppt, [AjxStringUtil.htmlEncode(calItem.getName())]);
	} else {
		m = AjxMessageFormat.format(ZmMsg.isRecurringTask, [AjxStringUtil.htmlEncode(calItem.getName())]);
	}
	if (mode == ZmCalItem.MODE_EDIT) {
		this.setTitle(ZmMsg.openRecurringItem);
		this._questionCell.innerHTML = m + " " + ZmMsg.editApptQuestion;
		this._instanceMsg.innerHTML = ZmMsg.openInstance;
		this._seriesMsg.innerHTML = ZmMsg.openSeries;
	} else if (mode == ZmAppt.MODE_DRAG_OR_SASH) {
		this.setTitle(ZmMsg.modifyRecurringItem);
		this._questionCell.innerHTML = m + " " + ZmMsg.modifyApptQuestion;
		this._instanceMsg.innerHTML = ZmMsg.modifyInstance;
		this._seriesMsg.innerHTML = ZmMsg.modifySeries;
	} else {
		this.setTitle(ZmMsg.deleteRecurringItem);
		if (calItem instanceof Array) {
			this._questionCell.innerHTML = m + " " + ZmMsg.deleteApptListQuestion;
			this._instanceMsg.innerHTML = ZmMsg.deleteInstances;
		} else {
			this._questionCell.innerHTML = m + " " + ZmMsg.deleteApptQuestion;
			this._instanceMsg.innerHTML = ZmMsg.deleteInstance;
		}
		this._seriesMsg.innerHTML = ZmMsg.deleteSeries;
	}
};

ZmCalItemTypeDialog.prototype.addSelectionListener =
function(buttonId, listener) {
	this._button[buttonId].addSelectionListener(listener);
};

ZmCalItemTypeDialog.prototype.isInstance =
function() {
	return this._defaultRadio.checked;
};
