/*
 * 
 */
package com.zimbra.cs.offline.util.yc;

import java.io.InputStream;
import java.io.StringReader;
import java.util.Scanner;

import javax.xml.parsers.DocumentBuilder;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import com.zimbra.cs.offline.util.Xml;
import com.zimbra.cs.offline.util.yc.oauth.OAuthGetRequestTokenRequest;
import com.zimbra.cs.offline.util.yc.oauth.OAuthGetRequestTokenResponse;
import com.zimbra.cs.offline.util.yc.oauth.OAuthGetTokenRequest;
import com.zimbra.cs.offline.util.yc.oauth.OAuthGetTokenResponse;
import com.zimbra.cs.offline.util.yc.oauth.OAuthHelper;
import com.zimbra.cs.offline.util.yc.oauth.OAuthPutContactRequest;
import com.zimbra.cs.offline.util.yc.oauth.OAuthRequest;
import com.zimbra.cs.offline.util.yc.oauth.OAuthResponse;
import com.zimbra.cs.offline.util.yc.oauth.OAuthToken;

public class DeleteContactTest {

    static OAuthToken token;
    static OAuthRequest req;
    static String resp;
    static OAuthResponse response;
    static String id;
    static int rev;

    @BeforeClass
    public static void init() throws Exception {
        req = new OAuthGetRequestTokenRequest(new OAuthToken());
        resp = req.send();
        OAuthResponse response = new OAuthGetRequestTokenResponse(resp);
        System.out.println("paste it into browser and input the highlighted codes below: "
                + response.getToken().getNextUrl());

        System.out.print("Verifier: ");
        Scanner scan = new Scanner(System.in);
        String verifier = scan.nextLine();
        req = new OAuthGetTokenRequest(response.getToken(), verifier);
        resp = req.send();
        response = new OAuthGetTokenResponse(resp);
        token = response.getToken();
    }

    @Test
    public void testAddContact() {
        try {
            InputStream stream = this.getClass().getClassLoader()
                    .getResourceAsStream("yahoo_contacts_client_add_dummy.xml");
            String content = OAuthHelper.getStreamContents(stream);

            req = new OAuthPutContactRequest(token, content);
            resp = req.send();
            System.out.println("resp:" + resp);
            DocumentBuilder builder = Xml.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(resp)));
            Element syncResult = doc.getDocumentElement();
            rev = Xml.getIntAttribute(syncResult, "yahoo:rev");
            System.out.println("rev: " + rev);
            Element result = Xml.getChildren(syncResult).get(0);
            Element contacts = Xml.getChildren(result).get(0);
            boolean success = false;
            for (Element child : Xml.getChildren(contacts)) {
                if ("response".equals(child.getNodeName())) {
                    Assert.assertEquals("success", child.getTextContent());
                    success = true;
                }
                if ("id".equals(child.getNodeName())) {
                    id = child.getTextContent();
                    System.out.println("get new contact id: " + id);
                }
            }
            if (!success) {
                Assert.fail();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail();
        }
    }

    //
    // @Test
    // public void testUpdateContact() {
    // try {
    // InputStream stream =
    // this.getClass().getClassLoader().getResourceAsStream("yahoo_contacts_client_update_dummy.xml");
    // String content = OAuthHelper.getStreamContents(stream);
    // content = String.format(content, id);
    // System.out.println("update put body: " + content);
    // req = new OAuthPutContactRequest(token, content);
    // resp = req.send();
    // System.out.println("resp:" + resp);
    // } catch (Exception e) {
    // e.printStackTrace();
    // Assert.fail();
    // }
    // }

    @Test
    public void testRemoveContact() {
        try {
            InputStream stream = this.getClass().getClassLoader()
                    .getResourceAsStream("yahoo_contacts_client_remove_dummy.xml");
            String content = OAuthHelper.getStreamContents(stream);
            content = String.format(content, id);
            System.out.println("remove put body: " + content);
            req = new OAuthPutContactRequest(token, content);
            resp = req.send();
            System.out.println("resp:" + resp);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail();
        }
    }
}
