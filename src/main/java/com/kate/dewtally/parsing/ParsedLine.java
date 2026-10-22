package com.kate.dewtally.parsing;

import java.math.BigDecimal;

/**
 * One invoice line as read from the file. Any field may be null if the file does not contain it.
 */
public record ParsedLine(
        int lineNo,
        String description,
        BigDecimal quantity,
        String unitCode,
        BigDecimal unitPrice,
        BigDecimal netAmount,
        BigDecimal taxRate
) {
}