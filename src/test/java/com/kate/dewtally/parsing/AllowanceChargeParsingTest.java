package com.kate.dewtally.parsing;

import com.kate.dewtally.invoice.InvoiceData;
import com.kate.dewtally.xml.SafeXml;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Document-level allowances and charges, read from the stated totals or summed from single entries.
 */
class AllowanceChargeParsingTest {

    private static final String CII_START = """
            <rsm:CrossIndustryInvoice
                xmlns:rsm="urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100"
                xmlns:ram="urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100"
                xmlns:udt="urn:un:unece:uncefact:data:standard:UnqualifiedDataType:100">
              <rsm:ExchangedDocument><ram:ID>R-1</ram:ID></rsm:ExchangedDocument>
              <rsm:SupplyChainTradeTransaction>
                <ram:ApplicableHeaderTradeSettlement>
                  <ram:InvoiceCurrencyCode>EUR</ram:InvoiceCurrencyCode>
            """;
    private static final String CII_END = """
                </ram:ApplicableHeaderTradeSettlement>
              </rsm:SupplyChainTradeTransaction>
            </rsm:CrossIndustryInvoice>
            """;

    private static final String UBL_START = """
            <Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
                xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
                xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
              <cbc:ID>R-1</cbc:ID>
            """;

    private static String ciiEntry(boolean charge, String amount) {
        return """
                <ram:SpecifiedTradeAllowanceCharge>
                  <ram:ChargeIndicator><udt:Indicator>%s</udt:Indicator></ram:ChargeIndicator>
                  <ram:ActualAmount>%s</ram:ActualAmount>
                </ram:SpecifiedTradeAllowanceCharge>
                """.formatted(charge, amount);
    }

    private static String ublEntry(boolean charge, String amount) {
        return """
                <cac:AllowanceCharge>
                  <cbc:ChargeIndicator>%s</cbc:ChargeIndicator>
                  <cbc:Amount currencyID="EUR">%s</cbc:Amount>
                </cac:AllowanceCharge>
                """.formatted(charge, amount);
    }

    private static InvoiceData cii(String xml) {
        return new CiiInvoiceParser().parse(SafeXml.parse(xml.getBytes(StandardCharsets.UTF_8))).data();
    }

    private static InvoiceData ubl(String xml) {
        return new UblInvoiceParser().parse(SafeXml.parse(xml.getBytes(StandardCharsets.UTF_8))).data();
    }

    @Test
    void ciiReadsStatedTotals() {
        InvoiceData data = cii(CII_START + """
                <ram:SpecifiedTradeSettlementHeaderMonetarySummation>
                  <ram:LineTotalAmount>2654.83</ram:LineTotalAmount>
                  <ram:ChargeTotalAmount>0.00</ram:ChargeTotalAmount>
                  <ram:AllowanceTotalAmount>268.28</ram:AllowanceTotalAmount>
                  <ram:TaxBasisTotalAmount>2386.55</ram:TaxBasisTotalAmount>
                </ram:SpecifiedTradeSettlementHeaderMonetarySummation>
                """ + CII_END);

        assertThat(data.allowanceTotal()).isEqualByComparingTo("268.28");
        assertThat(data.chargeTotal()).isEqualByComparingTo("0");
        assertThat(data.netAmount()).isEqualByComparingTo("2386.55");
    }

    @Test
    void ciiSumsSingleEntriesWhenTotalsAreMissing() {
        InvoiceData data = cii(CII_START
                + ciiEntry(false, "100.00") + ciiEntry(false, "20.00") + ciiEntry(true, "5.90")
                + CII_END);

        assertThat(data.allowanceTotal()).isEqualByComparingTo("120.00");
        assertThat(data.chargeTotal()).isEqualByComparingTo("5.90");
    }

    @Test
    void ciiWithoutAllowancesHasNone() {
        InvoiceData data = cii(CII_START + CII_END);

        assertThat(data.allowanceTotal()).isNull();
        assertThat(data.chargeTotal()).isNull();
    }

    @Test
    void ublReadsStatedTotals() {
        InvoiceData data = ubl(UBL_START + """
                <cac:LegalMonetaryTotal>
                  <cbc:LineExtensionAmount currencyID="EUR">1000.00</cbc:LineExtensionAmount>
                  <cbc:TaxExclusiveAmount currencyID="EUR">905.90</cbc:TaxExclusiveAmount>
                  <cbc:AllowanceTotalAmount currencyID="EUR">100.00</cbc:AllowanceTotalAmount>
                  <cbc:ChargeTotalAmount currencyID="EUR">5.90</cbc:ChargeTotalAmount>
                </cac:LegalMonetaryTotal>
                </Invoice>
                """);

        assertThat(data.allowanceTotal()).isEqualByComparingTo("100.00");
        assertThat(data.chargeTotal()).isEqualByComparingTo("5.90");
    }

    @Test
    void ublCountsOnlyDocumentLevelEntries() {
        // The allowance inside the line is already part of that line's net amount: it must not count twice.
        InvoiceData data = ubl(UBL_START
                + ublEntry(false, "50.00")
                + """
                <cac:InvoiceLine>
                  <cbc:ID>1</cbc:ID>
                """ + ublEntry(false, "999.00") + """
                </cac:InvoiceLine>
                </Invoice>
                """);

        assertThat(data.allowanceTotal()).isEqualByComparingTo("50.00");
        assertThat(data.chargeTotal()).isNull();
    }
}