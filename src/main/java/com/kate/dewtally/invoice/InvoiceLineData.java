package com.kate.dewtally.invoice;

import java.math.BigDecimal;

/**
 * Values of one invoice line as entered by a person. The line number comes from the position in the list.
 */
public record InvoiceLineData(
        String description,
        BigDecimal quantity,
        String unitCode,
        BigDecimal unitPrice,
        BigDecimal netAmount,
        BigDecimal taxRate
) {

    InvoiceLine toLine(int lineNo) {
        return new InvoiceLine(lineNo, description, quantity, unitCode, unitPrice, netAmount, taxRate);
    }

    // Short text for the audit trail when a whole line is added or removed.
    String summary() {
        return description + " | " + quantity + " " + unitCode + " x " + unitPrice + " = " + netAmount
                + " (" + taxRate + " %)";
    }
}