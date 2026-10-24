package com.kate.dewtally.invoice;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Header data of an invoice as extracted from a document.
 * Every field may be null: extraction can succeed only partially.
 *
 * @param allowanceTotal sum of discounts on the whole invoice (BT-107), null if there are none
 * @param chargeTotal    sum of surcharges on the whole invoice (BT-108), null if there are none
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
        BigDecimal grossAmount,
        BigDecimal allowanceTotal,
        BigDecimal chargeTotal
) {

    @JsonCreator
    public InvoiceData {
    }

    /**
     * Invoice without document-level allowances and charges, which is the common case.
     */
    public InvoiceData(String invoiceNumber, LocalDate issueDate, LocalDate dueDate, String currency,
                       String supplierName, String supplierVatId, String buyerReference, String iban,
                       BigDecimal netAmount, BigDecimal taxAmount, BigDecimal grossAmount) {
        this(invoiceNumber, issueDate, dueDate, currency, supplierName, supplierVatId, buyerReference, iban,
                netAmount, taxAmount, grossAmount, null, null);
    }

    public static InvoiceData empty() {
        return new InvoiceData(null, null, null, null, null, null, null, null, null, null, null);
    }
}