<%--
 * 
--%>
<%@ tag body-content="scriptless" %>
<%@ attribute name="title" rtexprvalue="true" required="false" %>
<%@ attribute name="rssfeed" rtexprvalue="true" required="false" %>
<%@ taglib prefix="zm" uri="com.zimbra.zm" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="fmt" uri="com.zimbra.i18n" %>

<head>
    <meta http-equiv="Content-Type" content="text/html;charset=utf-8">
    <meta http-equiv="cache-control" content="no-cache"/>
    <meta http-equiv="Pragma" content="no-cache"/>
    <title>
        <c:if test="${empty title}"><fmt:message key="zimbraTitle"/></c:if>
        <c:if test="${!empty title}">${fn:escapeXml(title)}</c:if>
    </title>
	<c:url var='cssurl' value='/css/common,login,images,skin.css'>
		<c:param name="client"	value="standard" />
		<c:param name="skin"	value="${empty requestScope.skin ? skin : requestScope.skin}" />
		<c:param name="v"		value="${initParam.zimbraCacheBusterVersion}" />
	</c:url>
	<link rel="stylesheet" type="text/css" href="${cssurl}" />
	<zm:getFavIcon request="${pageContext.request}" var="favIconUrl" />
	<c:if test="${empty favIconUrl}">
        <fmt:message key="favIconUrl" var="favIconUrl"/>
	</c:if>
    <link rel="SHORTCUT ICON" href="<c:url value='${favIconUrl}'/>">
    <c:if test="${rssfeed}">
    <link rel="alternate" type="application/rss+xml"  title="RSS Feed" href="${requestScope.zimbra_target_item_name}.rss">
    </c:if>
    <jsp:doBody/>
</head>
