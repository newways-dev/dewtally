package com.kate.dewtally.parsing;

import com.kate.dewtally.document.SourceFormat;
import com.kate.dewtally.format.ZugferdAttachments;
import com.kate.dewtally.xml.SafeXml;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;

/**
 * Entry point of the parsing module: picks the right parser for the detected format.
 * Returns empty for formats without structured data (plain PDF, unknown files).
 */
@Component
public class InvoiceParser {

    private static Optional<ParsedInvoice> parseZugferd(byte[] pdf) {
        try {
            return ZugferdAttachments.extractInvoiceXml(pdf)
                    .map(xml -> new CiiInvoiceParser().parse(SafeXml.parse(xml)));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the XML embedded in the ZUGFeRD PDF", e);
        }
    }

    public Optional<ParsedInvoice> parse(SourceFormat format, byte[] content) {
        return switch (format) {
            case UBL -> Optional.of(new UblInvoiceParser().parse(SafeXml.parse(content)));
            case CII -> Optional.of(new CiiInvoiceParser().parse(SafeXml.parse(content)));
            case ZUGFERD -> parseZugferd(content);
            // No structured data: in layer 2 these will be read by the AI extraction worker.
            case PDF, UNKNOWN -> Optional.empty();
        };
    }
}