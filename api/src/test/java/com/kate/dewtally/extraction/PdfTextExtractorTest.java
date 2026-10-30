package com.kate.dewtally.extraction;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfTextExtractorTest {

    private final PdfTextExtractor extractor = new PdfTextExtractor(20, 50_000);

    // Each inner array is one page, each string one line on that page.
    private static byte[] pdfWithPages(String[][] pages) throws IOException {
        try (PDDocument document = new PDDocument()) {
            for (String[] lines : pages) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    float y = 750;
                    for (String line : lines) {
                        writeLine(content, line, y);
                        y -= 20;
                    }
                }
            }
            return save(document);
        }
    }

    private static void writeLine(PDPageContentStream content, String line, float y) throws IOException {
        content.beginText();
        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        content.newLineAtOffset(50, y);
        content.showText(line);
        content.endText();
    }

    private static byte[] save(PDDocument document) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        document.save(out);
        return out.toByteArray();
    }

    @Test
    void extractsTextOfASimpleInvoice() throws IOException {
        byte[] pdf = pdfWithPages(new String[][]{{
                "Muster GmbH, Mühlenweg 1, 20095 Hamburg",
                "Rechnung Nr. RE-2041",
                "Rechnungsdatum: 14.03.2027",
                "Gesamtbetrag: 1.428,00 EUR"
        }});

        PdfText result = extractor.extract(pdf);

        assertThat(result.hasTextLayer()).isTrue();
        assertThat(result.pageCount()).isEqualTo(1);
        assertThat(result.text())
                .contains("Rechnung Nr. RE-2041")
                .contains("Mühlenweg")      // umlauts survive
                .contains("1.428,00 EUR");
    }

    @Test
    void ordersTextByPositionNotByFileOrder() throws IOException {
        // The lower line is written into the file first; a reader expects the upper one first.
        byte[] pdf;
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                writeLine(content, "Second line of the invoice", 600);
                writeLine(content, "First line of the invoice", 700);
            }
            pdf = save(document);
        }

        String text = extractor.extract(pdf).text();

        assertThat(text.indexOf("First line")).isLessThan(text.indexOf("Second line"));
    }

    @Test
    void detectsScansWithoutTextLayer() throws IOException {
        // A page with only graphics, like a scanned image: no text at all.
        byte[] pdf;
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.addRect(50, 50, 400, 600);
                content.fill();
            }
            pdf = save(document);
        }

        PdfText result = extractor.extract(pdf);

        assertThat(result.hasTextLayer()).isFalse();
        assertThat(result.pageCount()).isEqualTo(1);
    }

    @Test
    void readsOnlyUpToTheConfiguredNumberOfPages() throws IOException {
        PdfTextExtractor limited = new PdfTextExtractor(2, 50_000);
        byte[] pdf = pdfWithPages(new String[][]{
                {"Page one with invoice header data"},
                {"Page two with the invoice lines"},
                {"Page three with general terms and conditions"}
        });

        PdfText result = limited.extract(pdf);

        assertThat(result.pageCount()).isEqualTo(3);
        assertThat(result.extractedPages()).isEqualTo(2);
        assertThat(result.text()).contains("Page two").doesNotContain("Page three");
    }

    @Test
    void cutsTextAtTheCharacterLimit() throws IOException {
        PdfTextExtractor limited = new PdfTextExtractor(20, 40);
        byte[] pdf = pdfWithPages(new String[][]{{
                "This invoice line is definitely longer than forty characters"
        }});

        assertThat(limited.extract(pdf).text()).hasSize(40);
    }

    @Test
    void rejectsFilesThatAreNotPdf() {
        byte[] notPdf = "<invoice>this is XML</invoice>".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> extractor.extract(notPdf))
                .isInstanceOf(PdfReadException.class);
    }

    @Test
    void normalizesWhitespace() {
        String raw = "Rechnung\r\nNr.   RE-2041   \r\n\r\n\r\n\r\nSumme:\u00A01.428,00";

        assertThat(PdfTextExtractor.normalize(raw))
                .isEqualTo("Rechnung\nNr. RE-2041\n\nSumme: 1.428,00");
    }
}