package com.kate.dewtally.validation;

import com.kate.dewtally.invoice.Invoice;
import com.kate.dewtally.invoice.InvoiceLine;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class LineSumRule implements InvoiceRule {

    @Override
    public List<Finding> check(ValidationContext context) {
        Invoice invoice = context.invoice();
        if (invoice.getNetAmount() == null || invoice.getLines().isEmpty()) {
            return List.of();
        }

        BigDecimal sum = BigDecimal.ZERO;
        for (InvoiceLine line : invoice.getLines()) {
            if (line.getNetAmount() == null) {
                return List.of();
            }
            sum = sum.add(line.getNetAmount());
        }

        if (!Amounts.equalInCents(sum, invoice.getNetAmount())) {
            return List.of(Finding.warning("DT-LINE-SUM-MISMATCH",
                    "Summe der Positionen (%s) weicht vom Nettobetrag (%s) ab. "
                            .formatted(sum.toPlainString(), invoice.getNetAmount().toPlainString())
                            + "Möglicherweise gibt es Zu- oder Abschläge auf Belegebene."));
        }
        return List.of();
    }
}