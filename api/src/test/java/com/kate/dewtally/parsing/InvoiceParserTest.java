package com.kate.dewtally.parsing;

import com.kate.dewtally.document.SourceFormat;
import com.kate.dewtally.invoice.InvoiceData;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests: the same sample invoice in UBL, CII and ZUGFeRD must give the same data.
 */
class InvoiceParserTest {

    private final InvoiceParser parser = new InvoiceParser();

    private static void assertIsTheSampleInvoice(ParsedInvoice invoice) {
        InvoiceData data = invoice.data();
        assertThat(data.invoiceNumber()).isEqualTo("RE-2041");
        assertThat(data.issueDate()).isEqualTo(LocalDate.of(2027, 3, 14));
        assertThat(data.dueDate()).isEqualTo(LocalDate.of(2027, 4, 13));
        assertThat(data.currency()).isEqualTo("EUR");
        assertThat(data.supplierName()).isEqualTo("Muster GmbH");
        assertThat(data.supplierVatId()).isEqualTo("DE123456789");
        assertThat(data.buyerReference()).isEqualTo("04011000-12345-34");
        assertThat(data.iban()).isEqualTo("DE89370400440532013000");
        assertThat(data.netAmount()).isEqualByComparingTo("1200.00");
        assertThat(data.taxAmount()).isEqualByComparingTo("228.00");
        assertThat(data.grossAmount()).isEqualByComparingTo("1428.00");

        assertThat(invoice.lines()).hasSize(2);
        ParsedLine first = invoice.lines().get(0);
        assertThat(first.lineNo()).isEqualTo(1);
        assertThat(first.description()).isEqualTo("Consulting");
        assertThat(first.quantity()).isEqualByComparingTo("10");
        assertThat(first.unitCode()).isEqualTo("HUR");
        assertThat(first.unitPrice()).isEqualByComparingTo("100.00");
        assertThat(first.netAmount()).isEqualByComparingTo("1000.00");
        assertThat(first.taxRate()).isEqualByComparingTo("19");
        assertThat(invoice.lines().get(1).description()).isEqualTo("Travel expenses");
    }

    static byte[] resource(String name) throws IOException {
        try (InputStream in = InvoiceParserTest.class.getResourceAsStream("/invoices/" + name)) {
            if (in == null) {
                throw new IllegalStateException("Missing test resource /invoices/" + name);
            }
            return in.readAllBytes();
        }
    }

    private static byte[] pdfWithAttachment(String fileName, byte[] attachment) throws IOException {
        try (PDDocument pdf = new PDDocument()) {
            pdf.addPage(new PDPage());

            PDEmbeddedFile embedded = new PDEmbeddedFile(pdf, new ByteArrayInputStream(attachment));
            embedded.setSubtype("text/xml");
            embedded.setSize(attachment.length);

            PDComplexFileSpecification spec = new PDComplexFileSpecification();
            spec.setFile(fileName);
            spec.setFileUnicode(fileName);
            spec.setEmbeddedFile(embedded);
            spec.setEmbeddedFileUnicode(embedded);

            PDEmbeddedFilesNameTreeNode tree = new PDEmbeddedFilesNameTreeNode();
            tree.setNames(Map.of(fileName, spec));

            PDDocumentNameDictionary names = new PDDocumentNameDictionary(pdf.getDocumentCatalog());
            names.setEmbeddedFiles(tree);
            pdf.getDocumentCatalog().setNames(names);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            pdf.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void readsXRechnungInUblSyntax() throws IOException {
        ParsedInvoice invoice = parser.parse(SourceFormat.UBL, resource("xrechnung-ubl.xml")).orElseThrow();

        assertIsTheSampleInvoice(invoice);
    }

    @Test
    void readsXRechnungInCiiSyntax() throws IOException {
        ParsedInvoice invoice = parser.parse(SourceFormat.CII, resource("xrechnung-cii.xml")).orElseThrow();

        assertIsTheSampleInvoice(invoice);
    }

    @Test
    void readsTheCiiInvoiceEmbeddedInAZugferdPdf() throws IOException {
        byte[] pdf = pdfWithAttachment("factur-x.xml", resource("xrechnung-cii.xml"));

        ParsedInvoice invoice = parser.parse(SourceFormat.ZUGFERD, pdf).orElseThrow();

        assertIsTheSampleInvoice(invoice);
    }

    @Test
    void returnsNothingForFormatsWithoutStructuredData() {
        assertThat(parser.parse(SourceFormat.PDF, new byte[]{1, 2, 3})).isEmpty();
        assertThat(parser.parse(SourceFormat.UNKNOWN, new byte[]{1, 2, 3})).isEmpty();
    }

    @Test
    void leavesMissingFieldsEmptyInsteadOfFailing() {
        String minimal = """
                <Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
                         xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
                  <cbc:ID>RE-1</cbc:ID>
                </Invoice>
                """;

        ParsedInvoice invoice = parser.parse(SourceFormat.UBL, minimal.getBytes(StandardCharsets.UTF_8)).orElseThrow();

        assertThat(invoice.data().invoiceNumber()).isEqualTo("RE-1");
        assertThat(invoice.data().grossAmount()).isNull();
        assertThat(invoice.data().issueDate()).isNull();
        assertThat(invoice.lines()).isEmpty();
    }

    @Test
    void ignoresValuesThatAreNotValidNumbersOrDates() {
        String broken = """
                <Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
                         xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
                         xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
                  <cbc:ID>RE-2</cbc:ID>
                  <cbc:IssueDate>14.03.2027</cbc:IssueDate>
                  <cac:LegalMonetaryTotal>
                    <cbc:TaxInclusiveAmount currencyID="EUR">eintausend</cbc:TaxInclusiveAmount>
                  </cac:LegalMonetaryTotal>
                </Invoice>
                """;

        ParsedInvoice invoice = parser.parse(SourceFormat.UBL, broken.getBytes(StandardCharsets.UTF_8)).orElseThrow();

        assertThat(invoice.data().issueDate()).isNull();
        assertThat(invoice.data().grossAmount()).isNull();
    }
}