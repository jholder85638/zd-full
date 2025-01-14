/*
 * 
 */
package com.zimbra.cs.mime;

import java.util.Arrays;
import java.util.List;

import org.apache.lucene.document.Document;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.zimbra.cs.account.MockProvisioning;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.index.IndexDocument;
import com.zimbra.cs.index.LuceneFields;
import com.zimbra.cs.index.analysis.RFC822AddressTokenStream;

/**
 * Unit test for {@link ParsedMessage}.
 *
 * @author ysasaki
 */
public final class ParsedMessageTest {

    @BeforeClass
    public static void init() {
        System.setProperty("log4j.configuration", "log4j-test.properties");
        Provisioning.setInstance(new MockProvisioning());
    }

    /**
     * @see http://tools.ietf.org/html/rfc2822#appendix-A.5
     */
    @Test
    public void rfc2822a5() throws Exception {
        String raw =
            "From: Pete(A wonderful \\) chap) <pete(his account)@(comment)silly.test(his host)>\n" +
            "To: Chris <c@(xxx bbb)public.example>,\n" +
            "         joe@example.org,\n" +
            "  John <jdoe@one.test> (my dear friend); (the end of the group)\n" +
            "Cc:(Empty list)(start)Undisclosed recipients  :(nobody(that I know))  ;\n" +
            "Date: Thu,\n" +
            "      13\n" +
            "        Feb\n" +
            "          1969\n" +
            "      23:32\n" +
            "               -0330 (Newfoundland Time)\n" +
            "Message-ID:              <testabcd.1234@silly.test>\n" +
            "\n" +
            "Testing.";

        ParsedMessage msg = new ParsedMessage(raw.getBytes(), false);
        List<IndexDocument> docs = msg.getLuceneDocuments();
        Assert.assertEquals(1, docs.size());
        Document doc = docs.get(0).toDocument();

        RFC822AddressTokenStream from = (RFC822AddressTokenStream) doc.getFieldable(
                LuceneFields.L_H_FROM).tokenStreamValue();
        Assert.assertEquals(Arrays.asList("pete", "a", "wonderful", "chap", "pete", "his", "account", "comment",
                "silly.test", "his", "host", "pete@silly.test", "pete", "@silly.test", "silly.test"),
                from.getAllTokens());

        RFC822AddressTokenStream to = (RFC822AddressTokenStream) doc.getFieldable(
                LuceneFields.L_H_TO).tokenStreamValue();
        Assert.assertEquals(Arrays.asList("chris", "c@", "c", "xxx", "bbb", "public.example", "joe@example.org", "joe",
                "@example.org", "example.org", "example", "@example", "john", "jdoe@one.test", "jdoe", "@one.test",
                "one.test", "my", "dear", "friend", "the", "end", "of", "the", "group", "c@public.example", "c",
                "@public.example", "public.example"), to.getAllTokens());

        RFC822AddressTokenStream cc = (RFC822AddressTokenStream) doc.getFieldable(
                LuceneFields.L_H_CC).tokenStreamValue();
        Assert.assertEquals(Arrays.asList("empty", "list", "start", "undisclosed", "recipients", "nobody", "that", "i",
                "know"), cc.getAllTokens());

        RFC822AddressTokenStream xEnvFrom = (RFC822AddressTokenStream) doc.getFieldable(
                LuceneFields.L_H_X_ENV_FROM).tokenStreamValue();
        Assert.assertEquals(0, xEnvFrom.getAllTokens().size());

        RFC822AddressTokenStream xEnvTo = (RFC822AddressTokenStream) doc.getFieldable(
                LuceneFields.L_H_X_ENV_TO).tokenStreamValue();
        Assert.assertEquals(0, xEnvTo.getAllTokens().size());
    }

}
