/*
 * 
 */

YBabelfishPrefsDialog = function(shell, className, parent) {
	className = className || "YBabelfishPrefsDialog";
	this._zimlet = parent;
	var title = "Default Language";
	DwtDialog.call(this, {parent:shell, className:className, title:title});
	this.getHtmlElement().style.display= "auto";
	this.getHtmlElement().style.width="500px";
	this.setButtonListener(DwtDialog.OK_BUTTON, new AjxListener(this, this._prefSelected));
	this._createSearchHtml();
};

YBabelfishPrefsDialog.prototype = new DwtDialog;
YBabelfishPrefsDialog.prototype.constructor = YBabelfishPrefsDialog;

YBabelfishPrefsDialog.prototype._createSearchHtml = function() {

	//var selectId = Dwt.getNextId();

	this._langSelect = new DwtSelect({parent:this});
	//this._langSelect.reparentHtmlElement(selectId);

	for (i = 0; i < this._zimlet._languages.length; i++) {
		var option = this._zimlet._languages[i];
		this._langSelect.addOption(option.label, option.value == this._lang, option.value);
	}

	this._zimlet._resetDefaultLang();

	var table = document.createElement("TABLE");
	table.border = 0;
	table.cellPadding = 0;
	table.cellSpacing = 4;

	row = table.insertRow(-1);
	cell = row.insertCell(-1);
	cell.innerHTML = "Set Default Translation Language:";
	cell.appendChild(this._langSelect.getHtmlElement());

	var element = this._getContentDiv();
	element.appendChild(table);
};

YBabelfishPrefsDialog.prototype.popup = function(name, callback) {
	
	this.setTitle("Yahoo! Translator: Babel Fish Preferences");

	if (this._zimlet._defaultLang) {
		this._langSelect.setSelected(this._zimlet._defaultLang);
	} else {
		this._langSelect.setSelected(15);
	}
	
	// enable buttons
	this.setButtonEnabled(DwtDialog.OK_BUTTON, true);
	this.setButtonEnabled(DwtDialog.CANCEL_BUTTON, true);
	
	// show
	DwtDialog.prototype.popup.call(this);
};

YBabelfishPrefsDialog.prototype.popdown = 
function() {
	ZmDialog.prototype.popdown.call(this);
};

YBabelfishPrefsDialog.prototype._prefSelected =
function(){
	this._zimlet.setUserProperty("trans_language", this._langSelect.getValue(), true);
	this.popdown();
};

