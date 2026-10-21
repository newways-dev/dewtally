package com.kate.dewtally.invoice;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure unit tests: no Spring, no database. They run in milliseconds.
 */
class InvoiceTest {

    @Test
    void newInvoiceStartsAsReceived() {
        assertThat(newInvoice().getStatus()).isEqualTo(InvoiceStatus.RECEIVED);
    }

    @Test
    void invoiceWithoutIssuesBecomesValid() {
        Invoice invoice = newInvoice();

        invoice.applyValidationResult();

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.VALID);
    }

    @Test
    void warningSendsInvoiceToReview() {
        Invoice invoice = newInvoice();
        invoice.addIssue(Severity.WARNING, "DT-POSSIBLE-DUPLICATE", "Same supplier and invoice number already exist");

        invoice.applyValidationResult();

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.NEEDS_REVIEW);
    }

    @Test
    void errorMakesInvoiceInvalidEvenIfThereAreWarnings() {
        Invoice invoice = newInvoice();
        invoice.addIssue(Severity.WARNING, "DT-POSSIBLE-DUPLICATE", "Same supplier and invoice number already exist");
        invoice.addIssue(Severity.ERROR, "BR-CO-15", "Gross amount does not equal net amount plus VAT");

        invoice.applyValidationResult();

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.INVALID);
    }

    @Test
    void infoDoesNotAffectStatus() {
        Invoice invoice = newInvoice();
        invoice.addIssue(Severity.INFO, "DT-INFO", "Just for information");

        invoice.applyValidationResult();

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.VALID);
    }

    @Test
    void validationResultCanBeAppliedOnlyOnce() {
        Invoice invoice = newInvoice();
        invoice.applyValidationResult();

        assertThatThrownBy(invoice::applyValidationResult)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("VALID");
    }

    private static Invoice newInvoice() {
        return new Invoice(UUID.randomUUID(), UUID.randomUUID(), ExtractionMethod.STRUCTURED, TestInvoices.sampleData());
    }
}