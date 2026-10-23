package com.kate.dewtally.ingestion;

import com.kate.dewtally.document.InvoiceDocument;
import com.kate.dewtally.extraction.InvoiceExtraction;
import com.kate.dewtally.invoice.*;
import com.kate.dewtally.parsing.ParsedInvoice;
import com.kate.dewtally.parsing.ParsedLine;
import com.kate.dewtally.validation.InvoiceValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Creates an invoice and validates it in ONE transaction: either the invoice exists and has
 * a final status, or nothing was saved and the document can simply be processed again.
 * <p>
 * A separate bean on purpose: @Transactional only works when called from another bean,
 * not from inside the same class.
 */
@Service
class InvoiceImporter {

    private static final Logger log = LoggerFactory.getLogger(InvoiceImporter.class);

    private final InvoiceService invoiceService;
    private final InvoiceValidationService validationService;

    InvoiceImporter(InvoiceService invoiceService, InvoiceValidationService validationService) {
        this.invoiceService = invoiceService;
        this.validationService = validationService;
    }

    private static List<InvoiceLine> toInvoiceLines(ParsedInvoice parsed) {
        return parsed.lines().stream()
                .map(InvoiceImporter::toInvoiceLine)
                .toList();
    }

    private static InvoiceLine toInvoiceLine(ParsedLine line) {
        return new InvoiceLine(
                line.lineNo(),
                line.description(),
                line.quantity(),
                line.unitCode(),
                line.unitPrice(),
                line.netAmount(),
                line.taxRate());
    }

    @Transactional
    public UUID importStructured(InvoiceDocument document, ParsedInvoice parsed, byte[] content) {
        Invoice invoice = invoiceService.create(
                document.getTenantId(),
                document.getId(),
                ExtractionMethod.STRUCTURED,
                parsed.data(),
                toInvoiceLines(parsed));
        return validate(document, invoice, content);
    }

    @Transactional
    public UUID importExtracted(InvoiceDocument document, InvoiceExtraction extraction, byte[] content) {
        Invoice invoice = invoiceService.createExtracted(
                document.getTenantId(),
                document.getId(),
                extraction.invoice().data(),
                toInvoiceLines(extraction.invoice()),
                extraction.confidence());
        return validate(document, invoice, content);
    }

    private UUID validate(InvoiceDocument document, Invoice invoice, byte[] content) {
        InvoiceStatus status = validationService.validate(
                document.getTenantId(), invoice.getId(), document.getSourceFormat(), document.getProfile(), content);
        log.info("Created invoice {} ({}) from document {}, status {}",
                invoice.getId(), invoice.getExtractionMethod(), document.getId(), status);
        return invoice.getId();
    }
}