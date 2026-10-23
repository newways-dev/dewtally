package com.kate.dewtally.invoice;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Header data of an invoice as extracted from a document.
 * Every field may be null: extraction can succeed only partially.
 */
public record InvoiceData(
        String invoiceNumber,
        LocalDate issueDate,
        LocalDate dueDate,
        String currency,
        String supplierName,
        String supplierVatId,
        String buyerReference,
        String iban,
        BigDecimal netAmount,
        BigDecimal taxAmount,
        BigDecimal grossAmount
) {
    
    public static InvoiceData empty() {
        return new InvoiceData(null, null, null, null, null, null, null, null, null, null, null);
    }
}