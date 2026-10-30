package com.kate.dewtally.parsing;

import com.kate.dewtally.xml.EInvoiceNamespaces;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Small helper around XPath with the e-invoice namespace prefixes already registered.
 * XPath objects are not thread-safe, so a new reader is created for every parse.
 */
final class XPathReader {

    private static final Map<String, String> PREFIXES = Map.of(
            "cac", EInvoiceNamespaces.UBL_CAC,
            "cbc", EInvoiceNamespaces.UBL_CBC,
            "rsm", EInvoiceNamespaces.CII_RSM,
            "ram", EInvoiceNamespaces.CII_RAM,
            "udt", EInvoiceNamespaces.CII_UDT);

    private final XPath xpath;

    XPathReader() {
        this.xpath = XPathFactory.newInstance().newXPath();
        this.xpath.setNamespaceContext(new NamespaceContext() {
            @Override
            public String getNamespaceURI(String prefix) {
                return PREFIXES.getOrDefault(prefix, XMLConstants.NULL_NS_URI);
            }

            @Override
            public String getPrefix(String namespaceUri) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Iterator<String> getPrefixes(String namespaceUri) {
                throw new UnsupportedOperationException();
            }
        });
    }

    /**
     * Text of the first match, trimmed; null if missing or empty.
     */
    String text(Node context, String expression) {
        try {
            String value = xpath.evaluate(expression, context).strip();
            return value.isEmpty() ? null : value;
        } catch (XPathExpressionException e) {
            throw new IllegalArgumentException("Invalid XPath expression: " + expression, e);
        }
    }

    /**
     * Text limited to the column length of the database, so long values cannot break the insert.
     */
    String text(Node context, String expression, int maxLength) {
        String value = text(context, expression);
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    /**
     * Number of the first match; null if missing or not a valid number.
     */
    BigDecimal decimal(Node context, String expression) {
        String value = text(context, expression);
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    List<Element> elements(Node context, String expression) {
        try {
            NodeList nodes = (NodeList) xpath.evaluate(expression, context, XPathConstants.NODESET);
            List<Element> result = new ArrayList<>(nodes.getLength());
            for (int i = 0; i < nodes.getLength(); i++) {
                if (nodes.item(i) instanceof Element element) {
                    result.add(element);
                }
            }
            return result;
        } catch (XPathExpressionException e) {
            throw new IllegalArgumentException("Invalid XPath expression: " + expression, e);
        }
    }
}