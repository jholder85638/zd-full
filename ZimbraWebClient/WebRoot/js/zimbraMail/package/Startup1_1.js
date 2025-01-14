/*
 * 
 */
/*
 * Package: Startup1_1
 * 
 * Together with Startup1_2, contains everything needed to support displaying
 * the results of the initial mail search. Startup1 was split into two smaller
 * packages becausing JS parsing cost increases exponentially, so it is best to
 * keep the files under 100K or so.
 */
AjxPackage.require("ajax.core.AjxCore");
AjxPackage.require("ajax.util.AjxUtil");
AjxPackage.require("ajax.core.AjxException");
AjxPackage.require("ajax.util.AjxCookie");
AjxPackage.require("ajax.soap.AjxSoapException");
AjxPackage.require("ajax.soap.AjxSoapFault");
AjxPackage.require("ajax.soap.AjxSoapDoc");
AjxPackage.require("ajax.net.AjxRpcRequest");
AjxPackage.require("ajax.net.AjxRpc");
AjxPackage.require("ajax.util.AjxVector");
AjxPackage.require("ajax.util.AjxStringUtil");
AjxPackage.require("ajax.xml.AjxXmlDoc");
AjxPackage.require("ajax.core.AjxImg");
AjxPackage.require("ajax.core.AjxColor");
AjxPackage.require("ajax.events.AjxEvent");
AjxPackage.require("ajax.events.AjxEventMgr");
AjxPackage.require("ajax.util.AjxTimedAction");
AjxPackage.require("ajax.net.AjxInclude");
AjxPackage.require("ajax.events.AjxListener");
AjxPackage.require("ajax.util.AjxText");
AjxPackage.require("ajax.util.AjxDateUtil");
AjxPackage.require("ajax.util.AjxTimezoneData");
AjxPackage.require("ajax.util.AjxTimezone");
AjxPackage.require("ajax.util.AjxEmailAddress");
AjxPackage.require("ajax.util.AjxHistoryMgr");
AjxPackage.require("ajax.xml.AjxSerializer");

AjxPackage.require("ajax.debug.AjxDebug");
AjxPackage.require("ajax.debug.AjxDebugXmlDocument");

AjxPackage.require("ajax.dwt.core.DwtId");
AjxPackage.require("ajax.dwt.core.Dwt");
AjxPackage.require("ajax.dwt.core.DwtException");
AjxPackage.require("ajax.dwt.core.DwtDraggable");

AjxPackage.require("ajax.dwt.graphics.DwtCssStyle");
AjxPackage.require("ajax.dwt.graphics.DwtPoint");
AjxPackage.require("ajax.dwt.graphics.DwtRectangle");

AjxPackage.require("ajax.dwt.events.DwtEvent");
AjxPackage.require("ajax.dwt.events.DwtEventManager");
AjxPackage.require("ajax.dwt.events.DwtUiEvent");
AjxPackage.require("ajax.dwt.events.DwtDisposeEvent");
AjxPackage.require("ajax.dwt.events.DwtControlEvent");
AjxPackage.require("ajax.dwt.events.DwtFocusEvent");
AjxPackage.require("ajax.dwt.events.DwtKeyEvent");
AjxPackage.require("ajax.dwt.events.DwtMouseEvent");
AjxPackage.require("ajax.dwt.events.DwtMouseEventCapture");
AjxPackage.require("ajax.dwt.events.DwtListViewActionEvent");
AjxPackage.require("ajax.dwt.events.DwtSelectionEvent");
AjxPackage.require("ajax.dwt.events.DwtHtmlEditorStateEvent");
AjxPackage.require("ajax.dwt.events.DwtTreeEvent");
AjxPackage.require("ajax.dwt.events.DwtHoverEvent");
AjxPackage.require("ajax.dwt.events.DwtOutsideMouseEventMgr");

AjxPackage.require("ajax.dwt.keyboard.DwtTabGroupEvent");
AjxPackage.require("ajax.dwt.keyboard.DwtKeyMap");
AjxPackage.require("ajax.dwt.keyboard.DwtKeyMapMgr");
AjxPackage.require("ajax.dwt.keyboard.DwtKeyboardMgr");
AjxPackage.require("ajax.dwt.keyboard.DwtTabGroup");

AjxPackage.require("ajax.dwt.dnd.DwtDragEvent");
AjxPackage.require("ajax.dwt.dnd.DwtDragSource");
AjxPackage.require("ajax.dwt.dnd.DwtDropEvent");
AjxPackage.require("ajax.dwt.dnd.DwtDropTarget");
AjxPackage.require("ajax.dwt.dnd.DwtDragBox");

AjxPackage.require("ajax.dwt.widgets.DwtHoverMgr");
AjxPackage.require("ajax.dwt.widgets.DwtControl");
AjxPackage.require("ajax.dwt.widgets.DwtComposite");
AjxPackage.require("ajax.dwt.widgets.DwtShell");
AjxPackage.require("ajax.dwt.widgets.DwtLabel");
AjxPackage.require("ajax.dwt.widgets.DwtListView");
AjxPackage.require("ajax.dwt.widgets.DwtButton");
AjxPackage.require("ajax.dwt.widgets.DwtLinkButton");
AjxPackage.require("ajax.dwt.widgets.DwtBorderlessButton");
AjxPackage.require("ajax.dwt.widgets.DwtMenuItem");
AjxPackage.require("ajax.dwt.widgets.DwtMenu");
AjxPackage.require("ajax.dwt.widgets.DwtInputField");
AjxPackage.require("ajax.dwt.widgets.DwtBaseDialog");
AjxPackage.require("ajax.dwt.widgets.DwtDialog");
AjxPackage.require("ajax.dwt.widgets.DwtSash");
AjxPackage.require("ajax.dwt.widgets.DwtToolBar");
AjxPackage.require("ajax.dwt.widgets.DwtToolTip");
AjxPackage.require("ajax.dwt.widgets.DwtTreeItem");
AjxPackage.require("ajax.dwt.widgets.DwtHeaderTreeItem");
AjxPackage.require("ajax.dwt.widgets.DwtTree");
AjxPackage.require("ajax.dwt.widgets.DwtText");
AjxPackage.require("ajax.dwt.widgets.DwtIframe");
AjxPackage.require("ajax.dwt.widgets.DwtForm");
AjxPackage.require("ajax.dwt.widgets.DwtMessageDialog");

AjxPackage.require("zimbra.csfe.ZmBatchCommand");
AjxPackage.require("zimbra.csfe.ZmCsfeCommand");
AjxPackage.require("zimbra.csfe.ZmCsfeException");
AjxPackage.require("zimbra.csfe.ZmCsfeResult");

AjxPackage.require("zimbraMail.core.ZmId");
AjxPackage.require("zimbraMail.share.model.events.ZmEvent");
AjxPackage.require("zimbraMail.share.model.events.ZmAppEvent");
AjxPackage.require("zimbraMail.share.model.ZmModel");
AjxPackage.require("zimbraMail.share.model.ZmSetting");
AjxPackage.require("zimbraMail.core.ZmAppCtxt");
AjxPackage.require("zimbraMail.core.ZmOperation");
AjxPackage.require("zimbraMail.core.ZmMimeTable");


