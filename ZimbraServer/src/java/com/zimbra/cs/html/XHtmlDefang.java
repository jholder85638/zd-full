/*
 * 
 */
package com.zimbra.cs.html;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Makes xhtml and svg content safe for display
 * @author jpowers
 *
 */
public class XHtmlDefang extends AbstractDefang{

    @Override
    public void defang(InputStream is, boolean neuterImages, Writer out)
            throws IOException {
        InputSource inputSource = new InputSource(is);
        defang(inputSource, neuterImages, out);
    }

    @Override
    public void defang(Reader reader, boolean neuterImages, Writer out)
            throws IOException {
        InputSource inputSource = new InputSource(reader);
        defang(inputSource, neuterImages, out);
    }

    protected void defang(InputSource is, boolean neuterImages, Writer out)
            throws IOException {
        //get a factory
        SAXParserFactory spf = SAXParserFactory.newInstance();
        try {
            spf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            spf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            //get a new instance of parser            
            SAXParser sp = spf.newSAXParser();
            
            XHtmlDocumentHandler handler = new XHtmlDocumentHandler(out);
            //parse the file and also register this class for call backs
            sp.parse(is, handler);

        }catch(SAXException se) {
            se.printStackTrace();
        }catch(ParserConfigurationException pce) {
            pce.printStackTrace();
        }catch (IOException ie) {
            ie.printStackTrace();
        }
        
        
    }


    
}
