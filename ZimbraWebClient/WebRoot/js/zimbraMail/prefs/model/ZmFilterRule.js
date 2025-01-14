/*
 * 
 */

/**
 * Creates an empty filter rule. Conditions and actions will need to be added.
 * @constructor
 * @class
 * ZmFilterRule represents a filter rule. A rule includes one or more conditions
 * and one or more actions.
 *
 * @author Conrad Damon
 *
 * @param {String}	name			the rule name
 * @param {Boolean}	active		if <code>true</code>, if the rule is enabled
 * @param {Object}	filterActions	the filter action data as raw json object
 * @param {Object}	filterTests	the filter conditions data as raw json object
 * 
 */
ZmFilterRule = function(name, active, filterActions, filterTests) {
	/**
     * The name of the filter rule.
     * @type String
     */
	this.name = name;
	/**
	 * The filter rule actions.
	 * @type	Object
	 */
	this.actions = filterActions || {};
	/**
	 * The filter rules conditions.
	 * @type	Object
	 */
	this.conditions = filterTests || {};
	this.active = (active !== false);
	if (!filterTests) {
		this.setGroupOp();
	}

	this.id = ZmFilterRule._nextId++;
};

ZmFilterRule._nextId = 1;

/**
 * Defines the "group any" operator.
 */
ZmFilterRule.GROUP_ANY = "anyof";
/**
 * Defines the "group all" operator.
 */
ZmFilterRule.GROUP_ALL = "allof";

// Display widgets for various rule properties

/**
 * Defines the "input" type.
 */
ZmFilterRule.TYPE_INPUT			= "INPUT";
/**
 * Defines the "select" type.
 */
ZmFilterRule.TYPE_SELECT		= "SELECT";
/**
 * Defines the "calendar" type.
 */
ZmFilterRule.TYPE_CALENDAR		= "CALENDAR";
/**
 * Defines the "folder picker" type.
 */
ZmFilterRule.TYPE_FOLDER_PICKER	= "FOLDER_PICKER";
/**
 * Defines the "tag picker" type.
 */
ZmFilterRule.TYPE_TAG_PICKER	= "TAG_PICKER";

// Conditions (subjects)
ZmFilterRule.C_FROM			= "FROM";
ZmFilterRule.C_TO			= "TO";
ZmFilterRule.C_CC			= "CC";
ZmFilterRule.C_TO_CC		= "TO_CC";
ZmFilterRule.C_SUBJECT		= "SUBJECT";
ZmFilterRule.C_HEADER		= "HEADER";
ZmFilterRule.C_SIZE			= "SIZE";
ZmFilterRule.C_DATE			= "DATE";
ZmFilterRule.C_BODY			= "BODY";
ZmFilterRule.C_ATT			= "ATT";
ZmFilterRule.C_MIME_HEADER	= "MIME_HEADER";
ZmFilterRule.C_ADDRBOOK		= "ADDRBOOK";
ZmFilterRule.C_INVITE		= "INVITE";

ZmFilterRule.C_HEADER_VALUE = {};
ZmFilterRule.C_HEADER_VALUE[ZmFilterRule.C_FROM]	= "from";
ZmFilterRule.C_HEADER_VALUE[ZmFilterRule.C_TO]		= "to";
ZmFilterRule.C_HEADER_VALUE[ZmFilterRule.C_CC]		= "cc";
ZmFilterRule.C_HEADER_VALUE[ZmFilterRule.C_TO_CC]	= "to,cc";
ZmFilterRule.C_HEADER_VALUE[ZmFilterRule.C_SUBJECT]	= "subject";
ZmFilterRule.C_HEADER_VALUE[ZmFilterRule.C_HEADER]	= "header";

ZmFilterRule.C_HEADER_MAP = {};
for (var i in ZmFilterRule.C_HEADER_VALUE) {
	ZmFilterRule.C_HEADER_MAP[ZmFilterRule.C_HEADER_VALUE[i]] = i;
};

ZmFilterRule.C_LABEL = {};
ZmFilterRule.C_LABEL[ZmFilterRule.C_FROM]		= ZmMsg.from;
ZmFilterRule.C_LABEL[ZmFilterRule.C_TO]			= ZmMsg.to;
ZmFilterRule.C_LABEL[ZmFilterRule.C_CC]			= ZmMsg.cc;
ZmFilterRule.C_LABEL[ZmFilterRule.C_TO_CC]		= ZmMsg.toCc;
ZmFilterRule.C_LABEL[ZmFilterRule.C_SUBJECT]	= ZmMsg.subject;
ZmFilterRule.C_LABEL[ZmFilterRule.C_HEADER]		= ZmMsg.headerNamed;
ZmFilterRule.C_LABEL[ZmFilterRule.C_SIZE]		= ZmMsg.size;
ZmFilterRule.C_LABEL[ZmFilterRule.C_DATE]		= ZmMsg.date;
ZmFilterRule.C_LABEL[ZmFilterRule.C_BODY]		= ZmMsg.body;
ZmFilterRule.C_LABEL[ZmFilterRule.C_ATT]		= ZmMsg.attachment;
// only read-receipt (i.e. "message/disposition-notification") content-type is currently supported
ZmFilterRule.C_LABEL[ZmFilterRule.C_MIME_HEADER]= ZmMsg.readReceiptFilter;
ZmFilterRule.C_LABEL[ZmFilterRule.C_ADDRBOOK]	= ZmMsg.addressIn;
ZmFilterRule.C_LABEL[ZmFilterRule.C_INVITE]		= ZmMsg.calendarInvite;

