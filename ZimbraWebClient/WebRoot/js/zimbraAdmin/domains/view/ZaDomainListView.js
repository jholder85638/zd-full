/*
 * 
 */

/**
* @constructor
* @class ZaDomainListView
* @param parent
* @author Roland Schemers
* @author Greg Solovyev
**/

ZaDomainListView = function(parent) {

//	var className = "ZaDomainListView";
	var className = null;
	var posStyle = DwtControl.ABSOLUTE_STYLE;
	
	var headerList = this._getHeaderList();

	ZaListView.call(this, {
		parent:parent, 
		className:className, 
		posStyle:posStyle, 
		headerList:headerList,
		id: ZaId.TAB_DOMAIN_MANAGE
	});

	this._appCtxt = this.shell.getData(ZaAppCtxt.LABEL);
	
}

ZaDomainListView.prototype = new ZaListView;
ZaDomainListView.prototype.constructor = ZaDomainListView;

ZaDomainListView.prototype.toString = 
function() {
	return "ZaDomainListView";
}

ZaDomainListView.prototype.getTitle = 
function () {
	return ZaMsg.Domain_view_title;
}

ZaDomainListView.prototype.getTabIcon =
function () {
	return "Domain" ;
}

/**
* Renders a single item as a DIV element.
*/
ZaDomainListView.prototype._createItemHtml =
function(domain, now, isDragProxy) {
	var html = new Array(50);
	var	div = document.createElement("div");
	div[DwtListView._STYLE_CLASS] = "Row";
	div[DwtListView._SELECTED_STYLE_CLASS] = div[DwtListView._STYLE_CLASS] + "-" + DwtCssStyle.SELECTED;
	div.className = div[DwtListView._STYLE_CLASS];
	this.associateItemWithElement(domain, div, DwtListView.TYPE_LIST_ITEM);
	
	var idx = 0;
	html[idx++] = "<table width='100%' cellspacing='0' cellpadding='0'>";
	html[idx++] = "<tr>";
	var cnt = this._headerList.length;
	for(var i = 0; i < cnt; i++) {
		var field = this._headerList[i]._field;
		if(field == ZaDomain.A_domainName) {
			// name
			html[idx++] = "<td align='left' width=" + this._headerList[i]._width + "><nobr>";
			html[idx++] = AjxStringUtil.htmlEncode(domain.name);
			html[idx++] = "</nobr></td>";
		} else if(field == ZaDomain.A_description) {
			// description		
			html[idx++] = "<td align='left' width=" + this._headerList[i]._width + "><nobr>";
			html[idx++] = AjxStringUtil.htmlEncode(
                    ZaItem.getDescriptionValue(domain.attrs[ZaDomain.A_description]));
			html[idx++] = "</nobr></td>";
		} else if(field == ZaDomain.A_zimbraDomainStatus) {
			// description		
			html[idx++] = "<td align='left' width=" + this._headerList[i]._width + "><nobr>";
			html[idx++] = ZaDomain._domainStatus(domain.attrs[ZaDomain.A_zimbraDomainStatus]);
			html[idx++] = "</nobr></td>";
		} else if (field == ZaDomain.A_domainType) {
            // domain type
			html[idx++] = "<td align='left' width=" + this._headerList[i]._width + "><nobr>";
			html[idx++] = AjxStringUtil.htmlEncode(domain.attrs[ZaDomain.A_domainType]);
			html[idx++] = "</nobr></td>";
        }
	}
	html[idx++] = "</tr></table>";
	div.innerHTML = html.join("");
	return div;
}

ZaDomainListView.prototype._getHeaderList =
function() {

	var headerList = new Array();
	var sortable = 1;
    var i = 0 ;
	//idPrefix, label, iconInfo, width, sortable, sortField, resizeable, visible
	headerList[i++] = new ZaListHeaderItem(ZaDomain.A_domainName , ZaMsg.DLV_Name_col, null, "250px", sortable++, ZaDomain.A_domainName, true, true);
	//headerList[0].initialize(ZaMsg.CLV_Name_col, null, "245", true, ZaDomain.A_domainName);

    headerList[i++] = new ZaListHeaderItem(ZaDomain.A_domainType , ZaMsg.DLV_Type_col, null, "100px", sortable++, ZaDomain.A_domainType, true, true);
	headerList[i++] = new ZaListHeaderItem(ZaDomain.A_zimbraDomainStatus , ZaMsg.DLV_Status_col, null, "100px", sortable++, ZaDomain.A_zimbraDomainStatus, true, true);
	headerList[i++] = new ZaListHeaderItem(ZaDomain.A_description, ZaMsg.DLV_Description_col, null, "auto", null, null, true, true);
	//headerList[1].initialize(ZaMsg.CLV_Description_col, null, "245", false, ZaDomain.A_description);
	
	return headerList;
}


