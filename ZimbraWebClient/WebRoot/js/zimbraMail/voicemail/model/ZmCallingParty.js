/*
 * 
 */

/**
* Creates a calling party.
* @constructor
* @class  ZmCallingParty
* This class represents a calling party. Should be treated as immutable.
*
*/
ZmCallingParty = function() {
	ZmPhone.call(this);

	this.type = null;
	this.city = null;
	this.state = null;
	this.country = null;
	this.callerId = null;
};

ZmCallingParty.prototype = new ZmPhone;
ZmCallingParty.prototype.constructor = ZmCallingParty;

ZmCallingParty.prototype.toString = 
function() {
	return "ZmCallingParty";
};

ZmCallingParty.prototype.getPhoneNumber = 
function() {
	return this.name;
};

ZmCallingParty.prototype._loadFromDom =
function(node) {
	if (node.n) this.name = node.n;
	if (node.p) this.callerId = node.p == node.n ? null : this._getDisplayString(node.p);
	if (node.t) this.type = node.t == "f" ? ZmVoiceItem.FROM : ZmVoiceItem.TO;
	if (node.ci) this.city = this._getDisplayString(node.ci);
	if (node.st) this.state = this._getDisplayString(node.st);
	if (node.co) this.country = node.co == "null" ? null : node.co;
};

ZmCallingParty.prototype._getDisplayString =
function(str) {
	return (str.toUpperCase() != "UNAVAILABLE") ? str : null;
};
