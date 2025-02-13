/*
 * 
 */

/**
 * @class
 * This is a utility class.
 * 
 */
function com_zimbra_example_dynamictab_Util() {
};

/**
 * Gets a random number.
 * 
 * @param	{int}	range		a range
 * @return	{int}	a random number
 */
com_zimbra_example_dynamictab_Util._getRandomNumber =
function(range) {
	return Math.floor(Math.random() * range);
};

/**
 * Gets a random char.
 * 
 * @return	{String}	a random char
 */
com_zimbra_example_dynamictab_Util._getRandomChar =
function() {
	var chars = "0123456789abcdefghijklmnopqurstuvwxyzABCDEFGHIJKLMNOPQURSTUVWXYZ";
	return chars.substr( this._getRandomNumber(62), 1 );
};

/**
 * Generates a unique id.
 * 
 * @param	{int}	size		the size of the unique id
 * @return	{String}	the unique id
 */
com_zimbra_example_dynamictab_Util.generateUniqueID =
function(size) {
	var str = "";
	for(var i = 0; i < size; i++)
	{
		str += this._getRandomChar();
	}
	return str;
};

/**
 * Cleans the given url.
 * 
 * @param	{String}	url		the url to clean
 * @return	{String}	the resulting url
 */
com_zimbra_example_dynamictab_Util.cleanUrl =
function(url) {

	var newUrl = url;

	if (url) {
		url = url.trim();
		url = url.toLowerCase();
		
		if (url.indexOf("http://") == -1)
			url = "http://"+url;
		
		newUrl = url;
	}

	return newUrl;
};

/**
 * Checks if the item is in the array.
 * 
 * @param	{Array}		array		the array
 * @param	{String}	item		the item
 * @return	{Boolean}	<code>true</code> if the item is in the array
 */
com_zimbra_example_dynamictab_Util.arrayContains =
function(array,item) {
	for (i=0;array && i<array.length; i++) {
		if (array[i] == item)
			return true;
	}
	
	return false;
};

/**
 * 
 */
com_zimbra_example_dynamictab_Util.escapeHTML =
function (str) {                                       
    return(                                                               
        str.replace(/&/g,'&amp;').                                         
            replace(/>/g,'&gt;').                                           
            replace(/</g,'&lt;').                                           
            replace(/"/g,'&quot;')                                         
    );
    
};