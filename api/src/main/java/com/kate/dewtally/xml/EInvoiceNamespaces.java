package com.kate.dewtally.xml;

/**
 * XML namespaces of the two e-invoice syntaxes defined by EN 16931: UBL and UN/CEFACT CII.
 */
public final class EInvoiceNamespaces {

    // UBL 2.1
    public static final String UBL_INVOICE = "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2";
    public static final String UBL_CREDIT_NOTE = "urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2";
    public static final String UBL_CAC = "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";
    public static final String UBL_CBC = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";

    // UN/CEFACT Cross Industry Invoice (CII), also used inside ZUGFeRD / Factur-X
    public static final String CII_RSM = "urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100";
    public static final String CII_RAM = "urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100";
    public static final String CII_UDT = "urn:un:unece:uncefact:data:standard:UnqualifiedDataType:100";

    private EInvoiceNamespaces() {
    }
}