/*
 * 
 */
package com.zimbra.cs.filter;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.MailConstants;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.filter.FilterUtil.Comparator;
import com.zimbra.cs.filter.FilterUtil.Condition;
import com.zimbra.cs.filter.FilterUtil.DateComparison;
import com.zimbra.cs.filter.FilterUtil.Flag;
import com.zimbra.cs.filter.FilterUtil.NumberComparison;
import com.zimbra.cs.filter.FilterUtil.StringComparison;
import org.apache.jsieve.TagArgument;
import org.apache.jsieve.parser.SieveNode;
import org.apache.jsieve.parser.generated.ASTcommand;
import org.apache.jsieve.parser.generated.ASTtest;
import org.apache.jsieve.parser.generated.Node;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Iterates a Sieve node tree and calls callbacks at various
 * points.  A subclass can override whichever <tt>visitXXX()</tt>
 * callbacks it is interested in.
 */
public abstract class SieveVisitor {

    protected enum VisitPhase { begin, end }
    
    protected void visitNode(Node node, VisitPhase phase, RuleProperties props)
    throws ServiceException { }
    
    protected void visitRule(Node node, VisitPhase phase, RuleProperties props)
    throws ServiceException { }
    
    protected void visitTest(Node node, VisitPhase phase, RuleProperties props)
    throws ServiceException { }
    
    protected void visitAction(Node node, VisitPhase phase, RuleProperties props)
    throws ServiceException { }
    
    protected void visitHeaderTest(String testEltName, Node node, VisitPhase phase, RuleProperties props,
            List<String> headers, StringComparison comparison, boolean caseSensitive, String value)
    throws ServiceException { }

    protected void visitHeaderExistsTest(Node node, VisitPhase phase, RuleProperties props, String header)
    throws ServiceException { }
    
    protected void visitSizeTest(Node node, VisitPhase phase, RuleProperties props,
        NumberComparison comparison, int size, String sizeString)
    throws ServiceException { }
    
    protected void visitDateTest(Node node, VisitPhase phase, RuleProperties props,
        DateComparison comparison, Date date)
    throws ServiceException { }
    
    protected void visitCurrentTimeTest(Node node, VisitPhase phase, RuleProperties props,
        DateComparison comparison, String timeStr)
    throws ServiceException { }

    protected void visitCurrentDayOfWeekTest(Node node, VisitPhase phase, RuleProperties props, List<String> days)
    throws ServiceException { }

    protected void visitTrueTest(Node node, VisitPhase phase, RuleProperties props)
    throws ServiceException { }

    protected void visitAddressBookTest(Node node, VisitPhase phase, RuleProperties props,
        String header, String folderPath)
    throws ServiceException { }
    
    protected void visitBodyTest(Node node, VisitPhase phase, RuleProperties props, boolean caseSensitive, String value)
    throws ServiceException { }
    
    protected void visitAttachmentTest(Node node, VisitPhase phase, RuleProperties props)
    throws ServiceException { }
    
    protected void visitInviteTest(Node node, VisitPhase phase, RuleProperties props, List<String> methods)
    throws ServiceException { }
    
    protected void visitKeepAction(Node node, VisitPhase phase, RuleProperties props)
    throws ServiceException { }
    
    protected void visitDiscardAction(Node node, VisitPhase phase, RuleProperties props)
    throws ServiceException { }

    protected void visitFileIntoAction(Node node, VisitPhase phase, RuleProperties props, String folderPath)
    throws ServiceException { }
    
    protected void visitFlagAction(Node node, VisitPhase phase, RuleProperties props, Flag flag)
    throws ServiceException { }
    
    protected void visitTagAction(Node node, VisitPhase phase, RuleProperties props, String tagName)
    throws ServiceException { }

    protected void visitRedirectAction(Node node, VisitPhase phase, RuleProperties props, String address)
    throws ServiceException { }
    
