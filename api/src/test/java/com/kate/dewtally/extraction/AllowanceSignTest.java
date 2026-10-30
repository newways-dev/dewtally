package com.kate.dewtally.extraction;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AllowanceSignTest {

    @Test
    void discountIsPositiveOnANormalInvoiceEvenIfPrintedWithMinus() {
        assertThat(ExtractedInvoice.withSignOf(new BigDecimal("2386.55"), new BigDecimal("-268.28")))
                .isEqualByComparingTo("268.28");
    }

    @Test
    void discountIsNegativeOnACorrectionInvoiceWithNegativeAmounts() {
        assertThat(ExtractedInvoice.withSignOf(new BigDecimal("-12.50"), new BigDecimal("0.23")))
                .isEqualByComparingTo("-0.23");
    }

    @Test
    void noDiscountStaysEmpty() {
        assertThat(ExtractedInvoice.withSignOf(new BigDecimal("100.00"), null)).isNull();
    }
}