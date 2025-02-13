/*
 * 
 */
package com.zimbra.qa.unittest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.mail.internet.MimeMessage;
import javax.mail.util.SharedByteArrayInputStream;

import junit.framework.TestCase;

import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.zimbra.common.zmime.ZMimeMessage;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.Config;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mime.Mime;
import com.zimbra.cs.service.util.SpamHandler;
import com.zimbra.cs.util.JMSession;
import com.zimbra.cs.zclient.ZFilterAction;
import com.zimbra.cs.zclient.ZFilterAction.ZFileIntoAction;
import com.zimbra.cs.zclient.ZFilterCondition;
import com.zimbra.cs.zclient.ZFilterCondition.ZHeaderCondition;
import com.zimbra.cs.zclient.ZFilterRule;
import com.zimbra.cs.zclient.ZFilterRules;
import com.zimbra.cs.zclient.ZFolder;
import com.zimbra.cs.zclient.ZMailbox;
import com.zimbra.cs.zclient.ZMessage;

public class TestSpam extends TestCase {

    private static final String NAME_PREFIX = TestSpam.class.getSimpleName();
    private static final String USER_NAME = "user1";
    private static final String SPAM_NAME = "user2";
    private static final String HAM_NAME = "user3";
    private static final String REMOTE_USER_NAME = "user4";

    private String mOriginalSpamHeaderValue;
    private String mOriginalSpamAccount;
    private String mOriginalHamAccount;
    private String mOriginalSieveScript;

    @Override
    public void setUp() throws Exception {
        cleanUp();

        Config config = Provisioning.getInstance().getConfig();
        mOriginalSpamHeaderValue = config.getSpamHeaderValue();
        mOriginalSpamAccount = config.getSpamIsSpamAccount();
        mOriginalHamAccount = config.getSpamIsNotSpamAccount();

        Account account = TestUtil.getAccount(USER_NAME);
        mOriginalSieveScript = account.getMailSieveScript();
    }

    /**
     * Tests {@link Mime#isSpam}.
     */
    public void xtestSpam()
    throws Exception {
        String coreContent = TestUtil.getTestMessage(NAME_PREFIX + " testSpam", USER_NAME, USER_NAME, null);
        MimeMessage msg = new ZMimeMessage(JMSession.getSession(), new SharedByteArrayInputStream(coreContent.getBytes()));
        assertFalse(SpamHandler.isSpam(msg));

        // Test single-line spam header (common case)
        String headerName = Provisioning.getInstance().getConfig().getSpamHeader();
        String singleLineSpamContent = headerName + ": YES\r\n" + coreContent;
        msg = new ZMimeMessage(JMSession.getSession(), new SharedByteArrayInputStream(singleLineSpamContent.getBytes()));
        assertTrue(SpamHandler.isSpam(msg));

        // Test folded spam header (bug 24954).
        Provisioning.getInstance().getConfig().setSpamHeaderValue("spam.*");
        String folderSpamContent = headerName + ": spam, SpamAssassin (score=5.701, required 5,\r\n" +
            "   DCC_CHECK 1.37, FH_RELAY_NODNS 1.45, RATWARE_RCVD_PF 2.88)\r\n" + coreContent;
        msg = new ZMimeMessage(JMSession.getSession(), new SharedByteArrayInputStream(folderSpamContent.getBytes()));
        assertTrue(SpamHandler.isSpam(msg));
    }