// Tests
ZmFilterRule.TEST_ADDRESS						= "addressTest"; // not currently support
ZmFilterRule.TEST_HEADER						= "headerTest";
ZmFilterRule.TEST_HEADER_EXISTS					= "headerExistsTest";
ZmFilterRule.TEST_SIZE							= "sizeTest";
ZmFilterRule.TEST_DATE							= "dateTest";
ZmFilterRule.TEST_BODY							= "bodyTest";
ZmFilterRule.TEST_ATTACHMENT					= "attachmentTest";
ZmFilterRule.TEST_MIME_HEADER					= "mimeHeaderTest";
ZmFilterRule.TEST_ADDRBOOK						= "addressBookTest";
ZmFilterRule.TEST_INVITE						= "inviteTest";

// Conditions map to Tests
ZmFilterRule.C_TEST_MAP = {};
ZmFilterRule.C_TEST_MAP[ZmFilterRule.C_FROM]		= ZmFilterRule.TEST_HEADER;
ZmFilterRule.C_TEST_MAP[ZmFilterRule.C_TO]			= ZmFilterRule.TEST_HEADER;
ZmFilterRule.C_TEST_MAP[ZmFilterRule.C_CC]			= ZmFilterRule.TEST_HEADER;
ZmFilterRule.C_TEST_MAP[ZmFilterRule.C_TO_CC]		= ZmFilterRule.TEST_HEADER;
ZmFilterRule.C_TEST_MAP[ZmFilterRule.C_SUBJECT]		= ZmFilterRule.TEST_HEADER;
ZmFilterRule.C_TEST_MAP[ZmFilterRule.C_HEADER]		= ZmFilterRule.TEST_HEADER;
ZmFilterRule.C_TEST_MAP[ZmFilterRule.C_SIZE]		= ZmFilterRule.TEST_SIZE;
ZmFilterRule.C_TEST_MAP[ZmFilterRule.C_DATE]		= ZmFilterRule.TEST_DATE;
ZmFilterRule.C_TEST_MAP[ZmFilterRule.C_BODY]		= ZmFilterRule.TEST_BODY;
ZmFilterRule.C_TEST_MAP[ZmFilterRule.C_ATT]			= ZmFilterRule.TEST_ATTACHMENT;
ZmFilterRule.C_TEST_MAP[ZmFilterRule.C_MIME_HEADER]	= ZmFilterRule.TEST_MIME_HEADER;
ZmFilterRule.C_TEST_MAP[ZmFilterRule.C_ADDRBOOK]	= ZmFilterRule.TEST_ADDRBOOK;
ZmFilterRule.C_TEST_MAP[ZmFilterRule.C_INVITE]		= ZmFilterRule.TEST_INVITE;

// Operations (verbs)
ZmFilterRule.OP_IS				= "IS";
ZmFilterRule.OP_NOT_IS			= "NOT_IS";
ZmFilterRule.OP_CONTAINS		= "CONTAINS";
ZmFilterRule.OP_NOT_CONTAINS	= "NOT_CONTAINS";
ZmFilterRule.OP_MATCHES			= "MATCHES";
ZmFilterRule.OP_NOT_MATCHES		= "NOT_MATCHES";
ZmFilterRule.OP_EXISTS			= "EXISTS";
ZmFilterRule.OP_NOT_EXISTS		= "NOT_EXISTS";
ZmFilterRule.OP_UNDER			= "UNDER";
ZmFilterRule.OP_NOT_UNDER		= "NOT_UNDER";
ZmFilterRule.OP_OVER			= "OVER";
ZmFilterRule.OP_NOT_OVER		= "NOT_OVER";
ZmFilterRule.OP_BEFORE			= "BEFORE";
ZmFilterRule.OP_NOT_BEFORE		= "NOT_BEFORE";
ZmFilterRule.OP_AFTER			= "AFTER";
ZmFilterRule.OP_NOT_AFTER		= "NOT_AFTER";
ZmFilterRule.OP_IN				= "IN";
ZmFilterRule.OP_NOT_IN			= "NOT_IN";
ZmFilterRule.OP_IS_REQUESTED	= "IS_REQUESTED"; // invites
ZmFilterRule.OP_NOT_REQUESTED   = "NOT_REQUESTED"; //invites
ZmFilterRule.OP_NOT_REPLIED     = "NOT_REPLIED"; //invites
ZmFilterRule.OP_IS_REPLIED		= "IS_REPLIED"; // invites
ZmFilterRule.OP_IS_READRECEIPT  = "IS_READRECEIPT";
ZmFilterRule.OP_NOT_READRECEIPT = "NOT_READRECEIPT";


