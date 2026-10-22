package com.kate.dewtally.validation;

import com.kate.dewtally.invoice.Invoice;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DueDateRule implements InvoiceRule {

    @Override
    public List<Finding> check(ValidationContext context) {
        Invoice invoice = context.invoice();
        if (invoice.getIssueDate() == null || invoice.getDueDate() == null) {
            return List.of();
        }
        if (invoice.getDueDate().isBefore(invoice.getIssueDate())) {
            return List.of(Finding.warning("DT-DUE-BEFORE-ISSUE",
                    "Fälligkeitsdatum (%s) liegt vor dem Rechnungsdatum (%s)."
                            .formatted(invoice.getDueDate(), invoice.getIssueDate())));
        }
        return List.of();
    }
}