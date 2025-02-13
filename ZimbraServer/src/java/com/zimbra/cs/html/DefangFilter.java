/*
 * 
 */

package com.zimbra.cs.html;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.regex.Pattern;

import org.apache.xerces.xni.Augmentations;
import org.apache.xerces.xni.NamespaceContext;
import org.apache.xerces.xni.QName;
import org.apache.xerces.xni.XMLAttributes;
import org.apache.xerces.xni.XMLLocator;
import org.apache.xerces.xni.XMLResourceIdentifier;
import org.apache.xerces.xni.XMLString;
import org.apache.xerces.xni.XNIException;
import org.cyberneko.html.filters.DefaultFilter;

import com.google.common.base.Strings;
import com.zimbra.common.localconfig.KnownKey;
import com.zimbra.common.localconfig.LC;

/**
 * very Mutated version of ElementRemover.java filter from cyberneko html.
 * change accepted/removed elements to static hashmaps for one-time
 * initialization, switched from Hashtable to HashMap, sanatize
 * attributes, etc.
 *
 * TODO: more checks:
 * allow limited use of <meta> tags? like for Content-Type?
 * make sure any clicked links pop up in new window
 * figure out how to block images by default, and how to re-enable them. styles?
 * strict attr value checking?
 *  don't allow id attr in tags if we aren't putting html into an iframe (I'm assuming we are, and id's in iframes don't conflict with iframes elsewhere)
 */
public class DefangFilter extends DefaultFilter {

    /**
     * disable all form/input type tags
     */
    private static final boolean ENABLE_INPUT_TAGS = true;

    /**
     * enable table tags
     */
    private static final boolean ENABLE_TABLE_TAGS = true;

    /**
     * enable phrase tags (EM, STRONG, CITE, DFN, CODE, SAMP, KBD, VAR, ABBR, ACRONYM)
     */
    private static final boolean ENABLE_PHRASE_TAGS = true;

    /**
     * enable list tags (UL, OL, LI, DL, DT, DD, DIR, MENU)
     */
    private static final boolean ENABLE_LIST_TAGS = true;

    /**
     * enable font style tags (TT, I, B, BIG, SMALL, STRIKE, S, U)
     */
    private static final boolean ENABLE_FONT_STYLE_TAGS = true;

    //
    // Constants
    //

    /** A "null" object. */
    protected static final Object NULL = new Object();

    // regexes inside of attr values to strip out

    private static final Pattern AV_JS_ENTITY = Pattern.compile(LC.defang_av_js_entity.value());
    private static final Pattern AV_SCRIPT_TAG = Pattern.compile(LC.defang_av_script_tag.value(), Pattern.CASE_INSENSITIVE);


 // regex for URLs href. TODO: beef this up
    private static final Pattern VALID_EXT_URL = Pattern.compile(LC.defang_valid_ext_url.value(), Pattern.CASE_INSENSITIVE);
    private static final Pattern VALID_INT_IMG = Pattern.compile(LC.defang_valid_int_img.value());

    //
    // Data
    //

    // information

    /** attr Set cache */
    private static HashMap<String, HashSet<String>> mAttrSetCache = new HashMap<String, HashSet<String>>();

    /** Accepted elements. */
    private static HashMap<String, HashSet<String>> mAcceptedElements = new HashMap<String, HashSet<String>>();

    /** Removed elements. */
    private static HashMap<String, Object> mRemovedElements = new HashMap<String, Object>();

    // state

    private String mBaseHref = null;
    private URI mBaseHrefURI = null;

    /** Strip images */
    boolean mNeuterImages;

    /** The name of the element in the process of being removed. */
    protected String mRemovalElementName;

    /** Tracks the recursive nesting level of the element being removed.
     *  Since we're skipping from the element's open-tag to its close-tag,
     *  we need to make sure not to stop skipping if another element of
     *  the same type was nested in the first.  For instance,
     *  <pre>
     *    &lt;skipme>&lt;foo>&lt;skipme>XX&lt;/skipme>&lt;/foo>&lt;/skipme>
     *  </pre> should not stop skipping at the first <tt>&lt;/skipme></tt>
     *  but rather after the second. */
    protected int mRemovalElementCount;

