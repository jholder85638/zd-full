/*
 * 
 */
package com.zimbra.common.mime;

import java.nio.charset.Charset;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.zimbra.common.util.CharsetUtil;

public class MimeHeaderTest {

    @SuppressWarnings("deprecation")
    @Test
    public void addressHeader() {
        String src = "mine:=?us-ascii?Q?Bob_?=\t=?us-ascii?Q?the_Builder_1?= <bob@example.com>;,=?us-ascii?Q?Bob the Builder 2?= <bob@example.com>";
        MimeAddressHeader hdr = new MimeAddressHeader("To", src);
        List<InternetAddress> iaddrs = hdr.expandAddresses();
        Assert.assertEquals(2, iaddrs.size());
        Assert.assertEquals("Bob the Builder 1", iaddrs.get(0).getPersonal());
        Assert.assertEquals("bob@example.com", iaddrs.get(0).getAddress());
        Assert.assertEquals("Bob the Builder 2", iaddrs.get(1).getPersonal());
        Assert.assertEquals("bob@example.com", iaddrs.get(1).getAddress());
    }

    @Test
    public void encode() {
        String src = "Re: Pru\u00ee Loo";
        Assert.assertEquals("=?utf-8?Q?Re=3A_Pru=C3=AE?= Loo", MimeHeader.escape(src, null, true));
        Assert.assertEquals("Re: =?utf-8?B?UHJ1w64=?= Loo", MimeHeader.escape(src, null, false));

        src = "Re: Pru\u00ee Loo ";
        Assert.assertEquals("=?utf-8?Q?Re=3A_Pru=C3=AE_Loo_?=", MimeHeader.escape(src, null, true));
        Assert.assertEquals("Re: =?utf-8?Q?Pru=C3=AE_Loo_?=", MimeHeader.escape(src, null, false));

        src = "Fwd:   Pru\u00ee Loo";
        Assert.assertEquals("=?utf-8?Q?Fwd=3A___Pru=C3=AE?= Loo", MimeHeader.escape(src, null, true));
        Assert.assertEquals("Fwd:   =?utf-8?B?UHJ1w64=?= Loo", MimeHeader.escape(src, null, false));

        src = "Prue Loo  ";
        Assert.assertEquals("\"Prue Loo  \"", MimeHeader.escape(src, null, true));
        Assert.assertEquals("Prue Loo  ", MimeHeader.escape(src, null, false));

        src = "Fwd:  Pru\u00ee Loo ";
        Assert.assertEquals("=?utf-8?Q?Fwd=3A__Pru=C3=AE_Loo_?=", MimeHeader.escape(src, null, true));
        Assert.assertEquals("Fwd:  =?utf-8?Q?Pru=C3=AE_Loo_?=", MimeHeader.escape(src, null, false));

        src = "Prue  Loo";
        Assert.assertEquals("\"Prue  Loo\"", MimeHeader.escape(src, null, true));
        Assert.assertEquals("Prue  Loo", MimeHeader.escape(src, null, false));

        src = "Prue   Loo";
        Assert.assertEquals("\"Prue   Loo\"", MimeHeader.escape(src, null, true));
        Assert.assertEquals("Prue   Loo", MimeHeader.escape(src, null, false));

        src = " Prue  Loo ";
        Assert.assertEquals("\" Prue  Loo \"", MimeHeader.escape(src, null, true));
        Assert.assertEquals(" Prue  Loo ", MimeHeader.escape(src, null, false));

        src = "Pru\u00ee";
        Assert.assertEquals("=?utf-8?B?UHJ1w64=?=", MimeHeader.escape(src, null, true));
        Assert.assertEquals("=?utf-8?B?UHJ1w64=?=", MimeHeader.escape(src, null, false));

        src = "Pru\u00ee";
        Assert.assertEquals("=?iso-8859-1?Q?Pru=EE?=", MimeHeader.escape(src, CharsetUtil.ISO_8859_1, true));
        Assert.assertEquals("=?iso-8859-1?Q?Pru=EE?=", MimeHeader.escape(src, CharsetUtil.ISO_8859_1, false));

        Charset iso_8859_7 = CharsetUtil.toCharset("iso-8859-7");
        if (iso_8859_7 != null) {
            src = "Pru\u00ee";
            Assert.assertEquals("=?utf-8?B?UHJ1w64=?=", MimeHeader.escape(src, iso_8859_7, true));
            Assert.assertEquals("=?utf-8?B?UHJ1w64=?=", MimeHeader.escape(src, iso_8859_7, false));
        }

        src = "lskdhf lkshfl aksjhlfi ahslkfu Pru\u00ee uey liufhlasuifh haskjhf lkajshf lkajshflkajhslkfj hals\u00e4kjhf laskjhdflaksjh ksjfh ka";
        Assert.assertEquals(
                "lskdhf lkshfl aksjhlfi ahslkfu =?utf-8?Q?Pru=C3=AE_uey_liufhlasuifh_haskjhf_lkajshf_lkajshflkajhslkfj_hals=C3=A4kjhf?= laskjhdflaksjh ksjfh ka",
                MimeHeader.escape(src, null, true));
        Assert.assertEquals(
                "lskdhf lkshfl aksjhlfi ahslkfu =?utf-8?Q?Pru=C3=AE_uey_liufhlasuifh_haskjhf_lkajshf_lkajshflkajhslkfj_hals=C3=A4kjhf?= laskjhdflaksjh ksjfh ka",
                MimeHeader.escape(src, null, false));

        src = "\u00eb\u00ec\u00ed\u00ee";
        Assert.assertEquals("=?utf-8?B?w6vDrMOtw64=?=", MimeHeader.escape(src, null, true));
        Assert.assertEquals("=?utf-8?B?w6vDrMOtw64=?=", MimeHeader.escape(src, null, false));

        src = "\u00eb\u00ec\u00ed\u00ee";
        Assert.assertEquals("=?iso-8859-1?B?6+zt7g==?=", MimeHeader.escape(src, CharsetUtil.ISO_8859_1, true));
        Assert.assertEquals("=?iso-8859-1?B?6+zt7g==?=", MimeHeader.escape(src, CharsetUtil.ISO_8859_1, false));

    }

