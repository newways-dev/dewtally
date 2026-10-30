package com.kate.dewtally.format;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.common.PDNameTreeNode;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;

import java.io.IOException;
import java.util.*;

/**
 * Finds the invoice XML that ZUGFeRD / Factur-X embeds in a PDF.
 * Used by format detection and by parsing.
 */
public final class ZugferdAttachments {

    // Names of the embedded XML defined by the ZUGFeRD / Factur-X specifications.
    private static final Set<String> EMBEDDED_INVOICE_NAMES =
            Set.of("factur-x.xml", "zugferd-invoice.xml", "xrechnung.xml");

    private ZugferdAttachments() {
    }

    public static Optional<byte[]> extractInvoiceXml(byte[] pdfContent) throws IOException {
        try (PDDocument pdf = Loader.loadPDF(pdfContent)) {
            PDDocumentNameDictionary names = pdf.getDocumentCatalog().getNames();
            if (names == null || names.getEmbeddedFiles() == null) {
                return Optional.empty();
            }
            return searchNameTree(names.getEmbeddedFiles());
        }
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
}