package com.kate.dewtally.extraction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class PdfTextExtractorManualTest {

    @Test
    @EnabledIfSystemProperty(named = "pdf", matches = ".+")
    void printTextOfAGivenPdf() throws IOException {
        Path file = Path.of(System.getProperty("pdf"));
        PdfText result = new PdfTextExtractor(20, 50_000).extract(Files.readAllBytes(file));

        System.out.println("==================== " + file.getFileName() + " ====================");
        System.out.println("Pages: " + result.pageCount() + " (read: " + result.extractedPages() + ")");
        System.out.println("Text layer: " + (result.hasTextLayer() ? "yes" : "NO (probably a scan)"));
        System.out.println("Characters: " + result.text().length());
        System.out.println("------------------------------------------------------------");
        System.out.println(result.text());
        System.out.println("============================================================");
    }
}