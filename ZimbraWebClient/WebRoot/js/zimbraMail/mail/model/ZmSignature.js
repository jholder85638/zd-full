/*
 * 
 */

/**
 * Creates a signature.
 * @class
 * This class represents a signature.
 * 
 * 
 */
ZmSignature = function(id) {
	this.id = id;
};

ZmSignature.prototype.toString = function() {
	return "ZmSignature";
};

//
// Data
//
/**
 * The name property.
 * @type	String
 */
ZmSignature.prototype.name = "";
/**
 * The content type property.
 * @type	String
 * @see		ZmMimeTable
 */
ZmSignature.prototype.contentType = ZmMimeTable.TEXT_PLAIN;
/**
 * The value property.
 * @type	String
 */
ZmSignature.prototype.value = "";

//
// Static functions
//

ZmSignature.createFromJson =
function(object) {
	var signature = new ZmSignature(object.id);
	signature.setFromJson(object);
	return signature;
};

//
// Public methods
//
/**
 * Creates the signature.
 * 
 * @param	{AjxCallback}		callback		the callback
 * @param	{AjxCallback}		errorCallback		the error callback
 * @param	{ZmBatchCommand}		batchCmd		the batch command
 */
ZmSignature.prototype.create =
function(callback, errorCallback, batchCmd) {
	var respCallback = callback ? new AjxCallback(this, this._handleCreateResponse, [callback]) : null;
	var resp = this._sendRequest("CreateSignatureRequest", false, respCallback, errorCallback, batchCmd);
	if (!callback && !batchCmd) {
		this._handleCreateResponse(callback, resp);
	}
};

/**
 * Saves the signature.
 * 
 * @param	{AjxCallback}		callback		the callback
 * @param	{AjxCallback}		errorCallback		the error callback
 * @param	{ZmBatchCommand}		batchCmd		the batch command
 */
ZmSignature.prototype.save =
function(callback, errorCallback, batchCmd) {
	var respCallback = callback ? new AjxCallback(this, this._handleModifyResponse, [callback]) : null;
	var resp = this._sendRequest("ModifySignatureRequest", false, respCallback, errorCallback, batchCmd);
	if (!callback && !batchCmd) {
		this._handleModifyResponse(callback, resp);
	}
};

/**
 * Deletes the signature.
 * 
 * @param	{AjxCallback}		callback		the callback
 * @param	{AjxCallback}		errorCallback		the error callback
 * @param	{ZmBatchCommand}		batchCmd		the batch command
 */
ZmSignature.prototype.doDelete =
function(callback, errorCallback, batchCmd) {
	var respCallback = callback ? new AjxCallback(this, this._handleDeleteResponse, [callback]) : null;
	var resp = this._sendRequest("DeleteSignatureRequest", true, respCallback, errorCallback, batchCmd);
	if (!callback && !batchCmd) {
		this._handleDeleteResponse(callback, resp);
	}
};

/**
 * Sets the signature from JSON object.
 * 
 * @param	{Object}	object		the object
 */
ZmSignature.prototype.setFromJson =
function(object) {

	this.name = object.name || this.name;
	var c = object.content;
    if (c) {
		var sig = c[0]._content ? c[0] : c[1];
		this.contentType = sig.type || this.contentType;
		this.value = sig._content || this.value;
    }
	if (object.cid) {
		this.contactId = object.cid[0]._content;
	}
};

/**
 * Gets the content type.
 * 
 * @return	{String}	the content type
 */
ZmSignature.prototype.getContentType =
function() {
    return this.contentType;
};

/**
 * Sets the content type.
 * 
 * @param	{String}	ct		the content type
 * @see		ZmMimeTable
 */
ZmSignature.prototype.setContentType =
function(ct){
    this.contentType = ct || ZmMimeTable.TEXT_PLAIN;  
};

/**
 * @param outputType	[string]	(Optional) Formats the resulting
 *									signature text to the specified
 *									content-type. If not specified,
 *									the signature text is returned in
 *									the original format.
 *
 * @private
 */
ZmSignature.prototype.getValue =
function(outputType) {
	
    var isHtml = this.contentType == ZmMimeTable.TEXT_HTML;
	var value = this.value;

	var type = outputType || this.contentType;
	if (type != this.contentType) {
        value = isHtml ? AjxStringUtil.convertHtml2Text(value) : AjxStringUtil.convertToHtml(value);
	}

    return value;
};


//
// Protected methods
//

ZmSignature.prototype._sendRequest =
function(method, idOnly, respCallback, errorCallback, batchCmd) {

/*
	var jsonObj = {};
	var request = jsonObj[method] = {_jsns:"urn:zimbraAccount"};
	var sig = request.signature = {};
	if (this.id) {
		sig.id = this.id;
	}
	if (!idOnly) {
		sig.name = this.name;
		sig.cid = this.contactId || null;
		sig.content = [];
		sig.content.push({_content:this.value, type:this.contentType});

        // Empty the other content type
        var emptyType = (this.contentType == ZmMimeTable.TEXT_HTML) ? ZmMimeTable.TEXT_PLAIN : ZmMimeTable.TEXT_HTML;
		sig.content.push({_content:"", type:emptyType});
	}
*/

	var soapDoc = AjxSoapDoc.create(method, "urn:zimbraAccount");
	var signatureEl = soapDoc.set("signature");
	if (this.id) {
		signatureEl.setAttribute("id", this.id);
	}
	if (!idOnly) {
		signatureEl.setAttribute("name", this.name);
		if (this.contactId || (method == "ModifySignatureRequest")) {
			soapDoc.set("cid", this.contactId || null, signatureEl);
		}
		var contentEl = soapDoc.set("content", this.value, signatureEl);
		contentEl.setAttribute("type", this.contentType);

        //Empty the other content type
        var emptyType = (this.contentType == ZmMimeTable.TEXT_HTML) ? ZmMimeTable.TEXT_PLAIN : ZmMimeTable.TEXT_HTML;
        contentEl = soapDoc.set("content", "", signatureEl);
		contentEl.setAttribute("type", emptyType);

	}

	if (batchCmd) {
		batchCmd.addNewRequestParams(soapDoc, respCallback, errorCallback);
		return;
	}

	var appController = appCtxt.getAppController();
	var params = {
		soapDoc:		soapDoc,
		asyncMode:		Boolean(respCallback),
		callback:		respCallback,
		errorCallback:	errorCallback
	}
	return appController.sendRequest(params);
};

ZmSignature.prototype._handleCreateResponse =
function(callback, resp) {
	// save id
	this.id = resp._data.CreateSignatureResponse.signature[0].id;

	// add to global hash
	var signatures = appCtxt.getSignatureCollection();
	signatures.add(this);

	if (callback) {
		callback.run();
	}
};

ZmSignature.prototype._handleModifyResponse = function(callback, resp) {
	// promote settings to global signature
	var signatures = appCtxt.getSignatureCollection();
	var signature = signatures.getById(this.id);
	signature.name = this.name;
	signature.value = this.value;
    signature.contentType = this.contentType;
	signatures._notify(ZmEvent.E_MODIFY, { item: signature });

	if (callback) {
		callback.run();
	}
};

ZmSignature.prototype._handleDeleteResponse = function(callback, resp) {
	// remove from global hash
	var signatures = appCtxt.getSignatureCollection();
	signatures.remove(this);

	if (callback) {
		callback.run();
	}
};
