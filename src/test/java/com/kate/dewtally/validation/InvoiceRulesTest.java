package com.kate.dewtally.validation;

import com.kate.dewtally.document.SourceFormat;
import com.kate.dewtally.invoice.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for the rules that need no database.
 * The sample invoice is correct: 1000.00 + 200.00 = 1200.00 net, + 228.00 VAT = 1428.00 gross.
 */
class InvoiceRulesTest {

    private static final String XRECHNUNG =
            "urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0";

    private final List<InvoiceRule> rules = List.of(
            new RequiredFieldsRule(), new TotalsRule(), new LineSumRule(), new DueDateRule(), new ProfileRule());

    private static ValidationContext context(Invoice invoice) {
        return new ValidationContext(invoice, SourceFormat.UBL, XRECHNUNG);
    }

    private static Invoice sampleInvoice() {
        return invoice(sampleData("1200.00", "228.00", "1428.00"));
    }

    private static Invoice invoice(InvoiceData data) {
        Invoice invoice = new Invoice(UUID.randomUUID(), UUID.randomUUID(), ExtractionMethod.STRUCTURED, data);
        invoice.addLine(new InvoiceLine(1, "Consulting", new BigDecimal("10"), "HUR",
                new BigDecimal("100.00"), new BigDecimal("1000.00"), new BigDecimal("19")));
        invoice.addLine(new InvoiceLine(2, "Travel expenses", new BigDecimal("1"), "C62",
                new BigDecimal("200.00"), new BigDecimal("200.00"), new BigDecimal("19")));
        return invoice;
    }

    private static InvoiceData sampleData(String net, String tax, String gross) {
        return new InvoiceData("RE-2041", LocalDate.of(2027, 3, 14), LocalDate.of(2027, 4, 13),
                "EUR", "Muster GmbH", "DE123456789", "04011000-12345-34", "DE89370400440532013000",
                new BigDecimal(net), new BigDecimal(tax), new BigDecimal(gross));
    }

    @Test
    void aCorrectInvoicePassesAllRules() {
        assertThat(findings(sampleInvoice(), SourceFormat.UBL, XRECHNUNG)).isEmpty();
    }

    @Test
    void reportsEveryMissingRequiredField() {
        InvoiceData empty = new InvoiceData(null, null, null, null, null, null, null, null, null, null, null);
        Invoice invoice = new Invoice(UUID.randomUUID(), UUID.randomUUID(), ExtractionMethod.STRUCTURED, empty);

        List<Finding> findings = new RequiredFieldsRule().check(context(invoice));

        assertThat(findings)
                .extracting(Finding::ruleId)
                .containsExactlyInAnyOrder("BR-02", "BR-03", "BR-05", "BR-06", "BR-13", "BR-14", "BR-16");
        assertThat(findings).allMatch(finding -> finding.severity() == Severity.ERROR);
    }

    @Test
    void wrongGrossAmountIsAnError() {
        Invoice invoice = invoice(sampleData("1200.00", "228.00", "1500.00"));

        assertThat(new TotalsRule().check(context(invoice)))
                .singleElement()
                .satisfies(finding -> {
                    assertThat(finding.ruleId()).isEqualTo("BR-CO-15");
                    assertThat(finding.severity()).isEqualTo(Severity.ERROR);
                });
    }

    @Test
    void amountsWithDifferentDecimalPlacesAreEqual() {
        Invoice invoice = invoice(sampleData("1200.0", "228", "1428.00"));

        assertThat(new TotalsRule().check(context(invoice))).isEmpty();
    }

    @Test
    void lineSumDifferenceIsOnlyAWarning() {
        // Net total 1300 does not match the lines (1200), but the totals themselves add up.
        Invoice invoice = invoice(sampleData("1300.00", "247.00", "1547.00"));

        assertThat(new TotalsRule().check(context(invoice))).isEmpty();
        assertThat(new LineSumRule().check(context(invoice)))
                .singleElement()
                .satisfies(finding -> {
                    assertThat(finding.ruleId()).isEqualTo("DT-LINE-SUM-MISMATCH");
                    assertThat(finding.severity()).isEqualTo(Severity.WARNING);
                });
    }

    @Test
    void dueDateBeforeIssueDateIsAWarning() {
        InvoiceData data = new InvoiceData("RE-2041", LocalDate.of(2027, 3, 14), LocalDate.of(2027, 3, 1),
                "EUR", "Muster GmbH", "DE123456789", null, null,
                new BigDecimal("1200.00"), new BigDecimal("228.00"), new BigDecimal("1428.00"));

        assertThat(new DueDateRule().check(context(invoice(data))))
                .extracting(Finding::ruleId)
                .containsExactly("DT-DUE-BEFORE-ISSUE");
    }

    @Test
    void zugferdMinimumIsNotAnEInvoice() {
        List<Finding> findings = new ProfileRule()
                .check(new ValidationContext(sampleInvoice(), SourceFormat.ZUGFERD, "urn:factur-x.eu:1p0:minimum"));

        assertThat(findings).extracting(Finding::ruleId).containsExactly("DT-NOT-AN-E-INVOICE");
    }

    @Test
    void zugferdEn16931ProfileIsFine() {
        List<Finding> findings = new ProfileRule()
                .check(new ValidationContext(sampleInvoice(), SourceFormat.ZUGFERD, "urn:cen.eu:en16931:2017"));

        assertThat(findings).isEmpty();
    }

    private List<Finding> findings(Invoice invoice, SourceFormat format, String profile) {
        ValidationContext context = new ValidationContext(invoice, format, profile);
        return rules.stream().flatMap(rule -> rule.check(context).stream()).toList();
    }
}