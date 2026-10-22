package com.kate.dewtally.validation;

import com.kate.dewtally.document.SourceFormat;
import com.kate.dewtally.invoice.Invoice;
import com.kate.dewtally.invoice.InvoiceRepository;
import com.kate.dewtally.invoice.InvoiceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Runs all rules against an invoice, records the findings and sets the resulting status.
 * The invoice is only changed through its own methods (addIssue, applyValidationResult),
 * so the status logic stays inside the aggregate.
 */
@Service
public class InvoiceValidationService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceValidationService.class);

    private final InvoiceRepository invoices;
    private final List<InvoiceRule> rules;

    public InvoiceValidationService(InvoiceRepository invoices, List<InvoiceRule> rules) {
        this.invoices = invoices;
        this.rules = rules;
    }

    @Transactional
    public InvoiceStatus validate(UUID tenantId, UUID invoiceId, SourceFormat sourceFormat, String profile) {
        Invoice invoice = invoices.findByIdAndTenantId(invoiceId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invoice " + invoiceId + " not found for tenant " + tenantId));

        ValidationContext context = new ValidationContext(invoice, sourceFormat, profile);

        // First collect all findings, then change the invoice: rules always see the original state.
        List<Finding> findings = rules.stream()
                .flatMap(rule -> rule.check(context).stream())
                .toList();

        findings.forEach(finding -> invoice.addIssue(finding.severity(), finding.ruleId(), finding.message()));
        invoice.applyValidationResult();

        log.info("Validated invoice {}: {} ({} findings)", invoiceId, invoice.getStatus(), findings.size());
        // No explicit save: the invoice is managed, changes are written when the transaction commits.
        return invoice.getStatus();
    }
}