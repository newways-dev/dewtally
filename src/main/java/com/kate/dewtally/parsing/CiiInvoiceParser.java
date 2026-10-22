package com.kate.dewtally.parsing;

import com.kate.dewtally.invoice.InvoiceData;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the fields dewtally needs from a UN/CEFACT CII invoice (XRechnung CII, ZUGFeRD, Factur-X).
 * Paths follow the EN 16931 CII syntax binding.
 */
final class CiiInvoiceParser {

    private static final String TRADE = "rsm:SupplyChainTradeTransaction/";
    private static final String AGREEMENT = TRADE + "ram:ApplicableHeaderTradeAgreement/";
    private static final String SETTLEMENT = TRADE + "ram:ApplicableHeaderTradeSettlement/";
    private static final String TOTALS = SETTLEMENT + "ram:SpecifiedTradeSettlementHeaderMonetarySummation/";

    private static List<ParsedLine> lines(XPathReader xml, Element root) {
        List<ParsedLine> lines = new ArrayList<>();
        int lineNo = 1;
        for (Element line : xml.elements(root, TRADE + "ram:IncludedSupplyChainTradeLineItem")) {
            String quantity = "ram:SpecifiedLineTradeDelivery/ram:BilledQuantity";
            String settlement = "ram:SpecifiedLineTradeSettlement/";
            lines.add(new ParsedLine(
                    lineNo++,
                    xml.text(line, "ram:SpecifiedTradeProduct/ram:Name", 1000),
                    xml.decimal(line, quantity),
                    xml.text(line, quantity + "/@unitCode", 10),
                    xml.decimal(line, "ram:SpecifiedLineTradeAgreement/ram:NetPriceProductTradePrice/ram:ChargeAmount"),
                    xml.decimal(line, settlement
                            + "ram:SpecifiedTradeSettlementLineMonetarySummation/ram:LineTotalAmount"),
                    xml.decimal(line, settlement + "ram:ApplicableTradeTax/ram:RateApplicablePercent")));
        }
        return lines;
    }

    // CII dates use format code 102: yyyyMMdd, e.g. 20270314
    private static LocalDate date(String value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    ParsedInvoice parse(Document document) {
        XPathReader xml = new XPathReader();
        Element root = document.getDocumentElement();

        InvoiceData data = new InvoiceData(
                xml.text(root, "rsm:ExchangedDocument/ram:ID", 100),
                date(xml.text(root, "rsm:ExchangedDocument/ram:IssueDateTime/udt:DateTimeString")),
                date(xml.text(root, SETTLEMENT
                        + "ram:SpecifiedTradePaymentTerms/ram:DueDateDateTime/udt:DateTimeString")),
                xml.text(root, SETTLEMENT + "ram:InvoiceCurrencyCode", 3),
                xml.text(root, AGREEMENT + "ram:SellerTradeParty/ram:Name", 300),
                // schemeID "VA" = VAT identification number (USt-IdNr)
                xml.text(root, AGREEMENT
                        + "ram:SellerTradeParty/ram:SpecifiedTaxRegistration/ram:ID[@schemeID='VA']", 30),
                xml.text(root, AGREEMENT + "ram:BuyerReference", 100),
                UblInvoiceParser.iban(xml.text(root, SETTLEMENT
                        + "ram:SpecifiedTradeSettlementPaymentMeans/ram:PayeePartyCreditorFinancialAccount/ram:IBANID")),
                xml.decimal(root, TOTALS + "ram:TaxBasisTotalAmount"),
                xml.decimal(root, TOTALS + "ram:TaxTotalAmount"),
                xml.decimal(root, TOTALS + "ram:GrandTotalAmount"));

        return new ParsedInvoice(data, lines(xml, root));
    }
}