/*
 * 
 */

/**
 * @overview
 * This file contains the result class.
 */

/**
 * Creates a CSFE result object.
 * @class
 * This class represents the result of a CSFE request. The data is either the 
 * response that was received, or an exception. If the request resulted in a 
 * SOAP fault from the server, there will also be a SOAP header present.
 *
 * @author Conrad Damon
 * 
 * @param {Object}	data			the response data
 * @param {Boolean}	isException	if <code>true</code>, the data is an exception object
 * @param {Object}	header			the SOAP header
 * 
 */
ZmCsfeResult = function(data, isException, header) {
	this.set(data, isException, header);
};

/**
 * Returns a string representation of the object.
 * 
 * @return		{String}		a string representation of the object
 */
ZmCsfeResult.prototype.toString =
function() {
	return "ZmCsfeResult";
};

/**
 * Sets the content of the result.
 *
 * @param {Object}	data			the response data
 * @param {Boolean}	isException	if <code>true</code>, the data is an exception object
 * @param {Object}	header			the SOAP header
 */
ZmCsfeResult.prototype.set =
function(data, isException, header) {
	this._data = data;
	this._isException = (isException === true);
	this._header = header;
};

/**
 * Gets the response data. If there was an exception, throws the exception.
 * 
 * @return	{Object}	the data
 */
ZmCsfeResult.prototype.getResponse =
function() {
	if (this._isException) {
		throw this._data;
	} else {
		return this._data;
	}
};

/**
 * Gets the exception object, if any.
 * 
 * @return	{ZmCsfeException}	the exception or <code>null</code> for none
 */
ZmCsfeResult.prototype.getException =
function() {
	return this._isException ? this._data : null;
};

/**
 * Checks if this result is an exception.
 * 
 * @return	{Boolean}	<code>true</code> if an exception
 */
ZmCsfeResult.prototype.isException = 
function() {
	return this._isException;
};

/**
 * Gets the SOAP header that came with a SOAP fault.
 * 
 * @return	{String}	the header
 */
ZmCsfeResult.prototype.getHeader =
function() {
	return this._header;
};
