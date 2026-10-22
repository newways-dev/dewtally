package com.kate.dewtally.validation;

import com.kate.dewtally.invoice.Invoice;
import com.kate.dewtally.invoice.InvoiceRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Same supplier + same invoice number again: possibly the same invoice sent twice.
 * Only a warning, because it may also be a corrected invoice; a person decides.
 * This is the business protection against double payment (the SHA-256 check only
 * catches the identical file, not the same invoice in another format).
 */
@Component
public class DuplicateInvoiceRule implements InvoiceRule {

    private final InvoiceRepository invoices;

    public DuplicateInvoiceRule(InvoiceRepository invoices) {
        this.invoices = invoices;
    }

    @Override
    public List<Finding> check(ValidationContext context) {
        Invoice invoice = context.invoice();
        if (invoice.getInvoiceNumber() == null) {
            return List.of();
        }

        boolean duplicate;
        if (invoice.getSupplierVatId() != null) {
            duplicate = invoices.existsOtherInvoice(
                    invoice.getTenantId(), invoice.getSupplierVatId(), invoice.getInvoiceNumber(), invoice.getId());
        } else if (invoice.getSupplierName() != null) {
            duplicate = invoices.existsOtherInvoiceBySupplierName(
                    invoice.getTenantId(), invoice.getSupplierName(), invoice.getInvoiceNumber(), invoice.getId());
        } else {
            return List.of();
        }

        if (duplicate) {
            return List.of(Finding.warning("DT-POSSIBLE-DUPLICATE",
                    "Vom Lieferanten liegt bereits eine Rechnung mit der Nummer %s vor. "
                            .formatted(invoice.getInvoiceNumber())
                            + "Möglicherweise wurde sie doppelt eingereicht."));
        }
        return List.of();
    }
}