    protected void visitReplyAction(Node node, VisitPhase phase, RuleProperties props, String bodyTemplate)
    throws ServiceException { }

    protected void visitNotifyAction(Node node, VisitPhase phase, RuleProperties props, String emailAddr,
            String subjectTemplate, String bodyTemplate, int maxBodyBytes, List<String> origHeaders)
    throws ServiceException { }

    protected void visitStopAction(Node node, VisitPhase phase, RuleProperties props)
    throws ServiceException { }
    
    private static final Set<String> RULE_NODE_NAMES;
    
    static {
        Set<String> names = new HashSet<String>();
        names.add("if");
        names.add("disabled_if");
        RULE_NODE_NAMES = Collections.unmodifiableSet(names);
    }
    
    public class RuleProperties {
        boolean isEnabled = true;
        boolean isNegativeTest = false;
        Condition condition = Condition.allof;
    }
    
    public void accept(Node node)
    throws ServiceException {
        accept(node, null);
    }
    
    private void accept(Node parent, RuleProperties props)
    throws ServiceException {
        visitNode(parent, VisitPhase.begin, props);
        
        int numChildren = parent.jjtGetNumChildren();
        for (int i = 0; i < numChildren; i++) {
            Node node = parent.jjtGetChild(i);

            if (isRuleNode(node)) {
                // New rule tree.
                RuleProperties newProps = new RuleProperties();
                if ("disabled_if".equalsIgnoreCase(getNodeName(node))) {
                    newProps.isEnabled = false;
                }
                accept(node, newProps);
            } else if (node instanceof ASTtest) {
                acceptTest(node, props);
            } else if (node instanceof ASTcommand) {
                acceptAction(node, props);
            } else {
                accept(node, props);
            }
        }
        
        visitNode(parent, VisitPhase.end, props);
    }
    
