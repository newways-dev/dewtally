package com.kate.dewtally.extraction;

import com.kate.dewtally.invoice.VatSubtotal;
import com.kate.dewtally.parsing.ParsedInvoice;
import com.kate.dewtally.parsing.ParsedLine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractedInvoiceVatTest {

    private static ExtractedInvoice answer(List<ExtractedInvoice.Line> lines, List<ExtractedInvoice.VatRow> vat) {
        return new ExtractedInvoice("BAU-77", "2027-03-01", "", "EUR", "Trockenbau Muster GmbH", "DE123456789",
                "", "", "5000.00", "0.00", "5000.00", "", "", lines, vat, 0.9);
    }

    @Test
    void reverseChargeBecomesARowWithItsReasonAndTheLinesGetTheCategory() {
        ParsedInvoice parsed = answer(
                List.of(new ExtractedInvoice.Line("Trockenbauarbeiten", "1", "", "5000.00", "5000.00", "0", "ae")),
                List.of(new ExtractedInvoice.VatRow("AE", "0", "5000.00", "0.00",
                        "Steuerschuldnerschaft des Leistungsempfängers"))).toParsedInvoice();

        assertThat(parsed.vatBreakdown()).containsExactly(new VatSubtotal("AE", new BigDecimal("0"),
                new BigDecimal("5000.00"), new BigDecimal("0.00"), "Steuerschuldnerschaft des Leistungsempfängers"));
        assertThat(parsed.lines()).extracting(ParsedLine::taxCategory).containsExactly("AE");
    }

    @Test
    void unknownCategoriesAndEmptyRowsAreDropped() {
        ParsedInvoice parsed = answer(
                List.of(new ExtractedInvoice.Line("Beratung", "1", "", "100.00", "100.00", "19", "Reverse Charge")),
                List.of(new ExtractedInvoice.VatRow("19%", "19", "100.00", "19.00", ""),
                        new ExtractedInvoice.VatRow("", "", "", "", ""))).toParsedInvoice();

        // A category the model made up is unknown (null), the amounts still count; the empty row is noise
        assertThat(parsed.vatBreakdown()).containsExactly(new VatSubtotal(null, new BigDecimal("19"),
                new BigDecimal("100.00"), new BigDecimal("19.00"), null));
        assertThat(parsed.lines()).extracting(ParsedLine::taxCategory).containsExactly((String) null);
    }

    @Test
    void anAnswerWithoutBreakdownStillWorks() {
        ParsedInvoice parsed = new ExtractedInvoice("RE-1", "2027-03-01", "", "EUR", "Muster GmbH", "DE123456789",
                "", "", "100.00", "19.00", "119.00", List.of(), 0.9).toParsedInvoice();

        assertThat(parsed.vatBreakdown()).isEmpty();
    }
}