// comparator types
ZmFilterRule.COMP_STRING							= "stringComparison";
ZmFilterRule.COMP_NUMBER							= "numberComparison";
ZmFilterRule.COMP_DATE								= "dateComparison";
// comparator map to test
ZmFilterRule.COMP_TEST_MAP = {};
ZmFilterRule.COMP_TEST_MAP[ZmFilterRule.TEST_ADDRESS]		= ZmFilterRule.COMP_STRING;
ZmFilterRule.COMP_TEST_MAP[ZmFilterRule.TEST_HEADER]		= ZmFilterRule.COMP_STRING;
ZmFilterRule.COMP_TEST_MAP[ZmFilterRule.TEST_MIME_HEADER]	= ZmFilterRule.COMP_STRING;
ZmFilterRule.COMP_TEST_MAP[ZmFilterRule.TEST_SIZE]			= ZmFilterRule.COMP_NUMBER;
ZmFilterRule.COMP_TEST_MAP[ZmFilterRule.TEST_DATE]			= ZmFilterRule.COMP_DATE;

// operation values
ZmFilterRule.OP_VALUE = {};
ZmFilterRule.OP_VALUE[ZmFilterRule.OP_IS]			= "is";
ZmFilterRule.OP_VALUE[ZmFilterRule.OP_CONTAINS]		= "contains";
ZmFilterRule.OP_VALUE[ZmFilterRule.OP_MATCHES]		= "matches";
ZmFilterRule.OP_VALUE[ZmFilterRule.OP_EXISTS]		= "exists";
ZmFilterRule.OP_VALUE[ZmFilterRule.OP_UNDER]		= "under";
ZmFilterRule.OP_VALUE[ZmFilterRule.OP_OVER]			= "over";
ZmFilterRule.OP_VALUE[ZmFilterRule.OP_BEFORE]		= "before";
ZmFilterRule.OP_VALUE[ZmFilterRule.OP_AFTER]		= "after";
ZmFilterRule.OP_VALUE[ZmFilterRule.OP_IN]			= "in";
ZmFilterRule.OP_VALUE[ZmFilterRule.OP_IS_REQUESTED]	= "anyrequest";
ZmFilterRule.OP_VALUE[ZmFilterRule.OP_IS_REPLIED]	= "anyreply";
ZmFilterRule.OP_VALUE[ZmFilterRule.OP_IS_READRECEIPT] = "contains";

ZmFilterRule.OP_VALUE_MAP = {};
for (var i in ZmFilterRule.OP_VALUE) {
	ZmFilterRule.OP_VALUE_MAP[ZmFilterRule.OP_VALUE[i]] = i;
};

// operation labels
ZmFilterRule.OP_LABEL = {};
ZmFilterRule.OP_LABEL[ZmFilterRule.OP_IS]			= ZmMsg.exactMatch;
ZmFilterRule.OP_LABEL[ZmFilterRule.OP_NOT_IS]		= ZmMsg.notExactMatch;
ZmFilterRule.OP_LABEL[ZmFilterRule.OP_CONTAINS]		= ZmMsg.contains;
ZmFilterRule.OP_LABEL[ZmFilterRule.OP_NOT_CONTAINS]	= ZmMsg.notContain;
ZmFilterRule.OP_LABEL[ZmFilterRule.OP_MATCHES]		= ZmMsg.matches;
ZmFilterRule.OP_LABEL[ZmFilterRule.OP_NOT_MATCHES]	= ZmMsg.notMatch;
ZmFilterRule.OP_LABEL[ZmFilterRule.OP_EXISTS]		= ZmMsg.exists;
ZmFilterRule.OP_LABEL[ZmFilterRule.OP_NOT_EXISTS]	= ZmMsg.notExist;
ZmFilterRule.OP_LABEL[ZmFilterRule.OP_UNDER]		= ZmMsg.under;
ZmFilterRule.OP_LABEL[ZmFilterRule.OP_NOT_UNDER]	= ZmMsg.notUnder;
ZmFilterRule.OP_LABEL[ZmFilterRule.OP_OVER]			= ZmMsg.over;
ZmFilterRule.OP_LABEL[ZmFilterRule.OP_NOT_OVER]		= ZmMsg.notOver;
ZmFilterRule.OP_LABEL[ZmFilterRule.OP_BEFORE]		= ZmMsg.beforeLc;
ZmFilterRule.OP_LABEL[ZmFilterRule.OP_NOT_BEFORE]	= ZmMsg.notBefore;
ZmFilterRule.OP_LABEL[ZmFilterRule.OP_AFTER]		= ZmMsg.afterLc;
ZmFilterRule.OP_LABEL[ZmFilterRule.OP_NOT_AFTER]	= ZmMsg.notAfter;
ZmFilterRule.OP_LABEL[ZmFilterRule.OP_IN]			= ZmMsg.isIn;
ZmFilterRule.OP_LABEL[ZmFilterRule.OP_NOT_IN]		= ZmMsg.notIn;
ZmFilterRule.OP_LABEL[ZmFilterRule.OP_IS_REQUESTED]	= ZmMsg.isRequested;
ZmFilterRule.OP_LABEL[ZmFilterRule.OP_NOT_REQUESTED] = ZmMsg.notRequested;
ZmFilterRule.OP_LABEL[ZmFilterRule.OP_IS_REPLIED]	= ZmMsg.isReplied;
ZmFilterRule.OP_LABEL[ZmFilterRule.OP_NOT_REPLIED]  = ZmMsg.notReplied;
ZmFilterRule.OP_LABEL[ZmFilterRule.OP_IS_READRECEIPT] = ZmMsg.exists;
ZmFilterRule.OP_LABEL[ZmFilterRule.OP_NOT_READRECEIPT] = ZmMsg.notExist;