    /** The style element depth */
    protected int mStyleDepth;

    //private static String[] STD_CORE = { "id", "class", "title", "style" };
    private static String CORE = "id,class,title,style,";
    private static String LANG = "dir,lang,xml:lang,";
    private static String CORE_LANG = CORE+LANG;
    private static String KBD = "accesskey,tabindex,";

    static {
        // set which elements to accept
        acceptElement("a", CORE+KBD+",charset,coords,href,hreflang,name,rel,rev,shape,target,type");
        acceptElement("address", CORE_LANG);
        //acceptElement("base", "href"); //,target");
        acceptElement("bdo", CORE_LANG);
        acceptElement("blockquote", CORE_LANG+"cite");
        acceptElement("body", CORE_LANG+"background"); //+"alink,background,bgcolor,link,text,vlink");
        acceptElement("br", CORE+"clear");
        acceptElement("center", CORE_LANG);
        acceptElement("del", CORE_LANG+"cite,datetime");
        acceptElement("div", CORE_LANG+"align");
        acceptElement("head", LANG); // profile attr removed
        acceptElement("h1", CORE_LANG+"align");
        acceptElement("h2", CORE_LANG+"align");
        acceptElement("h3", CORE_LANG+"align");
        acceptElement("h4", CORE_LANG+"align");
        acceptElement("h5", CORE_LANG+"align");
        acceptElement("h6", CORE_LANG+"align");
        acceptElement("hr", CORE_LANG+"align,noshade,size,width");
        acceptElement("html", LANG+"xmlns");
        acceptElement("img", CORE_LANG+"align,alt,border,height,hspace,ismap,longdesc,src,usemap,vspace,width");
        acceptElement("ins", CORE_LANG+"cite");
        acceptElement("label", CORE_LANG+"for");
        //acceptElement("link", CORE_LANG+"charset,href,hreflang,media,ntarget,rel,rev,type");

        // NOTE: comment out noframes so its text shows up, since we are nuke frame-related tags
        //acceptElement("noframes", CORE_LANG);
        // NOTE: comment out noscript so its text shows up, since we are nuking script tags
        //acceptElement("noscript", CORE_LANG); // maybe convert to always execute if we are stripping script?
        acceptElement("p", CORE_LANG+"align");
        acceptElement("pre", CORE_LANG+"width");
        acceptElement("q", CORE_LANG+"cite");
        acceptElement("span", CORE_LANG);

        acceptElement("style", CORE_LANG);
        acceptElement("sub",  CORE_LANG);
        acceptElement("sup",  CORE_LANG);

        //acceptElement("title", CORE_LANG);
        acceptElement("title", "");

        if (ENABLE_FONT_STYLE_TAGS) {
            acceptElement("b",  CORE_LANG);
            acceptElement("basefont", CORE_LANG+"color,face,size");
            acceptElement("big", CORE_LANG);
            acceptElement("font", CORE_LANG+"color,face,size");
            acceptElement("i", CORE_LANG);
            acceptElement("s", CORE_LANG);
            acceptElement("small", CORE_LANG);
            acceptElement("strike", CORE_LANG);
            acceptElement("tt", CORE_LANG);
            acceptElement("u", CORE_LANG);
        } else {
            // allow the text, just strip the tags
        }

        if (ENABLE_LIST_TAGS) {
            acceptElement("dir", CORE_LANG+"compact");
            acceptElement("dl", CORE_LANG);
            acceptElement("dt", CORE_LANG);
            acceptElement("li", CORE_LANG+"type,value");
            acceptElement("ol", CORE_LANG+"compact,start,type");
            acceptElement("ul", CORE_LANG+"compact,type");
            acceptElement("dd", CORE_LANG);
            acceptElement("menu", CORE_LANG+"compact");
        } else {
            // allow the text, just strip the tags
        }

        if (ENABLE_PHRASE_TAGS) {
            acceptElement("abbr", CORE_LANG);
            acceptElement("acronym", CORE_LANG);
            acceptElement("cite", CORE_LANG);
            acceptElement("code", CORE_LANG);
            acceptElement("dfn", CORE_LANG);
            acceptElement("em", CORE_LANG);
            acceptElement("kbd", CORE_LANG);
            acceptElement("samp", CORE_LANG);
            acceptElement("strong", CORE_LANG);
            acceptElement("var", CORE_LANG);
        } else {
            // allow the text, just strip the tags
        }

        if (ENABLE_TABLE_TAGS) {
            acceptElement("caption", CORE_LANG+"align");
            acceptElement("col",CORE_LANG+"alink,background,char,charoff,span,valign,width");
            acceptElement("colgroup", CORE_LANG+"alink,background,char,charoff,span,valign,width");
            acceptElement("table", CORE_LANG+"align,valign,background,bgcolor,border,cellpadding,cellspacing,frame,rules,summary,width");
            acceptElement("tbody", CORE_LANG+"align,background,char,charoff,valign");
            acceptElement("td", CORE_LANG+"abbr,align,axis,background,bgcolor,char,charoff,colspan,headers,height,nowrap,rowspan,scope,,valign,width");
            acceptElement("tfoot", CORE_LANG+"align,background,char,charoff,valign");
            acceptElement("th", CORE_LANG+"abbr,align,axis,background,bgcolor,char,charoff,colspan,headers,height,nowrap,rowspan,scope,valign,width");
            acceptElement("thead", CORE_LANG+"align,background,char,charoff,valign");
            acceptElement("tr", CORE_LANG+"align,background,bgcolor,char,charoff,valign");
        } else {
            // allow the text, just strip the tags
        }


        if (ENABLE_INPUT_TAGS) {
            acceptElement("area", CORE_LANG+KBD+"alt,coords,href,nohref,shape,target");
            acceptElement("button", CORE_LANG+KBD+"disabled,name,type,value");
            acceptElement("fieldset", CORE_LANG);
            acceptElement("form", CORE_LANG+"action,accept,acceptcharset,enctype,method,name,target");
            acceptElement("input", CORE_LANG+"accept,align,alt,checked,disabled,maxlength,name,readonly,size,type,value,src");
            acceptElement("legend", CORE_LANG+"align");
            acceptElement("map", CORE_LANG+"name");
            acceptElement("optgroup", CORE_LANG+"disabled,label");
            acceptElement("option", CORE_LANG+KBD+"disabled,label,selected,value");
            acceptElement("select", CORE_LANG+KBD+"disabled,multiple,name,size");
            acceptElement("textarea", CORE_LANG+"cols,disabled,name,readonly,rows");
        } else {
            removeElement("area");
            removeElement("button");
            removeElement("fieldset");
            removeElement("form");
            removeElement("input");
            removeElement("legend");
            removeElement("map");
            removeElement("optgroup");
            removeElement("option");
            removeElement("select");
            removeElement("textarea");
        }

        // completely remove these elements and all enclosing tags/text
        removeElement("applet");
        removeElement("frame");
        removeElement("frameset");
        removeElement("iframe");
        removeElement("object");
        removeElement("script");

        // don't remove "content" of these tags since they have none.
        //removeElement("meta");
        //removeElement("param");
    }

