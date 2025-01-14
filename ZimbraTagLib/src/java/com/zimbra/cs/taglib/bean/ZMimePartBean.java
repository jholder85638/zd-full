/*
 * 
 */

package com.zimbra.cs.taglib.bean;

import com.zimbra.cs.zclient.ZMessage.ZMimePart;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ZMimePartBean {


    public static final String CT_APP               = "application";
    public static final String CT_APP_ADOBE_PDF	    = "application/pdf";
    public static final String CT_APP_ADOBE_PS	    = "application/postscript";
    public static final String CT_APP_APPLE_DOUBLE 	= "application/applefile";		// IGNORE
    public static final String CT_APP_EXE           = "application/exe";
    public static final String CT_APP_MS_DOWNLOAD	= "application/x-msdownload";
    public static final String CT_APP_MS_EXCEL		= "application/vnd.ms-excel";
    public static final String CT_APP_MS_PPT		= "application/vnd.ms-powerpoint";
    public static final String CT_APP_MS_PROJECT	= "application/vnd.ms-project";
    public static final String CT_APP_MS_TNEF		= "application/ms-tnef"; 		// IGNORE
    public static final String CT_APP_MS_TNEF2 		= "application/vnd.ms-tnef"; 	// IGNORE (added per bug 2339)
    public static final String CT_APP_MS_VISIO		= "application/vnd.visio";
    public static final String CT_APP_MS_WORD		= "application/msword";
    public static final String CT_APP_OCTET_STREAM	= "application/octet-stream";
    public static final String CT_APP_ZIP			= "application/zip";
    public static final String CT_APP_ZIP2			= "application/x-zip-compressed";
    public static final String CT_AUDIO				= "audio";
    public static final String CT_AUDIO_WAV			= "audio/x-wav";
    public static final String CT_AUDIO_MP3			= "audio/mpeg";
    public static final String CT_IMG				= "image";
    public static final String CT_IMG_GIF			= "image/gif";
    public static final String CT_IMG_JPEG			= "image/jpeg";
    public static final String CT_IMG_PNG			= "image/png";
    public static final String CT_IMG_TIFF			= "image/tiff";
    public static final String CT_MSG_RFC822		= "message/rfc822";
    public static final String CT_MULTI			    = "multipart";
    public static final String CT_MULTI_ALT			= "multipart/alternative"; 		// IGNORE
    public static final String CT_MULTI_MIXED		= "multipart/mixed"; 			// IGNORE
    public static final String CT_MULTI_RELATED		= "multipart/related"; 			// IGNORE
    public static final String CT_MULTI_APPLE_DBL 	= "multipart/appledouble"; 		// IGNORE
    public static final String CT_TEXT				= "text";
    public static final String CT_TEXT_RTF			= "text/enriched";
    public static final String CT_TEXT_HTML			= "text/html";
    public static final String CT_TEXT_CAL			= "text/calendar"; 				// IGNORE
    public static final String CT_TEXT_JAVA			= "text/x-java";
    public static final String CT_TEXT_VCARD		= "text/x-vcard";
    public static final String CT_TEXT_PLAIN		= "text/plain";
    public static final String CT_TEXT_XML			= "text/xml";
    public static final String CT_VIDEO				= "video";
    public static final String CT_VIDEO_WMV			= "video/x-ms-wmv";
    public static final String CT_XML_ZIMBRA_SHARE	= "xml/x-zimbra-share";
    public static final String CT_PLAIN_TEXT		= "plain/text"; // strange, saw this type...

    private static final Set<String> sIgnoredTypes =
            new HashSet<String>(Arrays.asList(
                    CT_APP_APPLE_DOUBLE,
                    CT_APP_MS_TNEF,
                    CT_APP_MS_TNEF2,
                    CT_MULTI_ALT,
                    CT_MULTI_MIXED,
                    CT_MULTI_RELATED,
                    CT_MULTI_APPLE_DBL,
                    CT_TEXT_CAL
            ));

    public static boolean isIgnoredPArt(ZMimePart part) {
        if (part.getContentType().equalsIgnoreCase(CT_APP_APPLE_DOUBLE)) {
            if (part.getParent() != null) {
                return part.getParent().getContentType().equalsIgnoreCase(CT_MULTI_APPLE_DBL);
            } else {
                return false;
            }
        } else {
            return sIgnoredTypes.contains(part.getContentType());
        }
    }

    private static final Map<String,String> sTypeToImage = new HashMap<String, String>();

    static {
        sTypeToImage.put(CT_APP,               "zimbra/ImgExeDoc.png");
        sTypeToImage.put(CT_APP_ADOBE_PDF,     "zimbra/ImgPDFDoc.png");
        sTypeToImage.put(CT_APP_ADOBE_PS,      "zimbra/ImgGenericDoc.png");
        sTypeToImage.put(CT_APP_EXE,           "zimbra/ImgExeDoc.png");

        sTypeToImage.put(CT_APP_MS_DOWNLOAD,   "zimbra/ImgExeDoc.png");
        sTypeToImage.put(CT_APP_MS_EXCEL,		"zimbra/ImgMSExcelDoc.png");
        sTypeToImage.put(CT_APP_MS_PPT,			"zimbra/ImgMSPowerpointDoc.png");
        sTypeToImage.put(CT_APP_MS_PROJECT,		"zimbra/ImgMSProjectDoc.png");
        sTypeToImage.put(CT_APP_MS_VISIO,		"zimbra/ImgMSVisioDoc.png");
        sTypeToImage.put(CT_APP_MS_WORD,        "zimbra/ImgMSWordDoc.png");
        sTypeToImage.put(CT_APP_OCTET_STREAM,	"zimbra/ImgUnknownDoc.png");
        sTypeToImage.put(CT_APP_ZIP,            "zimbra/ImgZipDoc.png");
        sTypeToImage.put(CT_APP_ZIP2,			"zimbra/ImgZipDoc.png");
        sTypeToImage.put(CT_AUDIO,				"zimbra/ImgAudioDoc.png");
        sTypeToImage.put(CT_VIDEO,				"zimbra/ImgVideoDoc.png");
        sTypeToImage.put(CT_IMG,                "zimbra/ImgImageDoc.png");
        sTypeToImage.put(CT_MSG_RFC822,			"zimbra/ImgMessageDoc.png");
        sTypeToImage.put(CT_TEXT,				"zimbra/ImgGenericDoc.png");
        sTypeToImage.put(CT_PLAIN_TEXT,			"zimbra/ImgGenericDoc.png");
        sTypeToImage.put(CT_TEXT_HTML, 			"zimbra/ImgHtmlDoc.png");
    }

    private ZMimePart mMimePart;

    public ZMimePartBean(ZMimePart mimePart) { mMimePart = mimePart; }

    public ZMimePart getMimePart() { return mMimePart; }
    
    /** @return "" means top-level part, 1 first part, 1.1 first part of a multipart inside of 1. */
    public String getPartName() { return mMimePart.getPartName(); }

    /** @return name attribute from the Content-Type param list */
    public String getName() { return mMimePart.getName(); }

    /** @return MIME Content-Type */
    public String getContentType() { return mMimePart.getContentType(); }

    /** @return MIME Content-Disposition */
    public String getContentDispostion() { return mMimePart.getContentDispostion(); }

    /** @return filename attribute from the Content-Disposition param list */
    public String getFileName() { return mMimePart.getFileName(); }

    /** @return MIME Content-ID (for display of embedded images) */
    public String getContentId() { return mMimePart.getContentId(); }

    /** @return MIME/Microsoft Content-Location (for display of embedded images) */
    public String getContentLocation() { return mMimePart.getContentLocation(); }

    /** @return MIME Content-Description.  Note cont-desc is not currently used in the code. */
    public String getContentDescription() { return mMimePart.getContentDescription(); }

    /** @return content of the part, if requested */
    public String getContent() { return mMimePart.getContent(); }

    /** @return set to 1, if this part is considered to be the "body" of the message for display purposes */
    public boolean isBody() { return mMimePart.isBody(); }

    /** @return get child parts */
    public List<ZMimePart> getChildren() { return mMimePart.getChildren(); }

    public long getSize() { return mMimePart.getSize(); }

    public String getTextContentAsHtml() {
        return BeanUtils.textToHtml(getContent());
    }

    public String getDisplayName() {
        return getName() != null ? getName() : getFileName();
    }

    public String getDisplaySize() {
        return BeanUtils.displaySize(null, getSize());
    }

    public boolean getIsImage() {
        return getContentType().toLowerCase().startsWith(CT_IMG);
    }

    public boolean getIsAudio() {
        return getContentType().toLowerCase().startsWith(CT_AUDIO);
    }

    public boolean getIsVideo() {
        return getContentType().toLowerCase().startsWith(CT_VIDEO);
    }

    public boolean getIsOctectStream() {
        return getContentType().equalsIgnoreCase(CT_APP_OCTET_STREAM);
    }

    public boolean getIsViewAsHtmlTarget() {
        return getContentType().toLowerCase().startsWith(CT_APP);
    }

    public boolean getIsMssage() {
        return getContentType().equalsIgnoreCase(CT_MSG_RFC822);
    }

    public boolean getIsMultipart() {
        return getContentType().toLowerCase().startsWith(CT_MULTI);
    }

    public boolean getIsApp() {
        return getContentType().toLowerCase().startsWith(CT_APP);   
    }

    public boolean getIsTextPlain() {
        return getContentType().equalsIgnoreCase(CT_TEXT_PLAIN);
    }

    public boolean getIsTextHtml() {
        return getContentType().equalsIgnoreCase(CT_TEXT_HTML);
    }

    public String getImage() {
        String ct = getContentType();
        String image = sTypeToImage.get(ct);
        if (image == null) {
            int index = ct.indexOf('/');
            if (index != -1) {
                ct = ct.substring(0, index);
                image = sTypeToImage.get(ct);
            }
        }
        if (image == null) image = sTypeToImage.get(CT_APP_OCTET_STREAM);
        return image;
    }

    public String toString() {
        return mMimePart.toString();
    }
}