    private void acceptTest(Node node, RuleProperties props)
    throws ServiceException {
        visitTest(node, VisitPhase.begin, props);
        String nodeName = getNodeName(node);
        
        if ("not".equalsIgnoreCase(nodeName)) {
            props.isNegativeTest = true;
            accept(node, props);
        } else {
            if ("allof".equalsIgnoreCase(nodeName)) {
                props.condition = Condition.allof;
                visitRule(node, VisitPhase.begin, props);
                accept(node, props);
                visitRule(node, VisitPhase.end, props);
            } else if ("anyof".equalsIgnoreCase(nodeName)) {
                props.condition = Condition.anyof;
                visitRule(node, VisitPhase.begin, props);
                accept(node, props);
                visitRule(node, VisitPhase.end, props);
            } else if ("header".equalsIgnoreCase(nodeName) || "mime_header".equalsIgnoreCase(nodeName)) {
                StringComparison comparison = StringComparison.is;
                boolean caseSensitive = false;
                List<String> headers;
                String value;

                int headersArgIndex = 0;
                // There can be up to two tag arguments
                SieveNode firstTagArgNode, secondTagArgNode;
                firstTagArgNode = (SieveNode) getNode(node, 0, 0);
                if (firstTagArgNode.getValue() instanceof TagArgument) {
                    String argStr = stripLeadingColon(firstTagArgNode.getValue().toString());
                    try {
                        // assume that the first tag arg is match-type arg
                        comparison = StringComparison.valueOf(argStr);
                        headersArgIndex ++;
                        secondTagArgNode = (SieveNode) getNode(node, 0 , 1);
                        if (secondTagArgNode.getValue() instanceof TagArgument) {
                            caseSensitive = Comparator.ioctet == Comparator.fromString(getValue(node, 0, 2, 0, 0));
                            headersArgIndex += 2;
                        }
                    } catch (IllegalArgumentException e) {
                        // so the first tag arg is not match-type arg, it must be :comparator arg then
                        caseSensitive = Comparator.ioctet == Comparator.fromString(getValue(node, 0, 1, 0, 0));
                        headersArgIndex += 2;
                        secondTagArgNode = (SieveNode) getNode(node, 0 , 2);
                        if (secondTagArgNode.getValue() instanceof TagArgument) {
                            argStr = stripLeadingColon(secondTagArgNode.getValue().toString());
                            comparison = StringComparison.fromString(argStr);
                            headersArgIndex ++;
                        }
                    }
                }

                headers = getMultiValue(node, 0, headersArgIndex, 0);
                value = getValue(node, 0, headersArgIndex + 1, 0, 0);

                String testEltName = "header".equalsIgnoreCase(nodeName) ?
                        MailConstants.E_HEADER_TEST : MailConstants.E_MIME_HEADER_TEST;
                visitHeaderTest(testEltName, node, VisitPhase.begin, props, headers, comparison, caseSensitive, value);
                accept(node, props);
                visitHeaderTest(testEltName, node, VisitPhase.end, props, headers, comparison, caseSensitive, value);
            } else if ("exists".equalsIgnoreCase(nodeName)) {
                String header = getValue(node, 0, 0, 0, 0);

                visitHeaderExistsTest(node, VisitPhase.begin, props, header);
                accept(node, props);
                visitHeaderExistsTest(node, VisitPhase.end, props, header);
            } else if ("size".equalsIgnoreCase(nodeName)) {
                String s = stripLeadingColon(getValue(node, 0, 0));
                NumberComparison comparison = NumberComparison.fromString(s);
                SieveNode sizeNode = (SieveNode) getNode(node, 0, 1);
                String sizeString = sizeNode.getFirstToken().toString();
                int size;
                try {
                    size = FilterUtil.parseSize(sizeString);
                } catch (NumberFormatException e) {
                    throw ServiceException.INVALID_REQUEST("Invalid size value " + sizeString, e);
                }

                visitSizeTest(node, VisitPhase.begin, props, comparison, size, sizeString);
                accept(node, props);
                visitSizeTest(node, VisitPhase.end, props, comparison, size, sizeString);
            } else if ("date".equalsIgnoreCase(nodeName)) {
                String s = stripLeadingColon(getValue(node, 0, 0));
                DateComparison comparison = DateComparison.fromString(s);
                String dateString = getValue(node, 0, 1, 0, 0);
                Date date = FilterUtil.SIEVE_DATE_PARSER.parse(dateString);
                if (date == null) {
                    throw ServiceException.PARSE_ERROR("Invalid date value: " + dateString, null);
                }

                visitDateTest(node, VisitPhase.begin, props, comparison, date);
                accept(node, props);
                visitDateTest(node, VisitPhase.end, props, comparison, date);
            } else if ("body".equalsIgnoreCase(nodeName)) {
                boolean caseSensitive = false;
                String value;
                if (getNode(node, 0, 1).jjtGetNumChildren() == 0) {
                    // must be :comparator
                    if (!":comparator".equals(getValue(node, 0, 1)))
                        throw ServiceException.PARSE_ERROR("Expected :comparator argument", null);
                    caseSensitive = Comparator.ioctet == Comparator.fromString(getValue(node, 0, 2, 0, 0));
                    value = getValue(node, 0, 3, 0, 0);
                } else {
                    value = getValue(node, 0, 1, 0, 0);
                }

                visitBodyTest(node, VisitPhase.begin, props, caseSensitive, value);
                accept(node, props);
                visitBodyTest(node, VisitPhase.end, props, caseSensitive, value);
            } else if ("attachment".equalsIgnoreCase(nodeName)) {
                visitAttachmentTest(node, VisitPhase.begin, props);
                accept(node, props);
                visitAttachmentTest(node, VisitPhase.end, props);
            } else if ("addressbook".equalsIgnoreCase(nodeName)) {
                String header = getValue(node, 0, 1, 0, 0);
                String folderPath = getValue(node, 0, 2, 0, 0);
                visitAddressBookTest(node, VisitPhase.begin, props, header, folderPath);
                accept(node, props);
                visitAddressBookTest(node, VisitPhase.end, props, header, folderPath);
            } else if ("invite".equalsIgnoreCase(nodeName)) {
                List<String> methods = Collections.emptyList();
                if (getNode(node, 0).jjtGetNumChildren() > 0) {
                    // Arguments node has children.
                    methods = getMultiValue(node, 0, 1, 0);
                }
                visitInviteTest(node, VisitPhase.begin, props, methods);
                accept(node, props);
                visitInviteTest(node, VisitPhase.end, props, methods);
            } else if ("current_time".equalsIgnoreCase(nodeName)) {
                String s = stripLeadingColon(getValue(node, 0, 0));
                DateComparison comparison = DateComparison.fromString(s);
                String timeString = getValue(node, 0, 1, 0, 0);

                visitCurrentTimeTest(node, VisitPhase.begin, props, comparison, timeString);
                accept(node, props);
                visitCurrentTimeTest(node, VisitPhase.end, props, comparison, timeString);
            } else if ("current_day_of_week".equalsIgnoreCase(nodeName)) {
                List<String> days = getMultiValue(node, 0, 1, 0);

                visitCurrentDayOfWeekTest(node, VisitPhase.begin, props, days);
                accept(node, props);
                visitCurrentDayOfWeekTest(node, VisitPhase.end, props, days);
            } else if ("true".equalsIgnoreCase(nodeName)) {
                visitTrueTest(node, VisitPhase.begin, props);
                accept(node, props);
                visitTrueTest(node, VisitPhase.end, props);
            } else {
                ZimbraLog.filter.debug("Ignoring unrecognized test type '%s'.", nodeName);
                accept(node, props);
            }
            
            // Done processing the current test.  Reset the negative test flag for
            // the next test (bug 46007).
            props.isNegativeTest = false;
        }

        visitTest(node, VisitPhase.end, props);
    }
    