// commonly used lists
ZmFilterRule.MATCHING_OPS = [
	ZmFilterRule.OP_IS, ZmFilterRule.OP_NOT_IS,
	ZmFilterRule.OP_CONTAINS, ZmFilterRule.OP_NOT_CONTAINS,
	ZmFilterRule.OP_MATCHES, ZmFilterRule.OP_NOT_MATCHES
];

/**
 * This defines a hash of conditions. Each condition is a hash of parameters. The key of the hash
 * is also known as the condition "subject". It is the field of an email message that 
 * the condition is tested against.
 * 
 * <p>
 * The condition parameters are:
 * <ul>
 * <li><b>subjectMod</b>	Type of input widget for the subjectModifier, which is a specifier or 
 *				modifier for the subject (such as which address to look at)</li>
 * <li><b>smOptions</b>		List of possible values for the subjectModifier ({@link ZmFilterRule.TYPE_SELECT})</li>
 * <li><b>ops</b>			Type of input widget for choosing the comparator</li>
 * <li><b>opsOptions</b>	List of possible comparators for this subject ({@link ZmFilterRule.TYPE_SELECT} type)</li>
 * <li><b>value</b>			Type of input widget for specifying the value</li>
 * <li><b>vOptions</b>		List of possible values ({@link ZmFilterRule.TYPE_SELECT} type)</li>
 * <li><b>valueMod</b>		Type of input widget for the valueModifier, which is a specifier or 
 *				modifier for the value (such as units for size)</li>
 * <li><b>vmOptions</b>		List of possible values for the valueModifier ({@link ZmFilterRule.TYPE_SELECT} type)</li>
 * </ul>
 */
ZmFilterRule.CONDITIONS = {};
ZmFilterRule.CONDITIONS[ZmFilterRule.C_FROM] = {
		ops:		ZmFilterRule.TYPE_SELECT,
		opsOptions:	ZmFilterRule.MATCHING_OPS,
		value:		ZmFilterRule.TYPE_INPUT
};
ZmFilterRule.CONDITIONS[ZmFilterRule.C_TO] = {
		ops:		ZmFilterRule.TYPE_SELECT,
		opsOptions:	ZmFilterRule.MATCHING_OPS,
		value:		ZmFilterRule.TYPE_INPUT
};
ZmFilterRule.CONDITIONS[ZmFilterRule.C_CC] = {
		ops:		ZmFilterRule.TYPE_SELECT,
		opsOptions:	ZmFilterRule.MATCHING_OPS,
		value:		ZmFilterRule.TYPE_INPUT
};
ZmFilterRule.CONDITIONS[ZmFilterRule.C_TO_CC] = {
		ops:		ZmFilterRule.TYPE_SELECT,
		opsOptions:	ZmFilterRule.MATCHING_OPS,
		value:		ZmFilterRule.TYPE_INPUT
};
ZmFilterRule.CONDITIONS[ZmFilterRule.C_SUBJECT] = {
		ops:		ZmFilterRule.TYPE_SELECT,
		opsOptions:	ZmFilterRule.MATCHING_OPS,
		value:		ZmFilterRule.TYPE_INPUT
};
ZmFilterRule.CONDITIONS[ZmFilterRule.C_HEADER] = {
		subjectMod:	ZmFilterRule.TYPE_INPUT,
		ops:		ZmFilterRule.TYPE_SELECT,
		opsOptions:	ZmFilterRule.MATCHING_OPS.concat([ZmFilterRule.OP_EXISTS, ZmFilterRule.OP_NOT_EXISTS]),
		value:		ZmFilterRule.TYPE_INPUT
};
ZmFilterRule.CONDITIONS[ZmFilterRule.C_SIZE] = {
		ops:		ZmFilterRule.TYPE_SELECT,
		opsOptions:	[ZmFilterRule.OP_UNDER, ZmFilterRule.OP_NOT_UNDER, ZmFilterRule.OP_OVER, ZmFilterRule.OP_NOT_OVER],
		value:		ZmFilterRule.TYPE_INPUT,
		valueMod:	ZmFilterRule.TYPE_SELECT,
		vmOptions:	[{label: ZmMsg.b, value: "B"}, {label: ZmMsg.kb, value: "K"}, {label: ZmMsg.mb, value: "M"}, {label: ZmMsg.gb, value: "G"}]
};
ZmFilterRule.CONDITIONS[ZmFilterRule.C_DATE] = {
		ops:		ZmFilterRule.TYPE_SELECT,
		opsOptions:	[ZmFilterRule.OP_BEFORE, ZmFilterRule.OP_NOT_BEFORE, ZmFilterRule.OP_AFTER, ZmFilterRule.OP_NOT_AFTER],
		value:		ZmFilterRule.TYPE_CALENDAR
};
ZmFilterRule.CONDITIONS[ZmFilterRule.C_BODY] = {
		ops:		ZmFilterRule.TYPE_SELECT,
		opsOptions:	[ZmFilterRule.OP_CONTAINS, ZmFilterRule.OP_NOT_CONTAINS],
		value:		ZmFilterRule.TYPE_INPUT
};
ZmFilterRule.CONDITIONS[ZmFilterRule.C_ATT] = {
		ops:		ZmFilterRule.TYPE_SELECT,
		opsOptions:	[ZmFilterRule.OP_EXISTS, ZmFilterRule.OP_NOT_EXISTS]
};
ZmFilterRule.CONDITIONS[ZmFilterRule.C_MIME_HEADER] = {
        ops:        ZmFilterRule.TYPE_SELECT,
        opsOptions: [ZmFilterRule.OP_IS_READRECEIPT, ZmFilterRule.OP_NOT_READRECEIPT]

};
ZmFilterRule.CONDITIONS[ZmFilterRule.C_ADDRBOOK] = {
		subjectMod:	ZmFilterRule.TYPE_SELECT,
		smOptions:	[{label: ZmMsg.from, value: "from"}, {label: ZmMsg.to, value: "to"},
					 {label: ZmMsg.cc, value: "cc"}, {label: ZmMsg.toOrCc, value: "to,cc"},
					 {label: ZmMsg.bcc, value: "bcc"}],
		ops:		ZmFilterRule.TYPE_SELECT,
		opsOptions:	[ZmFilterRule.OP_IN, ZmFilterRule.OP_NOT_IN],
		value:		ZmFilterRule.TYPE_SELECT,
		vOptions:	[{label: ZmMsg.myContacts, value: "contacts"}]
};
ZmFilterRule.CONDITIONS[ZmFilterRule.C_INVITE] = {
		ops:		ZmFilterRule.TYPE_SELECT,
		opsOptions:	[ZmFilterRule.OP_IS_REQUESTED, ZmFilterRule.OP_NOT_REQUESTED, ZmFilterRule.OP_IS_REPLIED, ZmFilterRule.OP_NOT_REPLIED]
};

