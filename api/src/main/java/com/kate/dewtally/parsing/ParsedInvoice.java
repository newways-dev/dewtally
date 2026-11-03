package com.kate.dewtally.parsing;

import com.kate.dewtally.invoice.InvoiceData;
import com.kate.dewtally.invoice.VatSubtotal;

import java.util.List;

/**
 * Result of reading a structured e-invoice: header data, lines and the VAT breakdown.
 * Nothing is validated here; missing or odd values are judged in the validation step.
 */
public record ParsedInvoice(InvoiceData data, List<ParsedLine> lines, List<VatSubtotal> vatBreakdown) {

    public ParsedInvoice {
        vatBreakdown = vatBreakdown == null ? List.of() : List.copyOf(vatBreakdown);
    }

    /**
     * Invoice without a VAT breakdown (e.g. read by the language model).
     */
    public ParsedInvoice(InvoiceData data, List<ParsedLine> lines) {
        this(data, lines, List.of());
    }
}