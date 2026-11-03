package com.kate.dewtally.parsing;

import com.kate.dewtally.invoice.VatSubtotal;
import com.kate.dewtally.xml.SafeXml;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class VatBreakdownParsingTest {

    private static Document xml(String text) {
        return SafeXml.parse(text.getBytes(StandardCharsets.UTF_8));
    }

    // Office supplies at 19 % and a book at 7 %: two rows in the breakdown
    @Test
    void ublReadsOneRowPerRateAndTheCategoryOfEachLine() {
        ParsedInvoice parsed = new UblInvoiceParser().parse(xml("""
                <Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
                         xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
                         xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
                  <cbc:ID>RE-2027-1</cbc:ID>
                  <cac:TaxTotal>
                    <cbc:TaxAmount currencyID="EUR">204.00</cbc:TaxAmount>
                    <cac:TaxSubtotal>
                      <cbc:TaxableAmount currencyID="EUR">1000.00</cbc:TaxableAmount>
                      <cbc:TaxAmount currencyID="EUR">190.00</cbc:TaxAmount>
                      <cac:TaxCategory>
                        <cbc:ID>S</cbc:ID>
                        <cbc:Percent>19</cbc:Percent>
                        <cac:TaxScheme><cbc:ID>VAT</cbc:ID></cac:TaxScheme>
                      </cac:TaxCategory>
                    </cac:TaxSubtotal>
                    <cac:TaxSubtotal>
                      <cbc:TaxableAmount currencyID="EUR">200.00</cbc:TaxableAmount>
                      <cbc:TaxAmount currencyID="EUR">14.00</cbc:TaxAmount>
                      <cac:TaxCategory>
                        <cbc:ID>S</cbc:ID>
                        <cbc:Percent>7</cbc:Percent>
                        <cac:TaxScheme><cbc:ID>VAT</cbc:ID></cac:TaxScheme>
                      </cac:TaxCategory>
                    </cac:TaxSubtotal>
                  </cac:TaxTotal>
                  <cac:InvoiceLine>
                    <cbc:ID>1</cbc:ID>
                    <cbc:InvoicedQuantity unitCode="C62">10</cbc:InvoicedQuantity>
                    <cbc:LineExtensionAmount currencyID="EUR">1000.00</cbc:LineExtensionAmount>
                    <cac:Item>
                      <cbc:Name>Office chair</cbc:Name>
                      <cac:ClassifiedTaxCategory>
                        <cbc:ID>S</cbc:ID>
                        <cbc:Percent>19</cbc:Percent>
                        <cac:TaxScheme><cbc:ID>VAT</cbc:ID></cac:TaxScheme>
                      </cac:ClassifiedTaxCategory>
                    </cac:Item>
                  </cac:InvoiceLine>
                  <cac:InvoiceLine>
                    <cbc:ID>2</cbc:ID>
                    <cbc:InvoicedQuantity unitCode="C62">1</cbc:InvoicedQuantity>
                    <cbc:LineExtensionAmount currencyID="EUR">200.00</cbc:LineExtensionAmount>
                    <cac:Item>
                      <cbc:Name>Handbook of German tax law</cbc:Name>
                      <cac:ClassifiedTaxCategory>
                        <cbc:ID>S</cbc:ID>
                        <cbc:Percent>7</cbc:Percent>
                        <cac:TaxScheme><cbc:ID>VAT</cbc:ID></cac:TaxScheme>
                      </cac:ClassifiedTaxCategory>
                    </cac:Item>
                  </cac:InvoiceLine>
                </Invoice>
                """));

        assertThat(parsed.vatBreakdown())
                .extracting(VatSubtotal::category, VatSubtotal::rate, VatSubtotal::taxableAmount, VatSubtotal::taxAmount)
                .containsExactly(
                        tuple("S", new BigDecimal("19"), new BigDecimal("1000.00"), new BigDecimal("190.00")),
                        tuple("S", new BigDecimal("7"), new BigDecimal("200.00"), new BigDecimal("14.00")));
        assertThat(parsed.lines())
                .extracting(ParsedLine::taxCategory, ParsedLine::taxRate)
                .containsExactly(tuple("S", new BigDecimal("19")), tuple("S", new BigDecimal("7")));
    }

    // Construction work from a German subcontractor: the buyer owes the VAT (§ 13b UStG), the invoice shows none
    @Test
    void ciiReadsReverseChargeWithItsReason() {
        ParsedInvoice parsed = new CiiInvoiceParser().parse(xml("""
                <rsm:CrossIndustryInvoice
                    xmlns:rsm="urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100"
                    xmlns:ram="urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100"
                    xmlns:udt="urn:un:unece:uncefact:data:standard:UnqualifiedDataType:100">
                  <rsm:ExchangedDocument><ram:ID>BAU-77</ram:ID></rsm:ExchangedDocument>
                  <rsm:SupplyChainTradeTransaction>
                    <ram:IncludedSupplyChainTradeLineItem>
                      <ram:SpecifiedTradeProduct><ram:Name>Drywall installation</ram:Name></ram:SpecifiedTradeProduct>
                      <ram:SpecifiedLineTradeSettlement>
                        <ram:ApplicableTradeTax>
                          <ram:TypeCode>VAT</ram:TypeCode>
                          <ram:CategoryCode>AE</ram:CategoryCode>
                          <ram:RateApplicablePercent>0</ram:RateApplicablePercent>
                        </ram:ApplicableTradeTax>
                        <ram:SpecifiedTradeSettlementLineMonetarySummation>
                          <ram:LineTotalAmount>5000.00</ram:LineTotalAmount>
                        </ram:SpecifiedTradeSettlementLineMonetarySummation>
                      </ram:SpecifiedLineTradeSettlement>
                    </ram:IncludedSupplyChainTradeLineItem>
                    <ram:ApplicableHeaderTradeSettlement>
                      <ram:InvoiceCurrencyCode>EUR</ram:InvoiceCurrencyCode>
                      <ram:ApplicableTradeTax>
                        <ram:CalculatedAmount>0.00</ram:CalculatedAmount>
                        <ram:TypeCode>VAT</ram:TypeCode>
                        <ram:ExemptionReason>Steuerschuldnerschaft des Leistungsempfängers</ram:ExemptionReason>
                        <ram:BasisAmount>5000.00</ram:BasisAmount>
                        <ram:CategoryCode>AE</ram:CategoryCode>
                        <ram:RateApplicablePercent>0</ram:RateApplicablePercent>
                      </ram:ApplicableTradeTax>
                    </ram:ApplicableHeaderTradeSettlement>
                  </rsm:SupplyChainTradeTransaction>
                </rsm:CrossIndustryInvoice>
                """));

        assertThat(parsed.vatBreakdown()).containsExactly(new VatSubtotal(
                "AE", new BigDecimal("0"), new BigDecimal("5000.00"), new BigDecimal("0.00"),
                "Steuerschuldnerschaft des Leistungsempfängers"));
        // The line-level tax entry names the line's category, it is not an extra row of the breakdown
        assertThat(parsed.lines()).extracting(ParsedLine::taxCategory).containsExactly("AE");
    }

    // Small business without VAT (§ 19 UStG): only the reason code, no text
    @Test
    void ublFallsBackToTheExemptionReasonCode() {
        ParsedInvoice parsed = new UblInvoiceParser().parse(xml("""
                <Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
                         xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
                         xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
                  <cbc:ID>KU-5</cbc:ID>
                  <cac:TaxTotal>
                    <cbc:TaxAmount currencyID="EUR">0.00</cbc:TaxAmount>
                    <cac:TaxSubtotal>
                      <cbc:TaxableAmount currencyID="EUR">350.00</cbc:TaxableAmount>
                      <cbc:TaxAmount currencyID="EUR">0.00</cbc:TaxAmount>
                      <cac:TaxCategory>
                        <cbc:ID>E</cbc:ID>
                        <cbc:Percent>0</cbc:Percent>
                        <cbc:TaxExemptionReasonCode>VATEX-EU-132</cbc:TaxExemptionReasonCode>
                        <cac:TaxScheme><cbc:ID>VAT</cbc:ID></cac:TaxScheme>
                      </cac:TaxCategory>
                    </cac:TaxSubtotal>
                  </cac:TaxTotal>
                </Invoice>
                """));

        assertThat(parsed.vatBreakdown())
                .extracting(VatSubtotal::category, VatSubtotal::exemptionReason)
                .containsExactly(tuple("E", "VATEX-EU-132"));
    }
}