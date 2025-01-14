/*
 * 
 */
package com.zimbra.cs.filter;

import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.account.Config;
import com.zimbra.cs.account.MockProvisioning;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.mime.Mime;
import com.zimbra.cs.service.util.SpamHandler;
import com.zimbra.cs.util.JMSession;
import junit.framework.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;

/**
 * Unit tests for spam/whitelist filtering
 */
public class SpamTest {

    @BeforeClass
    public static void init() throws ServiceException {
        MockProvisioning prov = new MockProvisioning();
        Provisioning.setInstance(prov);
        Config config = prov.getConfig();
        config.setSpamWhitelistHeader("X-Whitelist-Flag");
        config.setSpamWhitelistHeaderValue("YES");
    }

    /**
     * Tests whitelisting takes precedence over marking spam.
     */
    @Test
    public void whitelist() throws Exception {
        String raw = "From: sender@zimbra.com\n" +
                "To: recipient@zimbra.com\n" +
                "X-Spam-Flag: YES\n" +
                "Subject: test\n" +
                "\n" +
                "Hello World.";
        MimeMessage msg = new Mime.FixedMimeMessage(JMSession.getSession(), new ByteArrayInputStream(raw.getBytes()));
        Assert.assertTrue(SpamHandler.isSpam(msg));

        // add a whitelist header to the previous message
        raw = "From: sender@zimbra.com\n" +
                "To: recipient@zimbra.com\n" +
                "X-Whitelist-Flag: YES\n" +
                "X-Spam-Flag: YES\n" +
                "Subject: test\n" +
                "\n" +
                "Hello World.";
        msg = new Mime.FixedMimeMessage(JMSession.getSession(), new ByteArrayInputStream(raw.getBytes()));
        Assert.assertFalse(SpamHandler.isSpam(msg));
    }
}
