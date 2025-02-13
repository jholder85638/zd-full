/*
 * 
 */

ZmAccountTestDialog = function(parent) {
	DwtDialog.call(this, {parent:parent, title:ZmMsg.accountTest, className:"DwtBaseDialog ZmDataSourceTestDialog" });
	this.registerCallback(DwtDialog.OK_BUTTON, this._handleOkButton, this);
	this.registerCallback(DwtDialog.CANCEL_BUTTON, this._handleCancelButton, this);
};
ZmAccountTestDialog.prototype = new DwtDialog;
ZmAccountTestDialog.prototype.constructor = ZmAccountTestDialog;

ZmAccountTestDialog.prototype.toString = function() {
	return "ZmAccountTestDialog";
};

//
// DwtDialog methods
//

ZmAccountTestDialog.prototype.popup = function(accounts, okCallback, cancelCallback) {
	delete this._reqId;
	// perform tests
	if (accounts && accounts.length > 0) {
		this._okCallback = okCallback;
		this._cancelCallback = cancelCallback;

		// setup display
		this._initializeAccounts(accounts);
		this.setButtonEnabled(DwtDialog.OK_BUTTON, false);

		// show dialog
		DwtDialog.prototype.popup.call(this);

		// begin test
		var testCallback = new AjxCallback(this, this._handleTestResult);
		testCallback.args = [testCallback, 0];
		this._handleTestResult.apply(this, testCallback.args);
	}

	// nothing to do; report success
	else if (okCallback) {
		var successes = [];
		okCallback.run(successes);
	}
};

//
// Protected methods
//

ZmAccountTestDialog.prototype._initializeAccounts = function(accounts) {
	this._accounts = accounts;
	this._successes = new Array(accounts.length);

	var data = { id: this._htmlElId, accounts: accounts };
	var html = AjxTemplate.expand("prefs.Pages#AccountTestContent", data);
	this.setContent(html);
};

ZmAccountTestDialog.prototype._handleTestResult =
function(testCallback, index, result) {
	// show results
	if (result) {
		var account = this._accounts[index - 1];
		var statusEl = document.getElementById(account.id+"_test_status");

		var error = null;
		var resp = result._data && result._data.TestDataSourceResponse;
		if (resp) {
			this._successes[index - 1] = true;
			var dsrc = resp[ZmDataSource.prototype.ELEMENT_NAME] ||
					   resp[ZmPopAccount.prototype.ELEMENT_NAME] ||
					   resp[ZmImapAccount.prototype.ELEMENT_NAME];
			dsrc = dsrc && dsrc[0];
			if (dsrc.success) {
				statusEl.className = [statusEl.className,"ZmTestSucceeded"].join(" ");
				statusEl.innerHTML = ZmMsg.popAccountTestSuccess;
			}
			else {
				error = dsrc.error;
			}
		}
		else {
			error = "Generic Test Failure"; // TODO: i18n
		}

		if (error) {
			this._successes[index - 1] = false;

			statusEl.className = [statusEl.className,"ZmTestFailed"].join(" ");
			statusEl.innerHTML = ZmMsg.popAccountTestFailure;

			var detailsEl = document.getElementById(account.id+"_test_details");
			var errorEl = document.getElementById(account.id+"_test_error");
			errorEl.innerHTML = AjxStringUtil.htmlEncode(error);
			Dwt.setVisible(detailsEl, true);
		}

		this._position();
	}

	// finish
	if (this._accounts.length == index) {
		this.setButtonEnabled(DwtDialog.OK_BUTTON, true);
		return;
	}

	// continue testing
	var account = this._accounts[ testCallback.args[1]++ ];
	var statusEl = document.getElementById(account.id+"_test_status");
	statusEl.innerHTML = ZmMsg.popAccountTestInProgress;
	this._reqId = account.testConnection(testCallback, testCallback, null, true);
};

ZmAccountTestDialog.prototype._handleOkButton = function(evt) {
	this.popdown();
	if (this._reqId) {
		appCtxt.getAppController().cancelRequest(this._reqId);
	}
	if (this._okCallback) {
		this._okCallback.run(this._successes);
	}
};
ZmAccountTestDialog.prototype._handleCancelButton = function(evt) {
	this.popdown();
	if (this._cancelCallback) {
		this._cancelCallback.run();
	}
};