    public void testSpamHandler()
    throws Exception {
        Config config = Provisioning.getInstance().getConfig();
        config.setSpamIsSpamAccount(TestUtil.getAddress(SPAM_NAME));
        config.setSpamIsNotSpamAccount(TestUtil.getAddress(HAM_NAME));

        // Set filter rule.
        ZMailbox mbox = TestUtil.getZMailbox(USER_NAME);
        ZFilterCondition cond = new ZHeaderCondition("Subject", ZFilterCondition.HeaderOp.CONTAINS, NAME_PREFIX);
        ZFolder spamFolder = mbox.getFolderById(Integer.toString(Mailbox.ID_FOLDER_SPAM));
        ZFolder inboxFolder = mbox.getFolderById(Integer.toString(Mailbox.ID_FOLDER_INBOX));
        ZFilterAction action = new ZFileIntoAction(spamFolder.getPath());
        ZFilterRule rule = new ZFilterRule(NAME_PREFIX + " testSpamHandler", true, true, Arrays.asList(cond), Arrays.asList(action));
        ZFilterRules rules = new ZFilterRules(Arrays.asList(rule));
        mbox.saveIncomingFilterRules(rules);

        // Confirm that the message was delivered to the Spam folder and that the report was sent.
        String subject = NAME_PREFIX + " testSpamHandler";
        TestUtil.addMessageLmtp(subject, USER_NAME, USER_NAME);
        ZMessage msg = TestUtil.getMessage(mbox, "in:" + spamFolder.getPath() + " subject:\"" + subject + "\"");
        ZMailbox spamMbox = TestUtil.getZMailbox(SPAM_NAME);
        ZMessage reportMsg = TestUtil.waitForMessage(spamMbox, "zimbra-spam-report spam");
        validateSpamReport(TestUtil.getContent(spamMbox, reportMsg.getId()),
            TestUtil.getAddress(USER_NAME), "spam", "filter", null, spamFolder.getPath(), null);
        spamMbox.deleteMessage(reportMsg.getId());

        // Move out of spam folder.
        mbox.moveMessage(msg.getId(), Integer.toString(Mailbox.ID_FOLDER_INBOX));
        ZMailbox hamMbox = TestUtil.getZMailbox(HAM_NAME);
        reportMsg = TestUtil.waitForMessage(hamMbox, "zimbra-spam-report ham");
        validateSpamReport(TestUtil.getContent(hamMbox, reportMsg.getId()),
            TestUtil.getAddress(USER_NAME), "ham", "move", spamFolder.getPath(), inboxFolder.getPath(), null);
        hamMbox.deleteMessage(reportMsg.getId());

        // Move back to spam folder.
        mbox.moveMessage(msg.getId(), Integer.toString(Mailbox.ID_FOLDER_SPAM));
        reportMsg = TestUtil.waitForMessage(spamMbox, "zimbra-spam-report spam");
        validateSpamReport(TestUtil.getContent(spamMbox, reportMsg.getId()),
            TestUtil.getAddress(USER_NAME), "spam", "move", inboxFolder.getPath(), spamFolder.getPath(), null);
        spamMbox.deleteMessage(reportMsg.getId());

        // Move to remote folder.
        ZMailbox remoteMbox = TestUtil.getZMailbox(REMOTE_USER_NAME);
        String mountpointPath = NAME_PREFIX + " remote";
        TestUtil.createMountpoint(remoteMbox, "/Inbox", mbox, mountpointPath);
        ZFolder mountpoint = mbox.getFolderByPath(mountpointPath);
        mbox.moveMessage(msg.getId(), mountpoint.getId());
        reportMsg = TestUtil.waitForMessage(hamMbox, "zimbra-spam-report ham");
        validateSpamReport(TestUtil.getContent(hamMbox, reportMsg.getId()),
            TestUtil.getAddress(USER_NAME), "ham", "remote move", spamFolder.getPath(),
            inboxFolder.getPath(), TestUtil.getAddress(REMOTE_USER_NAME));
        hamMbox.deleteMessage(reportMsg.getId());
    }

    static Pattern PAT_REPORT_LINE = Pattern.compile("(.+): (.+)");

    private void validateSpamReport(String content, String classifiedBy, String classifiedAs, String action,
                                    String sourceFolder, String destFolder, String destMailbox)
    throws IOException {
        // Parse report content.
        BufferedReader reader = new BufferedReader(new StringReader(content));
        String line = null;
        Map<String, String> report = Maps.newHashMap();

        while ((line = reader.readLine()) != null) {
            Matcher m = PAT_REPORT_LINE.matcher(line);
            if (m.matches()) {
                report.put(m.group(1), m.group(2));
            }
        }
        reader.close();

        assertEquals(Strings.nullToEmpty(classifiedBy), Strings.nullToEmpty(report.get("Classified-By")));
        assertEquals(Strings.nullToEmpty(classifiedAs), Strings.nullToEmpty(report.get("Classified-As")));
        assertEquals(Strings.nullToEmpty(action), Strings.nullToEmpty(report.get("Action")));
        assertEquals(Strings.nullToEmpty(sourceFolder), Strings.nullToEmpty(report.get("Source-Folder")));
        assertEquals(Strings.nullToEmpty(destFolder), Strings.nullToEmpty(report.get("Destination-Folder")));
        assertEquals(Strings.nullToEmpty(destMailbox), Strings.nullToEmpty(report.get("Destination-Mailbox")));
    }

    @Override
    public void tearDown()
    throws Exception {
        Config config = Provisioning.getInstance().getConfig();
        config.setSpamHeaderValue(mOriginalSpamHeaderValue);
        config.setSpamIsSpamAccount(mOriginalSpamAccount);
        config.setSpamIsNotSpamAccount(mOriginalHamAccount);

        Account account = TestUtil.getAccount(USER_NAME);
        account.setMailSieveScript(mOriginalSieveScript);

        cleanUp();
    }

    private void cleanUp()
    throws Exception {
        TestUtil.deleteTestData(USER_NAME, NAME_PREFIX);
        TestUtil.deleteTestData(SPAM_NAME, "zimbra-spam-report");
        TestUtil.deleteTestData(HAM_NAME, "zimbra-spam-report");
        TestUtil.deleteTestData(REMOTE_USER_NAME, NAME_PREFIX);
    }

    public static void main(String[] args)
    throws Exception {
        TestUtil.cliSetup();
        TestUtil.runTest(TestSpam.class);
    }
}
