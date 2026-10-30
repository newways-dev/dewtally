package com.kate.dewtally.parsing;

import org.w3c.dom.Element;

import java.math.BigDecimal;

/**
 * Document-level allowances (discounts) and charges (surcharges), shared by the CII and UBL parsers.
 */
final class AllowanceCharges {

    private AllowanceCharges() {
    }

    /**
     * The total stated in the invoice (BT-107 / BT-108) if present. Otherwise the sum of the
     * single document-level entries of the requested kind. Null if the invoice has none.
     *
     * @param charges true for charges (surcharges), false for allowances (discounts)
     */
    static BigDecimal total(XPathReader xml, BigDecimal stated, Iterable<Element> entries,
                            String indicatorPath, String amountPath, boolean charges) {
        if (stated != null) {
            return stated;
        }
        BigDecimal sum = null;
        for (Element entry : entries) {
            String indicator = xml.text(entry, indicatorPath);
            if (indicator == null || Boolean.parseBoolean(indicator.strip()) != charges) {
                continue;
            }
            BigDecimal amount = xml.decimal(entry, amountPath);
            if (amount != null) {
                sum = sum == null ? amount : sum.add(amount);
            }
        }
        return sum;
    }
}