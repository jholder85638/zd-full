<%--
 * 
--%>
<%@ tag body-content="empty" %>
<%@ attribute name="folder" rtexprvalue="true" required="true" type="com.zimbra.cs.taglib.bean.ZFolderBean" %>

<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="fmt" uri="com.zimbra.i18n" %>
<%@ taglib prefix="app" uri="com.zimbra.htmlclient" %>
<%@ taglib prefix="zm" uri="com.zimbra.zm" %>

<c:set var="label" value="${zm:getFolderName(pageContext, folder.id)}"/>
<c:set var="padFudge" value="${folder.hasChildren ? 0 : 20}"/>
<c:set var="color" value="${zm:lightenColor(not empty folder.rgb ? folder.rgb : folder.rgbColor)}"/>
<tr>
    <td nowrap colspan="3" style="background-color:${color}" class='Folder<c:if test="${folder.hasUnread}"> Unread</c:if>'
        style='padding-left: ${padFudge + folder.depth*8}px'>
        <c:url var="url" value="/h/search">
            <c:param name="sfi" value="${folder.id}"/>
            <c:param name="st" value="task"/>
        </c:url>
        <c:if test="${folder.hasChildren}">
                <c:set var="expanded" value="${sessionScope.expanded[folder.id] ne 'collapse'}"/>
                <c:url var="toggleUrl" value="/h/search">
                   <c:param name="st" value="task"/>
                   <c:param name="${expanded ? 'collapse' : 'expand'}" value="${folder.id}"/>
               </c:url>
                <a href="${fn:escapeXml(toggleUrl)}">
                    <app:img src="${expanded ? 'startup/ImgNodeExpanded.png' : 'startup/ImgNodeCollapsed.png'}" altkey="${expanded ? 'ALT_TREE_EXPANDED' : 'ALT_TREE_COLLAPSED'}"/>
                </a>
        </c:if>
        <a href='${fn:escapeXml(url)}'>
            <app:img src="${folder.image}" alt='${fn:escapeXml(label)}'/>
            <span <c:if test="${folder.id eq requestScope.context.selectedId}"> class='ZhTISelected'</c:if>>
             ${fn:escapeXml(zm:truncate(label,20,true))}
            </span>
        </a>
    </td>
</tr>

