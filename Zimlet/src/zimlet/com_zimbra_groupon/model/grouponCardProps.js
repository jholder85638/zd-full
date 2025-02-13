/*
 * 
 * @Author Raja Rao DV (rrao@zimbra.com)
 */

function Com_Zimbra_GrouponCardProps() {
	this.type = "";
	this.tableId = "";
	this.timer = "";
	this.accountId = "";
	this.isClosed = "";
	this.refreshBtnId = "";
	this.closeBtnd = "";
	this.headerName = "";
	this.headerIcon = "";
	this.autoScroll = "";
	this.updateBtnId = "";//id of the updateButton
	this.updateFieldId = "";//id of the textArea that contains the status update
	this.updateToOtherFieldId = "";//html div that shows To:<otheruser> about the updateField
	this.feedPostParentId = "";//parentId of the current card
	this.feedPostOtherParentId = "";//parentId of the current feed-item within the card(used to write on other's wall)
	this.filterFeedByType = ""; //used to filter newsfeeds(used in default whereClause)
	this.whereClause = ""; //used to filter newsfeeds but allows us to set complete whereClause(if set, this will be used instead of this.filterFeedByType)
	this.latestTimeStamp = "";//latest timestamp of the feed/comment
	this.isMarkAsReadSet = false;//boolean that indicates if we need to use latestTimeStamp to filter the feeds
	this.unreadCountCellId = ""; //div id that shows the unreadcount
	this.markAsReadBtnId = "";//div id that stores markAsRead button.
	this.unReadCount = 0; //keeps track of # of unread feed items
	this.attachfileLinkId = ""; //Anchor for Attach files
}