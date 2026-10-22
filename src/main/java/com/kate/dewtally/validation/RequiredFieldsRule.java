package com.kate.dewtally.validation;

import com.kate.dewtally.invoice.Invoice;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Fields without which an invoice can neither be paid nor booked. Rule ids and BT numbers come from EN 16931.
 */
@Component
public class RequiredFieldsRule implements InvoiceRule {

    private static void require(List<Finding> findings, Object value, String ruleId, String message) {
        boolean missing = value == null || (value instanceof String text && text.isBlank());
        if (missing) {
            findings.add(Finding.error(ruleId, message));
        }
    }

    @Override
    public List<Finding> check(ValidationContext context) {
        Invoice invoice = context.invoice();
        List<Finding> findings = new ArrayList<>();

        require(findings, invoice.getInvoiceNumber(), "BR-02", "Rechnungsnummer (BT-1) fehlt.");
        require(findings, invoice.getIssueDate(), "BR-03", "Rechnungsdatum (BT-2) fehlt.");
        require(findings, invoice.getCurrency(), "BR-05", "Währung (BT-5) fehlt.");
        require(findings, invoice.getSupplierName(), "BR-06", "Name des Verkäufers (BT-27) fehlt.");
        require(findings, invoice.getNetAmount(), "BR-13", "Gesamtbetrag ohne Umsatzsteuer (BT-109) fehlt.");
        require(findings, invoice.getGrossAmount(), "BR-14", "Gesamtbetrag mit Umsatzsteuer (BT-112) fehlt.");

        if (invoice.getLines().isEmpty()) {
            findings.add(Finding.error("BR-16", "Die Rechnung enthält keine Rechnungsposition."));
        }
        return findings;
    }
}