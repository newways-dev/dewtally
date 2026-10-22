package com.kate.dewtally.validation;

import com.kate.dewtally.invoice.Invoice;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * EN 16931 BR-CO-15: total with VAT = total without VAT + VAT total.
 * An invoice whose own arithmetic is wrong must not be paid.
 */
@Component
public class TotalsRule implements InvoiceRule {

    @Override
    public List<Finding> check(ValidationContext context) {
        Invoice invoice = context.invoice();
        BigDecimal net = invoice.getNetAmount();
        BigDecimal gross = invoice.getGrossAmount();
        if (net == null || gross == null) {
            return List.of();
        }

        BigDecimal tax = invoice.getTaxAmount() == null ? BigDecimal.ZERO : invoice.getTaxAmount();
        BigDecimal expected = net.add(tax);

        if (!Amounts.equalInCents(expected, gross)) {
            return List.of(Finding.error("BR-CO-15",
                    "Gesamtbetrag mit Umsatzsteuer (%s) entspricht nicht Nettobetrag plus Umsatzsteuer (%s)."
                            .formatted(gross.toPlainString(), expected.toPlainString())));
        }
        return List.of();
    }
}