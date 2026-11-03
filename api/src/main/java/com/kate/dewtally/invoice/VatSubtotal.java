package com.kate.dewtally.invoice;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;

/**
 * One row of an invoice's VAT breakdown (EN 16931 BG-23): the net amount and VAT of one category and rate.
 * An invoice with goods at 19 % and books at 7 % has two rows.
 *
 * @param category        BT-118: S standard rate, AE reverse charge (§ 13b UStG), E exempt (e.g. § 19 UStG),
 *                        K intra-EU supply, Z zero rated, G export, O outside the scope of VAT
 * @param rate            BT-119: rate in percent, 0 for the categories without VAT
 * @param taxableAmount   BT-116: net amount of this category
 * @param taxAmount       BT-117: VAT of this category
 * @param exemptionReason BT-120 or the code of BT-121: why no VAT is charged; null for standard rates
 */
@Embeddable
public record VatSubtotal(
        @Column(name = "category", length = 5) String category,
        @Column(name = "rate", precision = 5, scale = 2) BigDecimal rate,
        @Column(name = "taxable_amount", precision = 15, scale = 2) BigDecimal taxableAmount,
        @Column(name = "tax_amount", precision = 15, scale = 2) BigDecimal taxAmount,
        @Column(name = "exemption_reason", length = 1000) String exemptionReason
) {
}