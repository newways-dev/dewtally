package com.kate.dewtally.validation;

import com.kate.dewtally.document.SourceFormat;
import com.kate.dewtally.invoice.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class VatBreakdownRuleTest {

    private final VatBreakdownRule rule = new VatBreakdownRule();

    private static BigDecimal amount(String value) {
        return new BigDecimal(value);
    }

    private static InvoiceLine line(int lineNo, String category, String rate, String net) {
        return new InvoiceLine(lineNo, "Item " + lineNo, BigDecimal.ONE, "C62", amount(net), amount(net),
                amount(rate), category);
    }

    private static VatSubtotal row(String category, String rate, String base, String tax, String reason) {
        return new VatSubtotal(category, amount(rate), amount(base), amount(tax), reason);
    }

    private static Invoice invoice(String supplierVatId, String net, String tax, List<VatSubtotal> breakdown,
                                   InvoiceLine... lines) {
        InvoiceData data = new InvoiceData("RE-1", LocalDate.of(2027, 3, 1), null, "EUR", "Muster GmbH",
                supplierVatId, null, null, amount(net), amount(tax), amount(net).add(amount(tax)));
        Invoice invoice = new Invoice(UUID.randomUUID(), UUID.randomUUID(), ExtractionMethod.STRUCTURED, data);
        Arrays.stream(lines).forEach(invoice::addLine);
        invoice.recordVatBreakdown(breakdown);
        return invoice;
    }

    private List<Finding> check(Invoice invoice) {
        return rule.check(new ValidationContext(invoice, SourceFormat.UBL, null));
    }

    @Test
    void consistentInvoiceWithTwoGermanRatesHasNoFindings() {
        Invoice invoice = invoice("DE123456789", "1200.00", "204.00",
                List.of(row("S", "19", "1000.00", "190.00", null), row("S", "7", "200.00", "14.00", null)),
                line(1, "S", "19", "1000.00"), line(2, "S", "7", "200.00"));

        assertThat(check(invoice)).isEmpty();
    }

    @Test
    void wrongTaxInOneRowIsAnError() {
        // 1000.00 × 19 % is 190.00, not 180.00; the total of 194.00 matches the rows, so only BR-CO-17 fires
        Invoice invoice = invoice("DE123456789", "1200.00", "194.00",
                List.of(row("S", "19", "1000.00", "180.00", null), row("S", "7", "200.00", "14.00", null)),
                line(1, "S", "19", "1000.00"), line(2, "S", "7", "200.00"));

        assertThat(check(invoice))
                .extracting(Finding::ruleId, Finding::severity)
                .containsExactly(tuple("BR-CO-17", Severity.ERROR));
    }

    @Test
    void rowsThatDoNotAddUpToTheVatTotalAreAnError() {
        Invoice invoice = invoice("DE123456789", "1200.00", "210.00",
                List.of(row("S", "19", "1000.00", "190.00", null), row("S", "7", "200.00", "14.00", null)),
                line(1, "S", "19", "1000.00"), line(2, "S", "7", "200.00"));

        assertThat(check(invoice))
                .extracting(Finding::ruleId, Finding::severity)
                .containsExactly(tuple("BR-CO-14", Severity.ERROR));
    }

    @Test
    void correctReverseChargeOnlyTellsAccountingWhatToDo() {
        Invoice invoice = invoice("DE123456789", "5000.00", "0.00",
                List.of(row("AE", "0", "5000.00", "0.00", "Steuerschuldnerschaft des Leistungsempfängers")),
                line(1, "AE", "0", "5000.00"));

        assertThat(check(invoice))
                .extracting(Finding::ruleId, Finding::severity)
                .containsExactly(tuple("DT-REVERSE-CHARGE", Severity.INFO));
    }

    @Test
    void reverseChargeWithVatAndWithoutReasonIsFlagged() {
        Invoice invoice = invoice("DE123456789", "5000.00", "950.00",
                List.of(row("AE", "19", "5000.00", "950.00", null)),
                line(1, "AE", "19", "5000.00"));

        assertThat(check(invoice))
                .extracting(Finding::ruleId, Finding::severity)
                .containsExactlyInAnyOrder(
                        tuple("DT-VAT-NOT-ZERO", Severity.ERROR),
                        tuple("DT-VAT-REASON-MISSING", Severity.WARNING),
                        tuple("DT-REVERSE-CHARGE", Severity.INFO));
    }

    @Test
    void unusualRateOfAGermanSupplierIsAWarning() {
        // 16 % was only valid in the second half of 2020
        Invoice invoice = invoice("DE123456789", "1000.00", "160.00",
                List.of(row("S", "16", "1000.00", "160.00", null)),
                line(1, "S", "16", "1000.00"));

        assertThat(check(invoice))
                .extracting(Finding::ruleId, Finding::severity)
                .containsExactly(tuple("DT-VAT-RATE-UNUSUAL", Severity.WARNING));
    }

    @Test
    void foreignRatesAreNotJudgedByGermanRules() {
        Invoice invoice = invoice("ATU12345678", "1000.00", "200.00",
                List.of(row("S", "20", "1000.00", "200.00", null)),
                line(1, "S", "20", "1000.00"));

        assertThat(check(invoice)).isEmpty();
    }

    @Test
    void linesOfACategoryThatDoNotMatchItsTaxableAmountAreAWarning() {
        // The rows add up, but the 7 % line is only 150.00 instead of 200.00
        Invoice invoice = invoice("DE123456789", "1200.00", "204.00",
                List.of(row("S", "19", "1000.00", "190.00", null), row("S", "7", "200.00", "14.00", null)),
                line(1, "S", "19", "1050.00"), line(2, "S", "7", "150.00"));

        assertThat(check(invoice))
                .extracting(Finding::ruleId)
                .containsExactly("DT-VAT-LINES-MISMATCH");
    }

    @Test
    void invoiceWithoutBreakdownIsSkipped() {
        Invoice invoice = invoice("DE123456789", "1000.00", "190.00", List.of(), line(1, null, "19", "1000.00"));

        assertThat(check(invoice)).isEmpty();
    }
}