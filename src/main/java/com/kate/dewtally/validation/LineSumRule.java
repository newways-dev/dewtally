package com.kate.dewtally.validation;

import com.kate.dewtally.invoice.Invoice;
import com.kate.dewtally.invoice.InvoiceLine;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * EN 16931 BR-CO-13: net total = sum of line net amounts − document allowances + document charges.
 * A warning, not an error: for PDFs the lines were read by a model and may be incomplete.
 */
@Component
public class LineSumRule implements InvoiceRule {

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

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

        BigDecimal allowances = orZero(invoice.getAllowanceTotal());
        BigDecimal charges = orZero(invoice.getChargeTotal());
        BigDecimal expected = sum.subtract(allowances).add(charges);

        if (!Amounts.equalInCents(expected, invoice.getNetAmount())) {
            return List.of(Finding.warning("DT-LINE-SUM-MISMATCH",
                    "Summe der Positionen (%s) − Abschläge (%s) + Zuschläge (%s) = %s weicht vom Nettobetrag (%s) ab."
                            .formatted(sum.toPlainString(), allowances.toPlainString(), charges.toPlainString(),
                                    expected.toPlainString(), invoice.getNetAmount().toPlainString())));
        }
        return List.of();
    }
}