    /**
     * @param neuterImages
     */
    public DefangFilter(boolean neuterImages) {
        mNeuterImages = neuterImages;
    }

    /**
     * Specifies that the given element should be accepted and, optionally,
     * which attributes of that element should be kept.
     *
     * @param element The element to accept.
     * @param attributes The comma-seperated list of attributes to be kept or null if no
     *                   attributes should be kept for this element.
     *
     * see #removeElement
     */
    public static void acceptElement(String element, String attributes) {
        element = element.toLowerCase();
        HashSet<String> set = mAttrSetCache.get(attributes);
        if (set != null) {
            //System.out.println(element+" cached set "+set.size());
            mAcceptedElements.put(element, set);
            return;
        }
        set = new HashSet<String>();
        String attrs[] = attributes.toLowerCase().split(",");
        if (attrs != null && attrs.length > 0) {
            for (int i=0; i < attrs.length; i++) {
                //deal with consecutive commas
                if (attrs[i].length() > 0)
                    set.add(attrs[i]);
            }
        }
        mAcceptedElements.put(element, set);
        mAttrSetCache.put(attributes, set);
    }

    /**
     * Specifies that the given element should be completely removed. If an
     * element is encountered during processing that is on the remove list,
     * the element's start and end tags as well as all of content contained
     * within the element will be removed from the processing stream.
     *
     * @param element The element to completely remove.
     */
    public static void removeElement(String element) {
        String key = element.toLowerCase();
        Object value = NULL;
        mRemovedElements.put(key, value);
    }

