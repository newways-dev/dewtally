package com.kate.dewtally.format;

import com.kate.dewtally.document.SourceFormat;
import com.kate.dewtally.xml.EInvoiceNamespaces;
import com.kate.dewtally.xml.InvalidXmlException;
import com.kate.dewtally.xml.SafeXml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Detects the syntax and profile of an uploaded file by looking at its content.
 * The file extension and the content type sent by the browser are never trusted.
 */
@Component
public class FormatDetector {

    private static final Logger log = LoggerFactory.getLogger(FormatDetector.class);

    // Every PDF contains "%PDF-" near the start (the spec allows some leading bytes).
    private static final byte[] PDF_MARKER = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final int PDF_MARKER_SEARCH_LIMIT = 1024;

    private static final int MAX_PROFILE_LENGTH = 255;

    private static boolean looksLikePdf(byte[] content) {
        int limit = Math.min(content.length, PDF_MARKER_SEARCH_LIMIT) - PDF_MARKER.length;
        for (int start = 0; start <= limit; start++) {
            boolean match = true;
            for (int i = 0; i < PDF_MARKER.length; i++) {
                if (content[start + i] != PDF_MARKER[i]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return true;
            }
        }
        return false;
    }

    // ---------- PDF ----------

    private static Element firstChild(Element parent, String namespace, String localName) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element
                    && namespace.equals(element.getNamespaceURI())
                    && localName.equals(element.getLocalName())) {
                return element;
            }
        }
        return null;
    }

    private static String text(Element element) {
        if (element == null) {
            return null;
        }
        String value = element.getTextContent().strip();
        return value.isEmpty() ? null : value;
    }

    // ---------- XML ----------

    private static String limit(String profile) {
        if (profile == null || profile.length() <= MAX_PROFILE_LENGTH) {
            return profile;
        }
        return profile.substring(0, MAX_PROFILE_LENGTH);
    }

    public DetectedFormat detect(byte[] content) {
        if (looksLikePdf(content)) {
            return detectPdf(content);
        }
        return detectXml(content).orElse(DetectedFormat.unknown());
    }

    private DetectedFormat detectPdf(byte[] content) {
        try {
            Optional<byte[]> embeddedXml = ZugferdAttachments.extractInvoiceXml(content);
            if (embeddedXml.isPresent()) {
                Optional<DetectedFormat> embedded = detectXml(embeddedXml.get());
                // ZUGFeRD always embeds CII; anything else is just an attachment.
                if (embedded.isPresent() && embedded.get().format() == SourceFormat.CII) {
                    return new DetectedFormat(SourceFormat.ZUGFERD, embedded.get().profile());
                }
            }
        } catch (IOException e) {
            // Damaged or encrypted PDF: still a PDF, just without readable structured data.
            log.warn("Could not read PDF structure, treating it as a plain PDF: {}", e.getMessage());
        }
        return new DetectedFormat(SourceFormat.PDF, null);
    }

    Optional<DetectedFormat> detectXml(byte[] content) {
        Document document;
        try {
            document = SafeXml.parse(content);
        } catch (InvalidXmlException e) {
            return Optional.empty(); // not well-formed XML, or rejected for security reasons
        }

        Element root = document.getDocumentElement();
        String namespace = root.getNamespaceURI();
        String name = root.getLocalName();

        boolean ublInvoice = "Invoice".equals(name) && EInvoiceNamespaces.UBL_INVOICE.equals(namespace);
        boolean ublCreditNote = "CreditNote".equals(name) && EInvoiceNamespaces.UBL_CREDIT_NOTE.equals(namespace);
        if (ublInvoice || ublCreditNote) {
            // UBL: <cbc:CustomizationID> directly under the root element.
            String profile = text(firstChild(root, EInvoiceNamespaces.UBL_CBC, "CustomizationID"));
            return Optional.of(new DetectedFormat(SourceFormat.UBL, limit(profile)));
        }

        if ("CrossIndustryInvoice".equals(name) && EInvoiceNamespaces.CII_RSM.equals(namespace)) {
            // CII: rsm:ExchangedDocumentContext / ram:GuidelineSpecifiedDocumentContextParameter / ram:ID
            Element context = firstChild(root, EInvoiceNamespaces.CII_RSM, "ExchangedDocumentContext");
            Element guideline = context == null
                    ? null
                    : firstChild(context, EInvoiceNamespaces.CII_RAM, "GuidelineSpecifiedDocumentContextParameter");
            String profile = guideline == null
                    ? null
                    : text(firstChild(guideline, EInvoiceNamespaces.CII_RAM, "ID"));
            return Optional.of(new DetectedFormat(SourceFormat.CII, limit(profile)));
        }

        return Optional.empty();
    }
}