// listed in order we want to display them in the SELECT
ZmFilterRule.CONDITIONS_LIST = [
	ZmFilterRule.C_FROM,
	ZmFilterRule.C_TO,
	ZmFilterRule.C_CC,
	ZmFilterRule.C_TO_CC,
	ZmFilterRule.C_SUBJECT,
	ZmFilterRule.C_HEADER,
	ZmFilterRule.C_SIZE,
	ZmFilterRule.C_DATE,
	ZmFilterRule.C_BODY,
	ZmFilterRule.C_ATT,
	ZmFilterRule.C_MIME_HEADER,
	ZmFilterRule.C_ADDRBOOK,
	ZmFilterRule.C_INVITE
];

// mark certain conditions as headers
ZmFilterRule.IS_HEADER = {};
ZmFilterRule.IS_HEADER[ZmFilterRule.C_FROM]		= true;
ZmFilterRule.IS_HEADER[ZmFilterRule.C_TO]		= true;
ZmFilterRule.IS_HEADER[ZmFilterRule.C_CC]		= true;
ZmFilterRule.IS_HEADER[ZmFilterRule.C_TO_CC]	= true;
ZmFilterRule.IS_HEADER[ZmFilterRule.C_SUBJECT]	= true;
ZmFilterRule.IS_HEADER[ZmFilterRule.C_HEADER]	= true;

// Actions

/**
 * Defines the "keep" action type.
 */
ZmFilterRule.A_KEEP			= "KEEP";
/**
 * Defines the "keep" action type.
 */
ZmFilterRule.A_KEEP_SENT	= "KEEP_SENT";
/**
 * Defines the "folder" action type.
 */
ZmFilterRule.A_FOLDER		= "FOLDER";
/**
 * Defines the "discard" action type.
 */
ZmFilterRule.A_DISCARD		= "DISCARD";
/**
 * Defines the "stop" action type.
 */
ZmFilterRule.A_STOP			= "STOP";
/**
 * Defines the "flag" action type.
 */
ZmFilterRule.A_FLAG			= "FLAG";
/**
 * Defines the "tag" action type.
 */
ZmFilterRule.A_TAG			= "TAG";
/**
 * Defines the "forward" action type.
 */
ZmFilterRule.A_FORWARD		= "FORWARD";

/**
 * Defines the "keep" action name.
 */
ZmFilterRule.A_NAME_KEEP						= "actionKeep";
/**
 * Defines the "keep" action name.
 */
ZmFilterRule.A_NAME_KEEP_SENT					= "actionKeep";
/**
 * Defines the "file into a folder" action name.
 */
ZmFilterRule.A_NAME_FOLDER						= "actionFileInto";
/**
 * Defines the "discard" action name.
 */
ZmFilterRule.A_NAME_DISCARD						= "actionDiscard";
/**
 * Defines the "stop" action name.
 */
ZmFilterRule.A_NAME_STOP						= "actionStop";
/**
 * Defines the "flag" action name.
 */
