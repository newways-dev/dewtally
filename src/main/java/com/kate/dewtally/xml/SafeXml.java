package com.kate.dewtally.xml;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * The only place where XML from outside is parsed. Hardened against XXE:
 * DOCTYPE declarations (and with them external entities that could read
 * files from our server) are rejected completely. Real e-invoices never contain them.
 */
public final class SafeXml {

    private SafeXml() {
    }

    public static Document parse(byte[] content) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            // Default handler: no "[Fatal Error]" noise on stderr; parse errors still throw.
            builder.setErrorHandler(new DefaultHandler());
            return builder.parse(new ByteArrayInputStream(content));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new InvalidXmlException("Content is not well-formed or allowed XML", e);
        }
    }
}