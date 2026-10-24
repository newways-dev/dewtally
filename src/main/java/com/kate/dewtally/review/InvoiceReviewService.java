package com.kate.dewtally.review;

import com.kate.dewtally.document.InvoiceDocument;
import com.kate.dewtally.document.InvoiceDocumentRepository;
import com.kate.dewtally.invoice.*;
import com.kate.dewtally.validation.InvoiceValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * What a reviewer can do with an invoice: correct its data, accept it or reject it.
 * Every action is recorded in the audit trail in the same transaction as the change itself:
 * either both are saved or neither.
 */
@Service
public class InvoiceReviewService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceReviewService.class);

    private final InvoiceService invoiceService;
    private final InvoiceValidationService validationService;
    private final InvoiceDocumentRepository documents;
    private final InvoiceChangeRepository changes;

    public InvoiceReviewService(InvoiceService invoiceService,
                                InvoiceValidationService validationService,
                                InvoiceDocumentRepository documents,
                                InvoiceChangeRepository changes) {
        this.invoiceService = invoiceService;
        this.validationService = validationService;
        this.documents = documents;
        this.changes = changes;
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found");
    }

    @Transactional
    public InvoiceDetailsResponse correct(UUID tenantId, UUID invoiceId, long expectedVersion,
                                          InvoiceData data, List<InvoiceLineData> lines, String user) {
        Invoice invoice = load(tenantId, invoiceId);
        if (invoice.getVersion() != expectedVersion) {
            // Someone else changed the invoice after the reviewer loaded it.
            throw new ObjectOptimisticLockingFailureException(Invoice.class, invoiceId);
        }

        List<FieldChange> fieldChanges = invoice.correct(data, lines);
        if (fieldChanges.isEmpty()) {
            return details(invoice);
        }
        fieldChanges.forEach(change -> changes.save(InvoiceChange.correction(tenantId, invoiceId, change, user)));

        // Run all rules again on the corrected data. Without the file content, KoSIT is skipped:
        // the original XML no longer matches the corrected values.
        InvoiceDocument document = documents.findById(invoice.getDocumentId())
                .orElseThrow(() -> new IllegalStateException("Document of invoice " + invoiceId + " not found"));
        validationService.validate(tenantId, invoiceId, document.getSourceFormat(), document.getProfile(), null);

        log.info("Invoice {} corrected by {} ({} changes), status {}",
                invoiceId, user, fieldChanges.size(), invoice.getStatus());
        return details(invoice);
    }

    @Transactional
    public InvoiceDetailsResponse accept(UUID tenantId, UUID invoiceId, String comment,
                                         boolean ibanConfirmed, String user) {
        Invoice invoice = load(tenantId, invoiceId);
        InvoiceStatus previous = invoice.getStatus();
        invoice.accept(ibanConfirmed);
        // The audit trail must show that the bank account was explicitly confirmed, and how.
        String recordedComment = ibanConfirmed ? "[IBAN confirmed] " + comment : comment;
        changes.save(InvoiceChange.decision(tenantId, invoiceId, ChangeType.ACCEPTED,
                previous, invoice.getStatus(), recordedComment, user));
        log.info("Invoice {} accepted by {}{}", invoiceId, user, ibanConfirmed ? " (IBAN confirmed)" : "");
        return details(invoice);
    }

    @Transactional
    public InvoiceDetailsResponse reject(UUID tenantId, UUID invoiceId, String reason, String user) {
        Invoice invoice = load(tenantId, invoiceId);
        InvoiceStatus previous = invoice.getStatus();
        invoice.reject();
        changes.save(InvoiceChange.decision(tenantId, invoiceId, ChangeType.REJECTED,
                previous, invoice.getStatus(), reason, user));
        log.info("Invoice {} rejected by {}", invoiceId, user);
        return details(invoice);
    }

    @Transactional(readOnly = true)
    public List<InvoiceChangeResponse> history(UUID tenantId, UUID invoiceId) {
        if (!invoiceService.exists(tenantId, invoiceId)) {
            throw notFound();
        }
        return changes.findByTenantIdAndInvoiceIdOrderByChangedAtAsc(tenantId, invoiceId).stream()
                .map(InvoiceChangeResponse::from)
                .toList();
    }

    private Invoice load(UUID tenantId, UUID invoiceId) {
        return invoiceService.loadForChange(tenantId, invoiceId).orElseThrow(InvoiceReviewService::notFound);
    }

    // Flush first, so the response already carries the new version for the next change.
    private InvoiceDetailsResponse details(Invoice invoice) {
        invoiceService.flush();
        return InvoiceDetailsResponse.from(invoice);
    }
}