ZmFilterRule.A_NAME_FLAG						= "actionFlag";
/**
 * Defines the "tag" action name.
 */
ZmFilterRule.A_NAME_TAG							= "actionTag";
/**
 * Defines the "forward" action name.
 */
ZmFilterRule.A_NAME_FORWARD						= "actionRedirect";
/**
 * Defines the "reply" action name.
 */
ZmFilterRule.A_REPLY                            = "actionReply";
/**
 * Defines the "notify" action name.
 */
ZmFilterRule.A_NOTIFY                           = "actionNotify";

ZmFilterRule.A_VALUE = {};
ZmFilterRule.A_VALUE[ZmFilterRule.A_KEEP]		= ZmFilterRule.A_NAME_KEEP;
ZmFilterRule.A_VALUE[ZmFilterRule.A_KEEP_SENT]	= ZmFilterRule.A_NAME_KEEP_SENT;
ZmFilterRule.A_VALUE[ZmFilterRule.A_FOLDER]		= ZmFilterRule.A_NAME_FOLDER;
ZmFilterRule.A_VALUE[ZmFilterRule.A_DISCARD]	= ZmFilterRule.A_NAME_DISCARD;
ZmFilterRule.A_VALUE[ZmFilterRule.A_STOP]		= ZmFilterRule.A_NAME_STOP;
ZmFilterRule.A_VALUE[ZmFilterRule.A_FLAG]		= ZmFilterRule.A_NAME_FLAG;
ZmFilterRule.A_VALUE[ZmFilterRule.A_TAG]		= ZmFilterRule.A_NAME_TAG;
ZmFilterRule.A_VALUE[ZmFilterRule.A_FORWARD]	= ZmFilterRule.A_NAME_FORWARD;
ZmFilterRule.A_VALUE[ZmFilterRule.A_REPLY]      = ZmFilterRule.A_REPLY;
ZmFilterRule.A_VALUE[ZmFilterRule.A_NOTIFY]     = ZmFilterRule.A_NOTIFY;

ZmFilterRule.A_VALUE_MAP = {};
for (var i in ZmFilterRule.A_VALUE) {
	ZmFilterRule.A_VALUE_MAP[ZmFilterRule.A_VALUE[i]] = i;
}
delete i;

ZmFilterRule.A_LABEL = {};
ZmFilterRule.A_LABEL[ZmFilterRule.A_KEEP]		= ZmMsg.keepInInbox;
ZmFilterRule.A_LABEL[ZmFilterRule.A_KEEP_SENT]	= ZmMsg.keepInSent;
ZmFilterRule.A_LABEL[ZmFilterRule.A_FOLDER]		= ZmMsg.fileIntoFolder;
ZmFilterRule.A_LABEL[ZmFilterRule.A_DISCARD]	= ZmMsg.discard;
ZmFilterRule.A_LABEL[ZmFilterRule.A_STOP]		= ZmMsg.stopEvaluation;
ZmFilterRule.A_LABEL[ZmFilterRule.A_FLAG]		= ZmMsg.mark;
ZmFilterRule.A_LABEL[ZmFilterRule.A_TAG]		= ZmMsg.tagWith;
ZmFilterRule.A_LABEL[ZmFilterRule.A_FORWARD]	= ZmMsg.forwardToAddress;

/**
 * This defines a hash of actions. The hash key is known as the action "name".
 * It may or may not take an argument.
 * 
 * <p>
 * The action parameters are:
 * <ul>
 * <li><b>param</b>			the type of input widget for the action's argument</li>
 * <li><b>pOptions</b>		the name/value pairs for args</li>
 * <li><b>precondition</b>	the setting that must be enabled for action to be available
 * 								(preconditions are set by ZmFilterRulesController, after
 * 								 settings are available)</li>
 * </ul>
 */
ZmFilterRule.ACTIONS = {};
ZmFilterRule.ACTIONS[ZmFilterRule.A_KEEP]		= {};
ZmFilterRule.ACTIONS[ZmFilterRule.A_KEEP_SENT]	= {};
ZmFilterRule.ACTIONS[ZmFilterRule.A_DISCARD] = {};
ZmFilterRule.ACTIONS[ZmFilterRule.A_STOP]		= {};
ZmFilterRule.ACTIONS[ZmFilterRule.A_FOLDER]	= {
	param:				ZmFilterRule.TYPE_FOLDER_PICKER
};

ZmFilterRule.ACTIONS[ZmFilterRule.A_FLAG] = {
	param:				ZmFilterRule.TYPE_SELECT,
	// NOTE: If you change the order of these options, also change _setPreconditions!!!
	pOptions:			[{label: ZmMsg.asRead, value: "read"}, {label: ZmMsg.asFlagged, value: "flagged"}]
};

ZmFilterRule.ACTIONS[ZmFilterRule.A_TAG] = {
	param:				ZmFilterRule.TYPE_TAG_PICKER
};

ZmFilterRule.ACTIONS[ZmFilterRule.A_FORWARD] = {
	param:				ZmFilterRule.TYPE_INPUT,
	validationFunction:	ZmPref.validateEmail,
	errorMessage:		ZmMsg.errorInvalidEmail
};


