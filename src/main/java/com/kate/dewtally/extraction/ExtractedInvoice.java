package com.kate.dewtally.extraction;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.kate.dewtally.invoice.InvoiceData;
import com.kate.dewtally.parsing.ParsedInvoice;
import com.kate.dewtally.parsing.ParsedLine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Exactly what the language model must return. Spring AI turns this record into a JSON schema,
 * and OpenAI's structured output guarantees the answer follows it.
 * <p>
 * Dates and amounts are strings on purpose: we convert them ourselves and turn anything
 * unexpected into null instead of guessing. Missing values come back as empty strings.
 */
public record ExtractedInvoice(
        @JsonPropertyDescription("Invoice number (Rechnungsnummer)")
        String invoiceNumber,
        @JsonPropertyDescription("Invoice date as YYYY-MM-DD")
        String issueDate,
        @JsonPropertyDescription("Payment due date as YYYY-MM-DD, empty if not stated")
        String dueDate,
        @JsonPropertyDescription("ISO 4217 currency code, e.g. EUR")
        String currency,
        @JsonPropertyDescription("Legal name of the seller who issued the invoice")
        String supplierName,
        @JsonPropertyDescription("Seller VAT identification number (USt-IdNr.), e.g. DE123456789, without spaces")
        String supplierVatId,
        @JsonPropertyDescription("Buyer reference or Leitweg-ID, empty if not stated")
        String buyerReference,
        @JsonPropertyDescription("Seller IBAN for payment, without spaces")
        String iban,
        @JsonPropertyDescription("Total net amount (Netto) as plain number with dot, e.g. 1200.00")
        String netAmount,
        @JsonPropertyDescription("Total VAT amount (MwSt./USt.) as plain number with dot, e.g. 228.00")
        String taxAmount,
        @JsonPropertyDescription("Total gross amount (Brutto/Gesamtbetrag) as plain number with dot, e.g. 1428.00")
        String grossAmount,
        @JsonPropertyDescription("Total of discounts on the whole invoice that are not invoice lines "
                + "(Rabatt, Nachlass), positive plain number with dot; empty if none. Skonto is not a discount here")
        String allowanceTotal,
        @JsonPropertyDescription("Total of surcharges on the whole invoice that are not invoice lines "
                + "(Versandkosten, Fracht, Verpackung), positive plain number with dot; empty if none")
        String chargeTotal,
        @JsonPropertyDescription("Invoice lines in the order they appear")
        List<Line> lines,
        @JsonPropertyDescription("Your confidence from 0.0 to 1.0 that all fields above are correct")
        double confidence
) {

    // Tells Jackson to use this (canonical) constructor for JSON, not the short one below.
    @JsonCreator
    public ExtractedInvoice {
    }

    /**
     * Answer without document-level allowances and charges (used by tests).
     */
    public ExtractedInvoice(String invoiceNumber, String issueDate, String dueDate, String currency,
                            String supplierName, String supplierVatId, String buyerReference, String iban,
                            String netAmount, String taxAmount, String grossAmount,
                            List<Line> lines, double confidence) {
        this(invoiceNumber, issueDate, dueDate, currency, supplierName, supplierVatId, buyerReference, iban,
                netAmount, taxAmount, grossAmount, "", "", lines, confidence);
    }

    static String text(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String stripped = value.strip();
        return stripped.length() > maxLength ? stripped.substring(0, maxLength) : stripped;
    }

    // IDs like IBAN or VAT number: no spaces, upper case.
    static String compact(String value, int maxLength) {
        String text = text(value, 100);
        if (text == null) {
            return null;
        }
        String compact = text.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
        return compact.length() > maxLength ? compact.substring(0, maxLength) : compact;
    }

    static String currency(String value) {
        String text = compact(value, 10);
        return text != null && text.matches("[A-Z]{3}") ? text : null;
    }

    static LocalDate date(String value) {
        String text = text(value, 20);
        if (text == null) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    // Accepts only the agreed format "1428.00". "1.428,00" is ambiguous, so it becomes null, not a guess.
    static BigDecimal amount(String value) {
        String text = text(value, 50);
        if (text == null) {
            return null;
        }
        String cleaned = text.replace("€", "").replace("EUR", "").replace("%", "").strip();
        if (!cleaned.matches("-?\\d+(\\.\\d+)?")) {
            return null;
        }
        return new BigDecimal(cleaned);
    }

    // Allowances and charges are positive by definition: a discount printed as "-268.28" means 268.28.
    static BigDecimal positiveAmount(String value) {
        BigDecimal amount = amount(value);
        return amount == null ? null : amount.abs();
    }

    /**
     * Converts the model's answer into the same structure the XML parsers produce.
     */
    public ParsedInvoice toParsedInvoice() {
        InvoiceData data = new InvoiceData(
                text(invoiceNumber, 100),
                date(issueDate),
                date(dueDate),
                currency(currency),
                text(supplierName, 300),
                compact(supplierVatId, 30),
                text(buyerReference, 100),
                compact(iban, 34),
                amount(netAmount),
                amount(taxAmount),
                amount(grossAmount),
                positiveAmount(allowanceTotal),
                positiveAmount(chargeTotal));

        List<ParsedLine> parsedLines = new ArrayList<>();
        int lineNo = 1;
        for (Line line : lines == null ? List.<Line>of() : lines) {
            parsedLines.add(new ParsedLine(
                    lineNo++,
                    text(line.description(), 1000),
                    amount(line.quantity()),
                    text(line.unit(), 10),
                    amount(line.unitPrice()),
                    amount(line.netAmount()),
                    amount(line.taxRate())));
        }
        return new ParsedInvoice(data, parsedLines);
    }

    public record Line(
            @JsonPropertyDescription("Description of the item or service")
            String description,
            @JsonPropertyDescription("Quantity as plain number with dot")
            String quantity,
            @JsonPropertyDescription("Unit, e.g. Stück, Std., kg; empty if not stated")
            String unit,
            @JsonPropertyDescription("Net price per unit as plain number with dot")
            String unitPrice,
            @JsonPropertyDescription("Net line total as plain number with dot")
            String netAmount,
            @JsonPropertyDescription("VAT rate in percent as plain number, e.g. 19")
            String taxRate
    ) {
    }
}