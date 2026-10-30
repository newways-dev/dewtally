package com.kate.dewtally.validation;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Money comparisons in rules: amounts are compared after rounding to cents,
 * so 1428.0 and 1428.00 count as equal.
 */
final class Amounts {

    private Amounts() {
    }

    static boolean equalInCents(BigDecimal a, BigDecimal b) {
        return a.setScale(2, RoundingMode.HALF_UP).compareTo(b.setScale(2, RoundingMode.HALF_UP)) == 0;
    }
}