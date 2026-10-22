package com.kate.dewtally.validation;

import com.kate.dewtally.document.SourceFormat;
import com.kate.dewtally.invoice.ExtractionMethod;
import com.kate.dewtally.invoice.Invoice;
import com.kate.dewtally.invoice.InvoiceData;
import com.kate.dewtally.invoice.InvoiceRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DuplicateInvoiceRuleTest {

    private final InvoiceRepository invoices = mock(InvoiceRepository.class);
    private final DuplicateInvoiceRule rule = new DuplicateInvoiceRule(invoices);

    private static ValidationContext context(Invoice invoice) {
        return new ValidationContext(invoice, SourceFormat.UBL, null);
    }

    private static Invoice invoice(String vatId, String supplierName, String invoiceNumber) {
        InvoiceData data = new InvoiceData(invoiceNumber, LocalDate.of(2027, 3, 14), null, "EUR",
                supplierName, vatId, null, null,
                new BigDecimal("1200.00"), new BigDecimal("228.00"), new BigDecimal("1428.00"));
        return new Invoice(UUID.randomUUID(), UUID.randomUUID(), ExtractionMethod.STRUCTURED, data);
    }

    @Test
    void warnsWhenTheSupplierAlreadySentThisInvoiceNumber() {
        when(invoices.existsOtherInvoice(any(), eq("DE123456789"), eq("RE-2041"), any())).thenReturn(true);

        assertThat(rule.check(context(invoice("DE123456789", "Muster GmbH", "RE-2041"))))
                .extracting(Finding::ruleId)
                .containsExactly("DT-POSSIBLE-DUPLICATE");
    }

    @Test
    void acceptsANewInvoiceNumber() {
        when(invoices.existsOtherInvoice(any(), any(), any(), any())).thenReturn(false);

        assertThat(rule.check(context(invoice("DE123456789", "Muster GmbH", "RE-2042")))).isEmpty();
    }

    @Test
    void usesTheSupplierNameWhenThereIsNoVatId() {
        when(invoices.existsOtherInvoiceBySupplierName(any(), eq("Muster GmbH"), eq("RE-2041"), any()))
                .thenReturn(true);

        assertThat(rule.check(context(invoice(null, "Muster GmbH", "RE-2041"))))
                .extracting(Finding::ruleId)
                .containsExactly("DT-POSSIBLE-DUPLICATE");
    }

    @Test
    void skipsInvoicesWithoutANumber() {
        assertThat(rule.check(context(invoice("DE123456789", "Muster GmbH", null)))).isEmpty();
        verifyNoInteractions(invoices);
    }
}