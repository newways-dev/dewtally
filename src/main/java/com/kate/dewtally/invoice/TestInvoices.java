package com.kate.dewtally.invoice;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Shared sample data for invoice tests.
 * Amounts are consistent: 1200.00 net + 228.00 VAT (19 %) = 1428.00 gross.
 */
final class TestInvoices {

    private TestInvoices() {
    }

    static InvoiceData sampleData() {
        return new InvoiceData(
                "RE-2041",
                LocalDate.of(2027, 3, 14),
                LocalDate.of(2027, 4, 13),
                "EUR",
                "Muster GmbH",
                "DE123456789",
                "04011000-12345-34",
                "DE89370400440532013000",
                new BigDecimal("1200.00"),
                new BigDecimal("228.00"),
                new BigDecimal("1428.00"));
    }

    static InvoiceLine consultingLine(int lineNo) {
        return new InvoiceLine(lineNo, "Consulting", new BigDecimal("10"), "HUR",
                new BigDecimal("100.00"), new BigDecimal("1000.00"), new BigDecimal("19.00"));
    }

    static InvoiceLine travelLine(int lineNo) {
        return new InvoiceLine(lineNo, "Travel expenses", new BigDecimal("1"), "C62",
                new BigDecimal("200.00"), new BigDecimal("200.00"), new BigDecimal("19.00"));
    }
}