package com.kate.dewtally.parsing;

import com.kate.dewtally.invoice.InvoiceData;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the fields dewtally needs from a UBL invoice or credit note.
 * Paths follow the EN 16931 UBL syntax binding (e.g. BT-1 invoice number = cbc:ID).
 */
final class UblInvoiceParser {

    private static final String SUPPLIER = "cac:AccountingSupplierParty/cac:Party/";
    private static final String TOTALS = "cac:LegalMonetaryTotal/";
    // Direct children of the root are document-level; line-level ones sit inside each line.
    private static final String ALLOWANCE_CHARGE = "cac:AllowanceCharge";
    private static final String INDICATOR = "cbc:ChargeIndicator";
    private static final String AMOUNT = "cbc:Amount";

    private static List<ParsedLine> lines(XPathReader xml, Element root, boolean creditNote) {
        // Credit notes use different element names for lines and quantities.
        String lineElement = creditNote ? "cac:CreditNoteLine" : "cac:InvoiceLine";
        String quantity = creditNote ? "cbc:CreditedQuantity" : "cbc:InvoicedQuantity";

        List<ParsedLine> lines = new ArrayList<>();
        int lineNo = 1;
        for (Element line : xml.elements(root, lineElement)) {
            lines.add(new ParsedLine(
                    lineNo++,
                    xml.text(line, "cac:Item/cbc:Name", 1000),
                    xml.decimal(line, quantity),
                    xml.text(line, quantity + "/@unitCode", 10),
                    xml.decimal(line, "cac:Price/cbc:PriceAmount"),
                    xml.decimal(line, "cbc:LineExtensionAmount"),
                    xml.decimal(line, "cac:Item/cac:ClassifiedTaxCategory/cbc:Percent")));
        }
        return lines;
    }

    // UBL dates are ISO dates: 2027-03-14
    private static LocalDate date(String value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    // IBANs are often written in groups of four ("DE89 3704 ..."); store them without spaces.
    static String iban(String value) {
        if (value == null) {
            return null;
        }
        String compact = value.replace(" ", "");
        return compact.length() > 34 ? compact.substring(0, 34) : compact;
    }

    ParsedInvoice parse(Document document) {
        XPathReader xml = new XPathReader();
        Element root = document.getDocumentElement();
        boolean creditNote = "CreditNote".equals(root.getLocalName());

        String supplierName = xml.text(root, SUPPLIER + "cac:PartyLegalEntity/cbc:RegistrationName", 300);
        if (supplierName == null) {
            // Trading name as a fallback when the legal name is missing.
            supplierName = xml.text(root, SUPPLIER + "cac:PartyName/cbc:Name", 300);
        }

        var entries = xml.elements(root, ALLOWANCE_CHARGE);
        BigDecimal allowances = AllowanceCharges.total(xml,
                xml.decimal(root, TOTALS + "cbc:AllowanceTotalAmount"), entries, INDICATOR, AMOUNT, false);
        BigDecimal charges = AllowanceCharges.total(xml,
                xml.decimal(root, TOTALS + "cbc:ChargeTotalAmount"), entries, INDICATOR, AMOUNT, true);

        InvoiceData data = new InvoiceData(
                xml.text(root, "cbc:ID", 100),
                date(xml.text(root, "cbc:IssueDate")),
                date(xml.text(root, "cbc:DueDate")),
                xml.text(root, "cbc:DocumentCurrencyCode", 3),
                supplierName,
                xml.text(root, SUPPLIER + "cac:PartyTaxScheme[cac:TaxScheme/cbc:ID='VAT']/cbc:CompanyID", 30),
                xml.text(root, "cbc:BuyerReference", 100),
                iban(xml.text(root, "cac:PaymentMeans/cac:PayeeFinancialAccount/cbc:ID")),
                xml.decimal(root, TOTALS + "cbc:TaxExclusiveAmount"),
                xml.decimal(root, "cac:TaxTotal/cbc:TaxAmount"),
                xml.decimal(root, TOTALS + "cbc:TaxInclusiveAmount"),
                allowances,
                charges);

        return new ParsedInvoice(data, lines(xml, root, creditNote));
    }
}