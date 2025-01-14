/*
 * 
 */

/**
 * @overview
 */

/**
 * Creates a revoke share dialog.
 * @class
 * This class represents a revoke share dialog.
 * 
 * @param	{DwtComposite}	parent		the parent
 * @param	{String}	className		the class name
 *  
 * @extends		DwtDialog
 */
ZmRevokeShareDialog = function(parent, className) {
	className = className || "ZmRevokeShareDialog";
	var title = ZmMsg.revokeShare;
	var buttons = [ DwtDialog.YES_BUTTON, DwtDialog.NO_BUTTON ];
	DwtDialog.call(this, {parent:parent, className:className, title:title, standardButtons:buttons});
	this.setButtonListener(DwtDialog.YES_BUTTON, new AjxListener(this, this._handleYesButton));
	
	var view = this._createView();
	this.setView(view);

	// create formatters
	this._formatter = new AjxMessageFormat(ZmMsg.revokeShareConfirm);
};

ZmRevokeShareDialog.prototype = new DwtDialog;
ZmRevokeShareDialog.prototype.constructor = ZmRevokeShareDialog;

// Public methods

ZmRevokeShareDialog.prototype.toString =
function() {
	return "ZmRevokeShareDialog";
};

/**
 * Pops-up the dialog.
 * 
 * @param	{ZmShare}	share		the share
 */
ZmRevokeShareDialog.prototype.popup =
function(share) {
	this._share = share;

	var isPubShare = share.isPublic();
	var isAllShare = share.grantee && (share.grantee.type == ZmShare.TYPE_ALL);

	var params = isPubShare ? ZmMsg.shareWithPublic : isAllShare ? ZmMsg.shareWithAll :
					(share.grantee.name || ZmMsg.userUnknown);
	this._confirmMsgEl.innerHTML = this._formatter.format(params);

	this._reply.setReplyType(ZmShareReply.STANDARD);
	this._reply.setReplyNote("");
	this._reply.setVisible(!isPubShare && !isAllShare);

	DwtDialog.prototype.popup.call(this);
	this.setButtonEnabled(DwtDialog.YES_BUTTON, true);
};

// Protected methods

ZmRevokeShareDialog.prototype._handleYesButton =
function() {
	var callback = new AjxCallback(this, this._yesButtonCallback);
	this._share.revoke(callback);
};

ZmRevokeShareDialog.prototype._yesButtonCallback =
function() {
	var share = this._share;
	var replyType = this._reply.getReplyType();
	var sendMail = !(share.isAll() || share.isPublic() || share.invalid); 
	if (replyType != ZmShareReply.NONE && sendMail) {
		// initialize rest of share information
		share.grantee.email = share.grantee.name || share.grantee.id;
		share.grantor.id = appCtxt.get(ZmSetting.USERID);
		share.grantor.email = appCtxt.get(ZmSetting.USERNAME);
		share.grantor.name = appCtxt.get(ZmSetting.DISPLAY_NAME) || share.grantor.email;
		share.link.id = share.object.id;
		share.link.name = share.object.name;
		share.link.view = ZmOrganizer.getViewName(share.object.type);

		share.notes = (replyType == ZmShareReply.QUICK) ? this._reply.getReplyNote() : "";
	
		if (replyType == ZmShareReply.COMPOSE) {
			share.composeMessage(ZmShare.DELETE);
		} else {
			share.sendMessage(ZmShare.DELETE);
		}
	}

	this.popdown();
};

ZmRevokeShareDialog.prototype._createView =
function() {
	this._confirmMsgEl = document.createElement("DIV");
	this._confirmMsgEl.style.fontWeight = "bold";
	this._confirmMsgEl.style.marginBottom = "0.25em";
	
	var view = new DwtComposite(this);
	this._reply = new ZmShareReply(view);
	
	var element = view.getHtmlElement();
	element.appendChild(this._confirmMsgEl);
	element.appendChild(this._reply.getHtmlElement());

	return view;
};

ZmRevokeShareDialog.prototype._getSeparatorTemplate =
function() {
	return "";
};
