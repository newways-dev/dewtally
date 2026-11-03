package com.kate.dewtally.validation;

import com.kate.dewtally.invoice.Invoice;
import com.kate.dewtally.invoice.InvoiceLine;
import com.kate.dewtally.invoice.Severity;
import com.kate.dewtally.invoice.VatSubtotal;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Checks the VAT breakdown (EN 16931 BG-23): the arithmetic of each row, the totals, the categories
 * without VAT and the German rates. KoSIT checks the same for XRechnung files; this rule also covers
 * ZUGFeRD and invoices read by the model, and tells accounting about reverse charge.
 * Invoices without a breakdown are skipped.
 */
@Component
public class VatBreakdownRule implements InvoiceRule {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    // One cent difference per row comes from rounding per line vs. per category and is accepted
    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");
    private static final List<BigDecimal> GERMAN_RATES = List.of(new BigDecimal("19"), new BigDecimal("7"));
    // Categories that show no VAT on the invoice: rate and amount must be 0
    private static final Set<String> WITHOUT_VAT = Set.of("Z", "E", "AE", "K", "G", "O");
    // All of them except zero-rated goods (Z) must say why no VAT is charged
    private static final Set<String> NEED_REASON = Set.of("E", "AE", "K", "G", "O");
    private static final int MAX_MESSAGE_LENGTH = 2000;

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().scale() < 0
                ? value.setScale(0, RoundingMode.UNNECESSARY).toPlainString()
                : value.toPlainString();
    }

    private static String category(VatSubtotal row) {
        return row.category() == null ? "" : row.category().strip().toUpperCase(Locale.ROOT);
    }

    // "S 19 %", "AE 0 %": how a row is named in messages
    private static String label(VatSubtotal row) {
        String category = row.category() == null ? "?" : row.category();
        return row.rate() == null ? category : category + " " + plain(row.rate().stripTrailingZeros()) + " %";
    }

    private static boolean nonZero(BigDecimal value) {
        return value != null && value.signum() != 0;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    // Suppliers with a German VAT ID, or without one, are judged by German rates
    private static boolean germanSupplier(Invoice invoice) {
        String vatId = invoice.getSupplierVatId();
        return vatId == null || vatId.strip().toUpperCase(Locale.ROOT).startsWith("DE");
    }

    private static String key(String category, BigDecimal rate) {
        return category.strip().toUpperCase(Locale.ROOT) + "|" + rate.stripTrailingZeros().toPlainString();
    }

    @Override
    public List<Finding> check(ValidationContext context) {
        Invoice invoice = context.invoice();
        List<VatSubtotal> breakdown = invoice.getVatBreakdown();
        if (breakdown.isEmpty()) {
            return List.of();
        }

        Findings findings = new Findings();
        for (VatSubtotal row : breakdown) {
            checkRow(invoice, row, findings);
        }
        checkTaxTotal(invoice, breakdown, findings);
        checkBaseTotal(invoice, breakdown, findings);
        checkLines(invoice, breakdown, findings);
        return findings.toList();
    }

    private void checkRow(Invoice invoice, VatSubtotal row, Findings findings) {
        String category = category(row);
        String label = label(row);
        BigDecimal rate = row.rate();
        BigDecimal base = row.taxableAmount();
        BigDecimal tax = row.taxAmount();

        // BR-CO-17: VAT of a category = taxable amount × rate, rounded to cents
        if (rate != null && base != null && tax != null) {
            BigDecimal expected = base.multiply(rate).divide(HUNDRED, 2, RoundingMode.HALF_UP);
            if (expected.subtract(tax).abs().compareTo(TOLERANCE) > 0) {
                findings.add(Severity.ERROR, "BR-CO-17",
                        "%s: Steuerbetrag %s entspricht nicht Bemessungsgrundlage %s × %s %% = %s."
                                .formatted(label, plain(tax), plain(base), plain(rate), plain(expected)));
            }
        }

        if ("S".equals(category) && rate != null && germanSupplier(invoice)
                && GERMAN_RATES.stream().noneMatch(german -> german.compareTo(rate) == 0)) {
            findings.add(Severity.WARNING, "DT-VAT-RATE-UNUSUAL",
                    "%s: Ungewöhnlicher Steuersatz für einen deutschen Lieferanten (üblich sind 19 %% und 7 %%)."
                            .formatted(label));
        }

        if (WITHOUT_VAT.contains(category) && (nonZero(rate) || nonZero(tax))) {
            findings.add(Severity.ERROR, "DT-VAT-NOT-ZERO",
                    "%s: In dieser Kategorie darf keine Umsatzsteuer ausgewiesen werden (Steuersatz und Steuerbetrag 0)."
                            .formatted(label));
        }

        if (NEED_REASON.contains(category) && blank(row.exemptionReason())) {
            findings.add(Severity.WARNING, "DT-VAT-REASON-MISSING",
                    "%s: Der Grund für die Steuerbefreiung fehlt (z. B. „Steuerschuldnerschaft des Leistungsempfängers“)."
                            .formatted(label));
        }

        if (("AE".equals(category) || "K".equals(category)) && blank(invoice.getSupplierVatId())) {
            findings.add(Severity.WARNING, "DT-VAT-SELLER-ID-MISSING",
                    "%s: Die USt-IdNr. des Lieferanten ist in dieser Kategorie Pflicht.".formatted(label));
        }

        String currency = invoice.getCurrency() == null ? "EUR" : invoice.getCurrency();
        String amount = base == null ? "" : " (Bemessungsgrundlage %s %s)".formatted(plain(base), currency);
        if ("AE".equals(category)) {
            findings.add(Severity.INFO, "DT-REVERSE-CHARGE",
                    "Steuerschuldnerschaft des Leistungsempfängers (§ 13b UStG)%s: ".formatted(amount)
                            + "Die Umsatzsteuer meldet das eigene Unternehmen selbst an und zieht sie als Vorsteuer ab. "
                            + "Bitte in der Buchhaltung entsprechend kontieren.");
        }
        if ("K".equals(category)) {
            findings.add(Severity.INFO, "DT-INTRA-EU-ACQUISITION",
                    "Innergemeinschaftlicher Erwerb%s: ".formatted(amount)
                            + "Die Erwerbsteuer meldet das eigene Unternehmen selbst an und zieht sie als Vorsteuer ab.");
        }
    }

    // BR-CO-14: the VAT of all categories adds up to the invoice's VAT total
    private void checkTaxTotal(Invoice invoice, List<VatSubtotal> breakdown, Findings findings) {
        if (invoice.getTaxAmount() == null || breakdown.stream().anyMatch(row -> row.taxAmount() == null)) {
            return;
        }
        BigDecimal sum = breakdown.stream().map(VatSubtotal::taxAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (!Amounts.equalInCents(sum, invoice.getTaxAmount())) {
            findings.add(Severity.ERROR, "BR-CO-14",
                    "Summe der Steuerbeträge der USt-Aufstellung (%s) entspricht nicht dem Umsatzsteuer-Gesamtbetrag (%s)."
                            .formatted(plain(sum), plain(invoice.getTaxAmount())));
        }
    }

    // The taxable amounts of all categories add up to the invoice's net total
    private void checkBaseTotal(Invoice invoice, List<VatSubtotal> breakdown, Findings findings) {
        if (invoice.getNetAmount() == null || breakdown.stream().anyMatch(row -> row.taxableAmount() == null)) {
            return;
        }
        BigDecimal sum = breakdown.stream().map(VatSubtotal::taxableAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (!Amounts.equalInCents(sum, invoice.getNetAmount())) {
            findings.add(Severity.WARNING, "DT-VAT-BASE-TOTAL",
                    "Summe der Bemessungsgrundlagen der USt-Aufstellung (%s) weicht vom Nettobetrag (%s) ab."
                            .formatted(plain(sum), plain(invoice.getNetAmount())));
        }
    }

    // The lines of each category and rate add up to its taxable amount. Only without document-level
    // allowances and charges: their VAT category is not stored, so they cannot be assigned to a row.
    private void checkLines(Invoice invoice, List<VatSubtotal> breakdown, Findings findings) {
        if (nonZero(invoice.getAllowanceTotal()) || nonZero(invoice.getChargeTotal()) || invoice.getLines().isEmpty()) {
            return;
        }
        Map<String, BigDecimal> perCategory = new LinkedHashMap<>();
        for (InvoiceLine line : invoice.getLines()) {
            if (line.getTaxCategory() == null || line.getTaxRate() == null || line.getNetAmount() == null) {
                return;
            }
            perCategory.merge(key(line.getTaxCategory(), line.getTaxRate()), line.getNetAmount(), BigDecimal::add);
        }
        for (VatSubtotal row : breakdown) {
            if (row.category() == null || row.rate() == null || row.taxableAmount() == null) {
                continue;
            }
            BigDecimal lines = perCategory.getOrDefault(key(row.category(), row.rate()), BigDecimal.ZERO);
            if (!Amounts.equalInCents(lines, row.taxableAmount())) {
                findings.add(Severity.WARNING, "DT-VAT-LINES-MISMATCH",
                        "%s: Summe der Positionen dieser Kategorie (%s) weicht von der Bemessungsgrundlage (%s) ab."
                                .formatted(label(row), plain(lines), plain(row.taxableAmount())));
            }
        }
    }

    // One finding per rule id, as the validation service expects; several rows with the same problem
    // are listed in one message instead of only the first one being kept.
    private static final class Findings {

        private final Map<String, Severity> severities = new LinkedHashMap<>();
        private final Map<String, List<String>> messages = new LinkedHashMap<>();

        void add(Severity severity, String ruleId, String message) {
            severities.putIfAbsent(ruleId, severity);
            messages.computeIfAbsent(ruleId, id -> new ArrayList<>()).add(message);
        }

        List<Finding> toList() {
            return severities.entrySet().stream()
                    .map(entry -> {
                        String text = String.join(" ", messages.get(entry.getKey()));
                        String limited = text.length() > MAX_MESSAGE_LENGTH ? text.substring(0, MAX_MESSAGE_LENGTH) : text;
                        return new Finding(entry.getValue(), entry.getKey(), limited);
                    })
                    .toList();
        }
    }
}