    private void acceptAction(Node node, RuleProperties props)
    throws ServiceException {
        visitAction(node, VisitPhase.begin, props);
        String nodeName = getNodeName(node);

        if ("keep".equalsIgnoreCase(nodeName)) {
            visitKeepAction(node, VisitPhase.begin, props);
            accept(node, props);
            visitKeepAction(node, VisitPhase.end, props);
        } else if ("discard".equalsIgnoreCase(nodeName)) {
            visitDiscardAction(node, VisitPhase.begin, props);
            accept(node, props);
            visitDiscardAction(node, VisitPhase.end, props);
        } else if ("fileinto".equalsIgnoreCase(nodeName)) {
            String folderPath = getValue(node, 0, 0, 0, 0);
            visitFileIntoAction(node, VisitPhase.begin, props, folderPath);
            accept(node, props);
            visitFileIntoAction(node, VisitPhase.end, props, folderPath);
        } else if ("flag".equalsIgnoreCase(nodeName)) {
            String s = getValue(node, 0, 0, 0, 0);
            Flag flag = Flag.fromString(s);
            
            visitFlagAction(node, VisitPhase.begin, props, flag);
            accept(node, props);
            visitFlagAction(node, VisitPhase.end, props, flag);
        } else if ("tag".equalsIgnoreCase(nodeName)) {
            String tagName = getValue(node, 0, 0, 0, 0);
            visitTagAction(node, VisitPhase.begin, props, tagName);
            accept(node, props);
            visitTagAction(node, VisitPhase.end, props, tagName);
        } else if ("redirect".equalsIgnoreCase(nodeName)) {
            String address = getValue(node, 0, 0, 0, 0);
            visitRedirectAction(node, VisitPhase.begin, props, address);
            accept(node, props);
            visitRedirectAction(node, VisitPhase.end, props, address);
        } else if ("reply".equalsIgnoreCase(nodeName)) {
            String bodyTemplate = getValue(node, 0, 0, 0, 0);
            visitReplyAction(node, VisitPhase.begin, props, bodyTemplate);
            accept(node, props);
            visitReplyAction(node, VisitPhase.end, props, bodyTemplate);
        } else if ("notify".equalsIgnoreCase(nodeName)) {
            String emailAddr = getValue(node, 0, 0, 0, 0);
            String subjectTemplate = getValue(node, 0, 1, 0, 0);
            String bodyTemplate = getValue(node, 0, 2, 0, 0);
            int numArgs = getNode(node, 0).jjtGetNumChildren();
            int maxBodyBytes = -1;
            List<String> origHeaders = null;
            if (numArgs == 4) {
                if (getNode(node, 0, 3).jjtGetNumChildren() == 0) {
                    maxBodyBytes = Integer.valueOf(getValue(node, 0, 3));
                } else {
                    origHeaders = getMultiValue(node, 0, 3, 0);
                }
            } else if (numArgs == 5) {
                maxBodyBytes = Integer.valueOf(getValue(node, 0, 3));
                origHeaders = getMultiValue(node, 0, 4, 0);
            }
            visitNotifyAction(
                    node, VisitPhase.begin, props, emailAddr, subjectTemplate, bodyTemplate, maxBodyBytes, origHeaders);
            accept(node, props);
            visitNotifyAction(
                    node, VisitPhase.end, props, emailAddr, subjectTemplate, bodyTemplate, maxBodyBytes, origHeaders);
        } else if ("stop".equalsIgnoreCase(nodeName)) {
            visitStopAction(node, VisitPhase.begin, props);
            accept(node, props);
            visitStopAction(node, VisitPhase.end, props);
        } else {
            accept(node, props);
        }
        
        visitAction(node, VisitPhase.end, props);
    }
    
