package com.kate.dewtally.parsing;

import java.math.BigDecimal;

/**
 * One invoice line as read from the file. Any field may be null if the file does not contain it.
 *
 * @param taxCategory BT-151: VAT category of the line (S, AE, E, ...)
 */
public record ParsedLine(
        int lineNo,
        String description,
        BigDecimal quantity,
        String unitCode,
        BigDecimal unitPrice,
        BigDecimal netAmount,
        BigDecimal taxRate,
        String taxCategory
) {

    /**
     * A line without a VAT category.
     */
    public ParsedLine(int lineNo, String description, BigDecimal quantity, String unitCode,
                      BigDecimal unitPrice, BigDecimal netAmount, BigDecimal taxRate) {
        this(lineNo, description, quantity, unitCode, unitPrice, netAmount, taxRate, null);
    }
}