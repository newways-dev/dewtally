package com.kate.dewtally.format;

import com.kate.dewtally.document.SourceFormat;
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
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests: XML samples are inline, PDFs are generated in memory with PDFBox.
 */
class FormatDetectorTest {

    private static final String XRECHNUNG_PROFILE =
            "urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0";
    private static final String ZUGFERD_MINIMUM_PROFILE = "urn:factur-x.eu:1p0:minimum";

    private static final String UBL_INVOICE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
                     xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
              <cbc:CustomizationID>%s</cbc:CustomizationID>
              <cbc:ID>RE-2041</cbc:ID>
            </Invoice>
            """.formatted(XRECHNUNG_PROFILE);

    private static final String UBL_CREDIT_NOTE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <CreditNote xmlns="urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2"
                        xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
              <cbc:ID>GS-17</cbc:ID>
            </CreditNote>
            """;

    private static final String CII_INVOICE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rsm:CrossIndustryInvoice
                xmlns:rsm="urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100"
                xmlns:ram="urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100">
              <rsm:ExchangedDocumentContext>
                <ram:GuidelineSpecifiedDocumentContextParameter>
                  <ram:ID>%s</ram:ID>
                </ram:GuidelineSpecifiedDocumentContextParameter>
              </rsm:ExchangedDocumentContext>
            </rsm:CrossIndustryInvoice>
            """;

    // Classic XXE attack: tries to read a local file through an external entity.
    private static final String XXE_ATTACK = """
            <?xml version="1.0"?>
            <!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2">&xxe;</Invoice>
            """;

    private final FormatDetector detector = new FormatDetector();

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] plainPdf() throws IOException {
        try (PDDocument pdf = new PDDocument()) {
            pdf.addPage(new PDPage());
            return save(pdf);
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

            return save(pdf);
        }
    }

    private static byte[] save(PDDocument pdf) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        pdf.save(out);
        return out.toByteArray();
    }

    @Test
    void detectsUblInvoiceWithItsProfile() {
        DetectedFormat result = detector.detect(bytes(UBL_INVOICE));

        assertThat(result.format()).isEqualTo(SourceFormat.UBL);
        assertThat(result.profile()).isEqualTo(XRECHNUNG_PROFILE);
    }

    @Test
    void detectsUblCreditNoteWithoutProfile() {
        DetectedFormat result = detector.detect(bytes(UBL_CREDIT_NOTE));

        assertThat(result.format()).isEqualTo(SourceFormat.UBL);
        assertThat(result.profile()).isNull();
    }

    @Test
    void detectsCiiInvoiceWithItsProfile() {
        DetectedFormat result = detector.detect(bytes(CII_INVOICE.formatted("urn:cen.eu:en16931:2017")));

        assertThat(result.format()).isEqualTo(SourceFormat.CII);
        assertThat(result.profile()).isEqualTo("urn:cen.eu:en16931:2017");
    }

    @Test
    void treatsXmlWithoutKnownNamespaceAsUnknown() {
        assertThat(detector.detect(bytes("<Invoice>RE-2041</Invoice>")).format())
                .isEqualTo(SourceFormat.UNKNOWN);
    }

    @Test
    void treatsNonXmlTextAsUnknown() {
        assertThat(detector.detect(bytes("Hello, this is not an invoice")).format())
                .isEqualTo(SourceFormat.UNKNOWN);
    }

    @Test
    void rejectsXmlWithDoctypeToPreventXxe() {
        assertThat(detector.detect(bytes(XXE_ATTACK)).format())
                .isEqualTo(SourceFormat.UNKNOWN);
    }

    @Test
    void detectsPlainPdf() throws IOException {
        DetectedFormat result = detector.detect(plainPdf());

        assertThat(result.format()).isEqualTo(SourceFormat.PDF);
        assertThat(result.profile()).isNull();
    }

    @Test
    void detectsZugferdPdfAndReadsTheProfileOfTheEmbeddedXml() throws IOException {
        byte[] pdf = pdfWithAttachment("factur-x.xml", bytes(CII_INVOICE.formatted(ZUGFERD_MINIMUM_PROFILE)));

        DetectedFormat result = detector.detect(pdf);

        assertThat(result.format()).isEqualTo(SourceFormat.ZUGFERD);
        assertThat(result.profile()).isEqualTo(ZUGFERD_MINIMUM_PROFILE);
    }

    @Test
    void treatsPdfWithAnUnrelatedAttachmentAsPlainPdf() throws IOException {
        byte[] pdf = pdfWithAttachment("notes.xml", bytes(CII_INVOICE.formatted(ZUGFERD_MINIMUM_PROFILE)));

        assertThat(detector.detect(pdf).format()).isEqualTo(SourceFormat.PDF);
    }
}