ZmFilterRule.ACTIONS_LIST = [
	ZmFilterRule.A_KEEP,
	ZmFilterRule.A_DISCARD,
	ZmFilterRule.A_FOLDER,
	ZmFilterRule.A_TAG,
	ZmFilterRule.A_FLAG,
	ZmFilterRule.A_FORWARD
];

ZmFilterRule.ACTIONS_OUTGOING_LIST = [
	ZmFilterRule.A_KEEP_SENT,
	ZmFilterRule.A_DISCARD,
	ZmFilterRule.A_FOLDER,
	ZmFilterRule.A_TAG,
	ZmFilterRule.A_FLAG,
	ZmFilterRule.A_FORWARD
];

ZmFilterRule._setPreconditions =
function() {
	ZmFilterRule.ACTIONS[ZmFilterRule.A_FLAG].pOptions[1].precondition = ZmSetting.FLAGGING_ENABLED;
	ZmFilterRule.ACTIONS[ZmFilterRule.A_TAG].precondition = ZmSetting.TAGGING_ENABLED;
	ZmFilterRule.ACTIONS[ZmFilterRule.A_FORWARD].precondition = ZmSetting.FILTERS_MAIL_FORWARDING_ENABLED;
	ZmFilterRule.ACTIONS[ZmFilterRule.A_DISCARD].precondition = ZmSetting.DISCARD_IN_FILTER_ENABLED;
};

ZmFilterRule.prototype.toString =
function() {
	return "ZmFilterRule";
};

/**
 * Gets the rule condition grouping operator.
 * 
 * @return	{constant}	the operator (see <code>ZmFilterRule.GROUP_</code> constants)
 */
ZmFilterRule.prototype.getGroupOp =
function() {
	return this.conditions.condition;
};

/**
 * Sets the rule condition grouping operator to "any" or "all".
 *
 * @param {constant}	groupOp		the grouping operator (see <code>ZmFilterRule.GROUP_</code> constants)
 */
ZmFilterRule.prototype.setGroupOp =
function(groupOp) {
	this.conditions.condition = groupOp || ZmFilterRule.GROUP_ANY;
};

ZmFilterRule.prototype.addCondition =
function(testType, comparator, value, subjectMod, caseSensitive) {
	if (!this.conditions[testType]) {
		this.conditions[testType] = [];
	}

	var cdata = ZmFilterRule.getConditionData(testType, comparator, value, subjectMod, caseSensitive);
	this.conditions[testType].push(cdata);
};

/**
 * Clears the rule conditions list.
 * 
 */
ZmFilterRule.prototype.clearConditions =
function() {
	this.conditions = {};
};

/**
 * Adds an action to the rule actions list.
 *
 * @param {constant}		actionType	the action type (see <code>ZmFilterRule.A_</code> constants)
 * @param {String}	value		the value for the action
 * 
 */
ZmFilterRule.prototype.addAction =
function(actionType, value) {
	var action = ZmFilterRule.A_VALUE[actionType];
	if (!this.actions[action]) {
		this.actions[action] = [];
	}

	var adata = ZmFilterRule.getActionData(actionType, value);
	this.actions[action].push(adata);
};

/**
 * Clears the rule actions list.
 * 
 */
ZmFilterRule.prototype.clearActions =
function() {
	this.actions = {};
};

/**
 * Checks if the if the rule is enabled.
 * 
 * @return	{Boolean}	<code>true</code> if the rule is enabled
 */
ZmFilterRule.prototype.hasValidAction =
function() {
	for (var i in this.actions) {
		var actionIndex = ZmFilterRule.A_VALUE_MAP[i];
		var actionCfg = ZmFilterRule.ACTIONS[actionIndex];
		if ((actionIndex != ZmFilterRule.A_STOP) && (ZmFilterRule.checkPreconditions(actionCfg))) {
			return true;
		}
	}
	return false;
};


// Static methods

