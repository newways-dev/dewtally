package com.kate.dewtally.invoice;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceVatBreakdownTest {

    // Muster GmbH: 1200.00 net + 228.00 VAT (19 %), lines 1000.00 + 200.00
    private static Invoice validInvoiceWithBreakdown() {
        Invoice invoice = new Invoice(UUID.randomUUID(), UUID.randomUUID(), ExtractionMethod.STRUCTURED,
                TestInvoices.sampleData());
        invoice.addLine(TestInvoices.consultingLine(1));
        invoice.addLine(TestInvoices.travelLine(2));
        invoice.recordVatBreakdown(List.of(new VatSubtotal(
                "S", new BigDecimal("19"), new BigDecimal("1200.00"), new BigDecimal("228.00"), null)));
        invoice.applyValidationResult();
        return invoice;
    }

    private static List<InvoiceLineData> unchangedLines(Invoice invoice) {
        return invoice.getLines().stream()
                .map(line -> new InvoiceLineData(line.getDescription(), line.getQuantity(), line.getUnitCode(),
                        line.getUnitPrice(), line.getNetAmount(), line.getTaxRate()))
                .toList();
    }

    private static InvoiceData withTax(InvoiceData data, String tax, String gross) {
        return new InvoiceData(data.invoiceNumber(), data.issueDate(), data.dueDate(), data.currency(),
                data.supplierName(), data.supplierVatId(), data.buyerReference(), data.iban(), data.netAmount(),
                new BigDecimal(tax), new BigDecimal(gross), data.allowanceTotal(), data.chargeTotal());
    }

    private static InvoiceData withBuyerReference(InvoiceData data, String buyerReference) {
        return new InvoiceData(data.invoiceNumber(), data.issueDate(), data.dueDate(), data.currency(),
                data.supplierName(), data.supplierVatId(), buyerReference, data.iban(), data.netAmount(),
                data.taxAmount(), data.grossAmount(), data.allowanceTotal(), data.chargeTotal());
    }

    @Test
    void correctingAnAmountRemovesTheBreakdownAndKeepsItInTheHistory() {
        Invoice invoice = validInvoiceWithBreakdown();

        List<FieldChange> changes = invoice.correct(
                withTax(TestInvoices.sampleData(), "230.00", "1430.00"), unchangedLines(invoice));

        assertThat(invoice.getVatBreakdown()).isEmpty();
        assertThat(changes).anySatisfy(change -> {
            assertThat(change.field()).isEqualTo("vatBreakdown");
            assertThat(change.oldValue()).isEqualTo("S 19: 1200.00 / 228.00");
            assertThat(change.newValue()).isNull();
        });
    }

    @Test
    void correctingOtherDataKeepsTheBreakdown() {
        Invoice invoice = validInvoiceWithBreakdown();

        List<FieldChange> changes = invoice.correct(
                withBuyerReference(TestInvoices.sampleData(), "04011000-99999-99"), unchangedLines(invoice));

        assertThat(invoice.getVatBreakdown()).hasSize(1);
        assertThat(changes).extracting(FieldChange::field).doesNotContain("vatBreakdown");
    }
}