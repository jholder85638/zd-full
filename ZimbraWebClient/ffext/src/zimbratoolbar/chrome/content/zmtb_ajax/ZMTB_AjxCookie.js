/*
 * 
 */



function ZMTB_AjxCookie() {
}

ZMTB_AjxCookie.prototype.toString = 
function() {
	return "ZMTB_AjxCookie";
}

ZMTB_AjxCookie.getCookie = 
function(doc, name) {
	var arg = name + "=";
	var alen = arg.length;
	var clen = doc.cookie.length;
	var cookie = doc.cookie;
	var i = 0;
	while (i < clen) {
		var j = i + alen;
		if (cookie.substring(i, j) == arg) {
			var endstr = cookie.indexOf (";", j);
			if (endstr == -1)
				endstr = cookie.length;
			return unescape(cookie.substring(j, endstr));
		}
		i = cookie.indexOf(" ", i) + 1;
		if (i == 0) 
			break; 
	}
  return null;
}

ZMTB_AjxCookie.setCookie = 
function(doc, name, value, expires, path, domain, secure) {
	doc.cookie = name + "=" + escape (value) +
		((expires) ? "; expires=" + expires.toGMTString() : "") +
		((path) ? "; path=" + path : "") +
		((domain) ? "; domain=" + domain : "") +
		((secure) ? "; secure" : "");
}

ZMTB_AjxCookie.deleteCookie = 
function (doc, name, path, domain) {
	doc.cookie = name + "=" +
	((path) ? "; path=" + path : "") +
	((domain) ? "; domain=" + domain : "") + "; expires=Fri, 31 Dec 1999 23:59:59 GMT";
}

ZMTB_AjxCookie.areCookiesEnabled = 
function (doc) {
	var name = "ZM_COOKIE_TEST";
	var value = "Zimbra";
	ZMTB_AjxCookie.setCookie(doc, name, value);
	var cookie = ZMTB_AjxCookie.getCookie(doc, name);
	ZMTB_AjxCookie.deleteCookie(doc, name);
	return cookie == value;
}