    //
    // XMLDocumentHandler methods
    //

    // since Xerces-J 2.2.0

    /** Start document. */
    @Override
    public void startDocument(XMLLocator locator, String encoding,
                              NamespaceContext nscontext, Augmentations augs)
    throws XNIException {
        mRemovalElementCount = 0;
        super.startDocument(locator, encoding, nscontext, augs);
    }

    // old methods

    /** Start document. */
    @Override public void startDocument(XMLLocator locator, String encoding, Augmentations augs)
    throws XNIException {
        startDocument(locator, encoding, null, augs);
    }

    /** Start prefix mapping. */
    @Override public void startPrefixMapping(String prefix, String uri, Augmentations augs)
    throws XNIException {
        if (mRemovalElementName == null) {
            super.startPrefixMapping(prefix, uri, augs);
        }
    }

    /** Start element. */
    @Override public void startElement(QName element, XMLAttributes attributes, Augmentations augs)
    throws XNIException {
        String name = element.localpart;
        if (mRemovalElementName == null) {
            if (handleOpenTag(element, attributes))
                super.startElement(element, attributes, augs);
        } else {
            if (name.equalsIgnoreCase(mRemovalElementName))
                mRemovalElementCount++;
        }
        if (name.equalsIgnoreCase("style"))
            mStyleDepth++;
    }

    /** Empty element. */
    @Override public void emptyElement(QName element, XMLAttributes attributes, Augmentations augs)
    throws XNIException {
        if (mRemovalElementName == null && handleOpenTag(element, attributes)) {
            super.emptyElement(element, attributes, augs);
        }
    }

    /** Comment. */
    @Override public void comment(XMLString text, Augmentations augs)
    throws XNIException {
        // we can safely ignore comments
        // they can only provide loop holes for hackers to exploit
        // e.g. CDATA sections are reported as comments with our HTML parser configuration
    }

    /** Processing instruction. */
    @Override public void processingInstruction(String target, XMLString data, Augmentations augs)
    throws XNIException {
        if (mRemovalElementName == null) {
            super.processingInstruction(target, data, augs);
        }
    }

    /** Characters. */
    @Override public void characters(XMLString text, Augmentations augs)
    throws XNIException {
        if (mRemovalElementName == null) {
            if (mStyleDepth > 0) {
                String result = sanitizeStyleValue(text.toString());
                super.characters(new XMLString(result.toCharArray(), 0, result.length()), augs);
            } else {
                super.characters(text, augs);
            }
        }
    }

    private static final Pattern COMMENT = Pattern.compile(LC.defang_comment.value());
    // matches functions (like url(), expression(), etc), except rgb()
    private static final Pattern STYLE_UNWANTED_FUNC =
            Pattern.compile(LC.defang_style_unwanted_func.value(), Pattern.CASE_INSENSITIVE);

    private static String sanitizeStyleValue(String value) {
        // remove comments
        value = COMMENT.matcher(value).replaceAll("");
        // strip off unwanted functions
        return STYLE_UNWANTED_FUNC.matcher(value).replaceAll("");
    }

    /** Ignorable whitespace. */
    @Override public void ignorableWhitespace(XMLString text, Augmentations augs)
    throws XNIException {
        if (mRemovalElementName == null) {
            super.ignorableWhitespace(text, augs);
        }
    }

    /** Start general entity. */
    @Override public void startGeneralEntity(String name, XMLResourceIdentifier id, String encoding, Augmentations augs)
    throws XNIException {
        if (mRemovalElementName == null) {
            super.startGeneralEntity(name, id, encoding, augs);
        }
    }

