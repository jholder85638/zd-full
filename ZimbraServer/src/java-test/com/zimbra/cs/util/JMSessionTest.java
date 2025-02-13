/*
 * 
 */
package com.zimbra.cs.util;

import java.util.HashMap;

import javax.mail.internet.MimeMessage;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.Domain;
import com.zimbra.cs.account.MockProvisioning;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.mailclient.smtp.SmtpTransport;
import com.zimbra.cs.mailclient.smtp.SmtpsTransport;

/**
 * Unit test for {@link JMSession}.
 *
 * @author ysasaki
 */
public class JMSessionTest {

    @BeforeClass
    public static void init() throws Exception {
        MockProvisioning prov = new MockProvisioning();
        prov.getLocalServer().setSmtpPort(25);
        Provisioning.setInstance(prov);
    }

    @Test
    public void getTransport() throws Exception {
        Assert.assertSame(SmtpTransport.class,
                JMSession.getSession().getTransport("smtp").getClass());
        Assert.assertSame(SmtpsTransport.class,
                JMSession.getSession().getTransport("smtps").getClass());

        Assert.assertSame(SmtpTransport.class,
                JMSession.getSmtpSession().getTransport("smtp").getClass());
        Assert.assertSame(SmtpsTransport.class,
                JMSession.getSmtpSession().getTransport("smtps").getClass());
    }

    @Test
    public void messageID() throws Exception {
        Provisioning prov = Provisioning.getInstance();
        Domain domain = prov.createDomain("example.com", new HashMap<String, Object>());
        Account account = prov.createAccount("user1@example.com", "test123", new HashMap<String, Object>());

        MimeMessage mm = new MimeMessage(JMSession.getSmtpSession(account));
        mm.saveChanges();
        Assert.assertEquals("message ID contains account domain", domain.getName() + '>', mm.getMessageID().split("@")[1]);
    }
}