    @Test
    public void decode() {
        String src = "RE: [Bug 30944]=?UTF-8?Q?=20Meeting=20invitation=20that=E2=80=99s=20created=20within=20exchange=20containing=20=C3=A5=C3=A4=C3=B6=20will=20show=20within=20the=20calendar=20and=20acceptance=20notification=20as=20?=?????";
        Assert.assertEquals(
                "RE: [Bug 30944] Meeting invitation that\u2019s created within exchange containing \u00e5\u00e4\u00f6 will show within the calendar and acceptance notification as ?????",
                MimeHeader.decode(src));

        src = "=?utf-8?Q?Hambone_x?=";
        Assert.assertEquals("Hambone x", MimeHeader.decode(src));

        src = "=?utf-8?Q?Ha?==?utf-8?Q?mbone?= x";
        Assert.assertEquals("Hambone x", MimeHeader.decode(src));

        src = "=?utf-8?Q?Ha?=    =?utf-8?Q?mbone x?=";
        Assert.assertEquals("Hambone x", MimeHeader.decode(src));

        src = "=?utf-8?Q?Ha?=  m =?utf-8?Q?bone?=";
        Assert.assertEquals("Ha  m bone", MimeHeader.decode(src));

        src = "=?utf-8?Q?Ha?= \r\n m =?utf-8?Q?bone?=";
        Assert.assertEquals("Ha  m bone", MimeHeader.decode(src));

        src = "=?utf-8?Q?Ha?=    =?utf-8??mbone?=";
        Assert.assertEquals("Ha    =?utf-8??mbone?=", MimeHeader.decode(src));

        src = "=?utf-8??Broken?=";
        Assert.assertEquals(src, MimeHeader.decode(src));

        src = "test\r\n one";
        Assert.assertEquals("test one", MimeHeader.decode(src));

        src = "1564 =?ISO-8859-1?Q?boo_1565_?=\n =?ISO-8859-1?Q?hoo?=";
        Assert.assertEquals("1564 boo 1565 hoo", MimeHeader.decode(src));
    }

    @Test
    public void unfold() {
        Assert.assertEquals("dog", MimeHeader.unfold("dog"));
        Assert.assertEquals("dog", MimeHeader.unfold("dog\n"));
        Assert.assertEquals("dog", MimeHeader.unfold("\ndog"));
        Assert.assertEquals("dog cat", MimeHeader.unfold("dog\n cat"));
    }

    @Test
    public void charsetSuperset() {
        // decoding should automatically fall back to the superset charset
        boolean hasWindows1252 = CharsetUtil.toCharset(MimeConstants.P_CHARSET_WINDOWS_1252) != null;
        String euro200 = "=?ISO-8859-1?Q?=80200?=";
        byte[] euro300 = new byte[] { (byte) 0x80, '3', '0', '0' };
        if (hasWindows1252) {
            Assert.assertEquals("\u20ac200", MimeHeader.decode(euro200));
            Assert.assertEquals("\u20ac300", new MimeHeader("X-Cost", euro300).getValue("iso-8859-1"));
        } else {
            Assert.assertEquals("\u0080200", MimeHeader.decode(euro200));
            Assert.assertEquals("\u0080300", new MimeHeader("X-Cost", euro300).getValue("iso-8859-1"));
        }

        // encoding should *not* use a superset charset
        Assert.assertEquals("=?UTF-8?Q?=E2=82=AC200000?=", MimeHeader.escape("\u20ac200000", CharsetUtil.ISO_8859_1, true).toUpperCase());
    }
}