    /** Text declaration. */
    @Override public void textDecl(String version, String encoding, Augmentations augs)
    throws XNIException {
        if (mRemovalElementName == null) {
            super.textDecl(version, encoding, augs);
        }
    }

    /** End general entity. */
    @Override public void endGeneralEntity(String name, Augmentations augs)
    throws XNIException {
        if (mRemovalElementName == null) {
            super.endGeneralEntity(name, augs);
        }
    }

    /** Start CDATA section. */
    @Override public void startCDATA(Augmentations augs) throws XNIException {
        if (mRemovalElementName == null) {
            super.startCDATA(augs);
        }
    }

    /** End CDATA section. */
    @Override public void endCDATA(Augmentations augs) throws XNIException {
        if (mRemovalElementName == null) {
            super.endCDATA(augs);
        }
    }

    /** End element. */
    @Override public void endElement(QName element, Augmentations augs)
    throws XNIException {
        String name = element.localpart;
        if (mRemovalElementName == null) {
            if (elementAccepted(element.rawname))
                super.endElement(element, augs);
        } else {
            if (name.equalsIgnoreCase(mRemovalElementName) && --mRemovalElementCount == 0)
                mRemovalElementName = null;
        }
        if (name.equalsIgnoreCase("style"))
            mStyleDepth--;
    }

    /** End prefix mapping. */
    @Override public void endPrefixMapping(String prefix, Augmentations augs)
    throws XNIException {
        if (mRemovalElementName == null) {
            super.endPrefixMapping(prefix, augs);
        }
    }

    //
    // Protected methods
    //

    /** Returns true if the specified element is accepted. */
    protected static boolean elementAccepted(String element) {
        String key = element.toLowerCase();
        return mAcceptedElements.containsKey(key);
    }

    /** Returns true if the specified element should be removed. */
    protected static boolean elementRemoved(String element) {
        String key = element.toLowerCase();
        return mRemovedElements.containsKey(key);
    }

    /** Handles an open tag. */
    protected boolean handleOpenTag(QName element, XMLAttributes attributes) {
        String eName = element.rawname.toLowerCase();
        if (eName.equals("base")) {
            int index = attributes.getIndex("href");
            if (index != -1) {
                mBaseHref = attributes.getValue(index);
                if (mBaseHref != null) {
                    try {
                        mBaseHrefURI = new URI(mBaseHref);
                    } catch (URISyntaxException e) {
                        if (!mBaseHref.endsWith("/"))
                            mBaseHref += "/";
                    }
                }
            }
        }
        if (elementAccepted(element.rawname)) {
            HashSet<String> value = mAcceptedElements.get(eName);
            if (value != NULL) {
                HashSet<String> anames = value;
                int attributeCount = attributes.getLength();
                for (int i = 0; i < attributeCount; i++) {
                    String aName = attributes.getQName(i).toLowerCase();
                    // remove the attribute if it isn't in the list of accepted names
                    // or it has invalid content
                    if (!anames.contains(aName) || removeAttrValue(eName, aName, attributes, i)) {
                        attributes.removeAttributeAt(i--);
                        attributeCount--;
                    } else {
                        sanatizeAttrValue(eName, aName, attributes, i);
                    }
                }
            } else {
                attributes.removeAllAttributes();
            }

            if (eName.equals("img") || eName.equals("input")) {
                fixUrlBase(attributes, "src");
            } else if (eName.equals("a") || eName.equals("area")) {
                fixUrlBase(attributes, "href");
            }
            fixUrlBase(attributes, "background");


            if (eName.equals("a") || eName.equals("area")) {
                fixATag(attributes);
            }
            if (mNeuterImages) {
                String srcValue = Strings.nullToEmpty(attributes.getValue("src"));
                if((eName.equals("img") || eName.equals("input")) && 
                 (VALID_EXT_URL.matcher(srcValue).find() || // check for valid urls, and definitely defang
                 !VALID_INT_IMG.matcher(srcValue).find())) { 
                        neuterTag(attributes, "src");    
                        }
                neuterTag(attributes, "background");
                }

            return true;
        } else if (elementRemoved(element.rawname)) {
            mRemovalElementName = element.rawname;
            mRemovalElementCount = 1;
        }
        return false;
    }