    /**
     * Returns the given node's name in lower case.
     */
    static String getNodeName(Node node) {
        if (node == null || !(node instanceof SieveNode)) {
            return null;
        }
        String name = ((SieveNode) node).getName();
        if (name != null) {
            name = name.toLowerCase();
        }
        return name;
    }

    
    protected Node getNode(Node parent, int ... indexes)
    throws ServiceException {
        Node node = parent;
        for (int index : indexes) {
            if (node.jjtGetNumChildren() == 0) {
                throw ServiceException.PARSE_ERROR(
                        "Subnode " + getNodeName(node) + " of node " + getNodeName(parent) + " has no children.", null);
            }

            if (index >= node.jjtGetNumChildren()) {
                throw ServiceException.PARSE_ERROR(
                        "Subnode " + getNodeName(node) + " of node " + getNodeName(parent) + " has " +
                                node.jjtGetNumChildren() + " children.  Requested child " + index + ".", null);
            }
            node = node.jjtGetChild(index);
        }
        return node;
    }
    
    private String getValue(Node parent, int ... indexes)
    throws ServiceException {
        Node child = getNode(parent, indexes);
        Object value = ((SieveNode) child).getValue();
        if (value == null) {
            return null;
        }
        return value.toString();
    }
    
    private List<String> getMultiValue(Node parent, int ... indexes)
    throws ServiceException {
        Node child = getNode(parent, indexes);
        List<String> values = new ArrayList<String>();
        for (int i = 0; i < child.jjtGetNumChildren(); i++) {
            Object value = ((SieveNode) child.jjtGetChild(i)).getValue();
            values.add(value == null ? null : value.toString());
        }
        return values;
    }
    
    
    private String stripLeadingColon(String s) {
        if (s == null || s.length() == 0 || s.charAt(0) != ':') {
            return s;
        }
        return s.substring(1, s.length());
    }
    
    /**
     * Returns <tt>true</tt> if the given node is the root of a rule node
     * hierarchy.
     */
    private boolean isRuleNode(Node node) {
        if (node == null) {
            return false;
        }
        if (!(node instanceof ASTcommand)) {
            return false;
        }
        String name = getNodeName(node);
        return RULE_NODE_NAMES.contains(name);
    }
}

