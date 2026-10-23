package com.kate.dewtally.extraction;

import com.kate.dewtally.invoice.InvoiceData;
import com.kate.dewtally.parsing.ParsedInvoice;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractedInvoiceTest {

    @Test
    void convertsAWellFormedAnswer() {
        ExtractedInvoice answer = new ExtractedInvoice(
                "RE-2041", "2027-03-14", "2027-04-13", "eur",
                "Muster GmbH", "DE 123 456 789", "04011000-12345-34", "DE89 3704 0044 0532 0130 00",
                "1200.00", "228.00", "1428.00",
                List.of(new ExtractedInvoice.Line("Consulting", "10", "Std.", "100.00", "1000.00", "19")),
                0.95);

        ParsedInvoice parsed = answer.toParsedInvoice();
        InvoiceData data = parsed.data();

        assertThat(data.invoiceNumber()).isEqualTo("RE-2041");
        assertThat(data.issueDate()).isEqualTo(LocalDate.of(2027, 3, 14));
        assertThat(data.currency()).isEqualTo("EUR");
        assertThat(data.supplierVatId()).isEqualTo("DE123456789");
        assertThat(data.iban()).isEqualTo("DE89370400440532013000");
        assertThat(data.grossAmount()).isEqualByComparingTo("1428.00");
        assertThat(parsed.lines()).hasSize(1);
        assertThat(parsed.lines().getFirst().lineNo()).isEqualTo(1);
        assertThat(parsed.lines().getFirst().taxRate()).isEqualByComparingTo("19");
    }

    @Test
    void turnsMissingValuesIntoNull() {
        ExtractedInvoice answer = new ExtractedInvoice(
                "", "", " ", "", "", "", "", "", "", "", "", List.of(), 0.1);

        InvoiceData data = answer.toParsedInvoice().data();

        assertThat(data.invoiceNumber()).isNull();
        assertThat(data.issueDate()).isNull();
        assertThat(data.currency()).isNull();
        assertThat(data.grossAmount()).isNull();
    }

    @Test
    void refusesToGuessAmbiguousOrBrokenValues() {
        // German number format and German date: not the agreed format → null, never a guess.
        assertThat(ExtractedInvoice.amount("1.428,00")).isNull();
        assertThat(ExtractedInvoice.amount("about 1400")).isNull();
        assertThat(ExtractedInvoice.date("14.03.2027")).isNull();
        assertThat(ExtractedInvoice.currency("Euro")).isNull();
    }

    @Test
    void acceptsHarmlessDecorationsAroundAmounts() {
        assertThat(ExtractedInvoice.amount("1428.00 EUR")).isEqualByComparingTo(new BigDecimal("1428.00"));
        assertThat(ExtractedInvoice.amount("€ 228.00")).isEqualByComparingTo(new BigDecimal("228.00"));
        assertThat(ExtractedInvoice.amount("19 %")).isEqualByComparingTo(new BigDecimal("19"));
        assertThat(ExtractedInvoice.amount("-50.00")).isEqualByComparingTo(new BigDecimal("-50.00"));
    }

    @Test
    void handlesMissingLineList() {
        ExtractedInvoice answer = new ExtractedInvoice(
                "RE-1", "", "", "", "", "", "", "", "", "", "", null, 0.5);

        assertThat(answer.toParsedInvoice().lines()).isEmpty();
    }
}