ZmFilterRule.getConditionData =
function(testType, comparator, value, subjectMod, caseSensitive) {
	var conditionData = {};

	// add subject modifier
	if (subjectMod &&
		(testType == ZmFilterRule.TEST_HEADER ||
		 testType == ZmFilterRule.TEST_HEADER_EXISTS ||
		 testType == ZmFilterRule.TEST_ADDRBOOK ||
		 testType == ZmFilterRule.TEST_MIME_HEADER))
	{
		conditionData.header = subjectMod;
	}

	// normalize negative operator and add comparator
	var negativeOp;
	switch (comparator) {
		case ZmFilterRule.OP_NOT_IS:		negativeOp = ZmFilterRule.OP_IS; break;
		case ZmFilterRule.OP_NOT_CONTAINS:	negativeOp = ZmFilterRule.OP_CONTAINS; break;
		case ZmFilterRule.OP_NOT_MATCHES:	negativeOp = ZmFilterRule.OP_MATCHES; break;
		case ZmFilterRule.OP_NOT_EXISTS:	negativeOp = ZmFilterRule.OP_EXISTS; break;
		case ZmFilterRule.OP_NOT_UNDER:		negativeOp = ZmFilterRule.OP_UNDER; break;
		case ZmFilterRule.OP_NOT_OVER:		negativeOp = ZmFilterRule.OP_OVER; break;
		case ZmFilterRule.OP_NOT_BEFORE:	negativeOp = ZmFilterRule.OP_BEFORE; break;
		case ZmFilterRule.OP_NOT_AFTER:		negativeOp = ZmFilterRule.OP_AFTER; break;
		case ZmFilterRule.OP_NOT_IN:		negativeOp = ZmFilterRule.OP_IN; break;
        case ZmFilterRule.OP_NOT_REPLIED:   negativeOp = ZmFilterRule.OP_IS_REPLIED; break;
        case ZmFilterRule.OP_NOT_REQUESTED: negativeOp = ZmFilterRule.OP_IS_REQUESTED; break;
        case ZmFilterRule.OP_NOT_READRECEIPT: negativeOp = ZmFilterRule.OP_CONTAINS; break;
	}
	if (negativeOp) {
		conditionData.negative = "1";
	}

	var compType = ZmFilterRule.COMP_TEST_MAP[testType];
	if (compType) {
		conditionData[compType] = ZmFilterRule.OP_VALUE[negativeOp || comparator];
	}

	// add data value
	if (value) {
		switch (testType) {
			case ZmFilterRule.TEST_ADDRBOOK:	conditionData.folderPath = value; break;
			case ZmFilterRule.TEST_SIZE:		conditionData.s = value; break;
			case ZmFilterRule.TEST_DATE:		conditionData.d = value; break;
			default:							conditionData.value = value; break;
		}
	}

	if (testType == ZmFilterRule.TEST_INVITE) {
	    conditionData.method = [{_content:ZmFilterRule.OP_VALUE[negativeOp || comparator]}];
	}
	if (caseSensitive != null) {
		conditionData.caseSensitive = caseSensitive;
	}

	return conditionData;
};

ZmFilterRule.getActionData =
function(actionType, value) {
	var actionData = {};

	switch (actionType) {
		case ZmFilterRule.A_FOLDER:			actionData.folderPath = value; break;
		case ZmFilterRule.A_FLAG:			actionData.flagName = value; break;
		case ZmFilterRule.A_TAG:			actionData.tagName = value; break;
		case ZmFilterRule.A_FORWARD:		actionData.a = value; break;
	}

	return actionData;
};

ZmFilterRule.getDummyRule =
function() {
	var rule = new ZmFilterRule(null, true, {}, {});
	var subjMod = ZmFilterRule.C_HEADER_VALUE[ZmFilterRule.C_SUBJECT];
	rule.addCondition(ZmFilterRule.TEST_HEADER, ZmFilterRule.OP_CONTAINS, "", subjMod);
	rule.addAction(ZmFilterRule.A_KEEP);
	return rule;
};

ZmFilterRule.checkPreconditions =
function(obj) {
    if (!ZmFilterRule.__preConditionsInitialized) {
        ZmFilterRule.__preConditionsInitialized = true;
        ZmFilterRule._setPreconditions();
    }

	var pre = obj && obj.precondition;
	if (!pre) { return true; }

	var conds = AjxUtil.toArray(pre);
	for (var i = 0; i < conds.length; i++) {
		if (!appCtxt.get(conds[i])) {
			return false;
		}
	}
	return true;
};

/**
 * helper method to get the negative comparator
 *
 * @return	{constant}	the operator (see <code>ZmFilterRule.OP_</code> constants)
 */

ZmFilterRule.getNegativeComparator =
function(comparator) {
    var negativeOp;
    
    switch (comparator) {
		case ZmFilterRule.OP_IS:		negativeOp = ZmFilterRule.OP_NOT_IS; break;
		case ZmFilterRule.OP_CONTAINS:	negativeOp = ZmFilterRule.OP_NOT_CONTAINS; break;
		case ZmFilterRule.OP_MATCHES:	negativeOp = ZmFilterRule.OP_NOT_MATCHES; break;
		case ZmFilterRule.OP_EXISTS:	negativeOp = ZmFilterRule.OP_NOT_EXISTS; break;
		case ZmFilterRule.OP_UNDER:		negativeOp = ZmFilterRule.OP_NOT_UNDER; break;
		case ZmFilterRule.OP_OVER:		negativeOp = ZmFilterRule.OP_NOT_OVER; break;
		case ZmFilterRule.OP_BEFORE:	negativeOp = ZmFilterRule.OP_NOT_BEFORE; break;
		case ZmFilterRule.OP_AFTER:		negativeOp = ZmFilterRule.OP_NOT_AFTER; break;
		case ZmFilterRule.OP_IN:		negativeOp = ZmFilterRule.OP_NOT_IN; break;
        case ZmFilterRule.OP_IS_REPLIED:   negativeOp = ZmFilterRule.OP_NOT_REPLIED; break;
        case ZmFilterRule.OP_IS_REQUESTED: negativeOp = ZmFilterRule.OP_NOT_REQUESTED; break;
        case ZmFilterRule.OP_IS_READRECEIPT: negativeOp = ZmFilterRule.OP_NOT_CONTAINS; break;
	}
    return negativeOp;

};
