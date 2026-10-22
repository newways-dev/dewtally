package com.kate.dewtally.format;

import com.kate.dewtally.document.SourceFormat;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.common.PDNameTreeNode;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Detects the syntax and profile of an uploaded file by looking at its content.
 * The file extension and the content type sent by the browser are never trusted.
 */
@Component
public class FormatDetector {

    static final String UBL_INVOICE_NS = "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2";
    static final String UBL_CREDIT_NOTE_NS = "urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2";
    static final String UBL_CBC_NS = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
    static final String CII_RSM_NS = "urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100";
    static final String CII_RAM_NS = "urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100";
    private static final Logger log = LoggerFactory.getLogger(FormatDetector.class);
    // Every PDF contains "%PDF-" near the start (the spec allows some leading bytes).
    private static final byte[] PDF_MARKER = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final int PDF_MARKER_SEARCH_LIMIT = 1024;

    // Names of the embedded XML defined by the ZUGFeRD / Factur-X specifications.
    private static final Set<String> EMBEDDED_INVOICE_NAMES =
            Set.of("factur-x.xml", "zugferd-invoice.xml", "xrechnung.xml");

    private static final int MAX_PROFILE_LENGTH = 255;

    private static Optional<byte[]> findEmbeddedInvoiceXml(PDDocument pdf) throws IOException {
        PDDocumentNameDictionary names = pdf.getDocumentCatalog().getNames();
        if (names == null || names.getEmbeddedFiles() == null) {
            return Optional.empty();
        }
        return searchNameTree(names.getEmbeddedFiles());
    }

    // Embedded files are stored in a tree: entries can sit directly in a node or in its children.
    private static Optional<byte[]> searchNameTree(PDNameTreeNode<PDComplexFileSpecification> node)
            throws IOException {
        Map<String, PDComplexFileSpecification> files = node.getNames();
        if (files != null) {
            for (PDComplexFileSpecification spec : files.values()) {
                String name = spec.getFileUnicode() != null ? spec.getFileUnicode() : spec.getFilename();
                if (name != null && EMBEDDED_INVOICE_NAMES.contains(name.toLowerCase(Locale.ROOT))) {
                    PDEmbeddedFile file = spec.getEmbeddedFileUnicode() != null
                            ? spec.getEmbeddedFileUnicode()
                            : spec.getEmbeddedFile();
                    if (file != null) {
                        return Optional.of(file.toByteArray());
                    }
                }
            }
        }

        List<PDNameTreeNode<PDComplexFileSpecification>> kids = node.getKids();
        if (kids != null) {
            for (PDNameTreeNode<PDComplexFileSpecification> kid : kids) {
                Optional<byte[]> found = searchNameTree(kid);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }

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

    private static Document parseSafely(byte[] content)
            throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        // XXE protection: files come from outside, so DOCTYPE declarations are rejected completely.
        // Otherwise an attacker could use an external entity to read files from our server.
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        // Default handler: no "[Fatal Error]" noise on stderr; parse errors still throw.
        builder.setErrorHandler(new DefaultHandler());
        return builder.parse(new ByteArrayInputStream(content));
    }

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

    // ---------- PDF ----------
    private DetectedFormat detectPdf(byte[] content) {
        try (PDDocument pdf = Loader.loadPDF(content)) {
            Optional<byte[]> embeddedXml = findEmbeddedInvoiceXml(pdf);
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

    // ---------- XML ----------
    Optional<DetectedFormat> detectXml(byte[] content) {
        Document document;
        try {
            document = parseSafely(content);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            return Optional.empty(); // not well-formed XML, or rejected for security reasons
        }

        Element root = document.getDocumentElement();
        String namespace = root.getNamespaceURI();
        String name = root.getLocalName();

        boolean ublInvoice = "Invoice".equals(name) && UBL_INVOICE_NS.equals(namespace);
        boolean ublCreditNote = "CreditNote".equals(name) && UBL_CREDIT_NOTE_NS.equals(namespace);
        if (ublInvoice || ublCreditNote) {
            // UBL: <cbc:CustomizationID> directly under the root element.
            String profile = text(firstChild(root, UBL_CBC_NS, "CustomizationID"));
            return Optional.of(new DetectedFormat(SourceFormat.UBL, limit(profile)));
        }

        if ("CrossIndustryInvoice".equals(name) && CII_RSM_NS.equals(namespace)) {
            // CII: rsm:ExchangedDocumentContext / ram:GuidelineSpecifiedDocumentContextParameter / ram:ID
            Element context = firstChild(root, CII_RSM_NS, "ExchangedDocumentContext");
            Element guideline = context == null
                    ? null
                    : firstChild(context, CII_RAM_NS, "GuidelineSpecifiedDocumentContextParameter");
            String profile = guideline == null ? null : text(firstChild(guideline, CII_RAM_NS, "ID"));
            return Optional.of(new DetectedFormat(SourceFormat.CII, limit(profile)));
        }

        return Optional.empty();
    }
}