    private void fixUrlBase(XMLAttributes attributes, String attrName) {
        int index = attributes.getIndex(attrName);
        if (index != -1) {
            String value = attributes.getValue(index);
            if (mBaseHref != null && value != null && value.indexOf(":") == -1) {
                if (mBaseHrefURI != null) {
                    try {
                        attributes.setValue(index, mBaseHrefURI.resolve(value).toString());
                        return;
                    } catch (IllegalArgumentException e) {
                        // ignore and do string-logic
                    }
                }
                attributes.setValue(index, mBaseHref+value);
            }
        }
    }

    /**
     * @param attributes
     */
    private void neuterTag(XMLAttributes attributes, String aName) {
        String df_aName = "df"+aName;
        int dfIndex = attributes.getIndex(df_aName);
        int index = attributes.getIndex(aName);
        if (index != -1) {
            String aValue = attributes.getValue(index);
            if (dfIndex != -1) {
                attributes.setValue(dfIndex, aValue);
            } else {
                attributes.addAttribute(new QName("", df_aName, df_aName, null), "CDATA", aValue);
            }
            attributes.removeAttributeAt(index);
            // remove dups if there are multiple src attributes
            index = attributes.getIndex(aName);
            while (index != -1) {
                attributes.removeAttributeAt(index);
                index = attributes.getIndex(aName);
            }
        }
    }

    /**
     * make sure all <a> tags have a target="_blank" attribute set.
     * @param attributes
     */
    private void fixATag(XMLAttributes attributes) {
        // BEGIN: bug 7927
        int index = attributes.getIndex("href");
        if (index == -1)	// links that don't have a href don't need target="_blank"
            return;
        String href = attributes.getValue(index);
        if (href.indexOf('#') == 0) // LOCAL links don't need target="_blank"
            return;
        // END: bug 7927
        index = attributes.getIndex("target");
        if (index != -1) {
            attributes.setValue(index, "_blank");
        } else {
            attributes.addAttribute(new QName("", "target", "target", null), "CDATA", "_blank");
        }
    }
    /**
     * Checks to see if an attr value should just be removed
     * @param eName The element name
     * @param aName The attribute name
     * @param attributes The set of the attribtues
     * @param i The index of the attribute
     * @return true if the attr should be removed, false if not
     */
    private boolean removeAttrValue(String eName, String aName, XMLAttributes attributes, int i) {
        String value = attributes.getValue(i);
        // get rid of any spaces that might throw off the regex
        value = value == null? null: value.trim();
        if (aName.equalsIgnoreCase("href") || aName.equalsIgnoreCase("longdesc") || aName.equalsIgnoreCase("usemap")){
			if (!VALID_EXT_URL.matcher(value).find()) {
				return true;
			}
		}
        // We'll treat the SRC a little different since deleting it
        // may annoy the front end. Here, we'll check for
        // a valid url as well as just a valid filename in the
        // case that its an inline image
        if(aName.equals("src")) {
            if (!(VALID_EXT_URL.matcher(value).find() ||
                VALID_INT_IMG.matcher(value).find())) {
                attributes.setValue(i, "#");
                return false;
            }
        }
        return false;
    }
    /**
     * sanitize an attr value. For now, this means stirpping out Java Script entity tags &{...},
     * and <script> tags.
     *
     *
     */
    private void sanatizeAttrValue(String eName, String aName, XMLAttributes attributes, int i) {
        String value = attributes.getValue(i);
        String result = AV_JS_ENTITY.matcher(value).replaceAll("JS-ENTITY-BLOCKED");
        result = AV_SCRIPT_TAG.matcher(result).replaceAll("SCRIPT-TAG-BLOCKED");
        if (aName.equalsIgnoreCase("style")) {
            result = sanitizeStyleValue(value);
        }

        if (!result.equals(value)) {
            attributes.setValue(i, result);
        }
    }

}
