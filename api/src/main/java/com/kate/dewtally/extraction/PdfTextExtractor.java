package com.kate.dewtally.extraction;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class PdfTextExtractor {
    static final int MIN_TEXT_CHARS = 30;
    private final int maxPages;
    private final int maxChars;

    public PdfTextExtractor(@Value("${dewtally.extraction.pdf.max-pages:20}") int maxPages,
                            @Value("${dewtally.extraction.pdf.max-chars:50000}") int maxChars) {
        this.maxPages = maxPages;
        this.maxChars = maxChars;
    }

    // Unifies line breaks and spaces and removes empty noise, so the model gets compact, clean text.
    static String normalize(String raw) {
        return raw
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\u00A0', ' ')
                .replaceAll("[ \\t]+\n", "\n")
                .replaceAll("[ \\t]{2,}", " ")
                .replaceAll("\n{3,}", "\n\n")
                .strip();
    }

    private static long visibleChars(String text) {
        return text.chars().filter(c -> !Character.isWhitespace(c)).count();
    }

    public PdfText extract(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            int pageCount = document.getNumberOfPages();
            int lastPage = Math.min(pageCount, maxPages);

            PDFTextStripper stripper = new PDFTextStripper();
            // Order text by its position on the page (top to bottom, left to right),
            // not by the order it was written into the file. Important for tables and columns.
            stripper.setSortByPosition(true);
            stripper.setStartPage(1);
            stripper.setEndPage(lastPage);

            String text = normalize(stripper.getText(document));
            boolean hasTextLayer = visibleChars(text) >= MIN_TEXT_CHARS;
            if (text.length() > maxChars) {
                text = text.substring(0, maxChars);
            }
            return new PdfText(text, pageCount, lastPage, hasTextLayer);
        } catch (InvalidPasswordException e) {
            throw new PdfReadException("The PDF is password protected", e);
        } catch (IOException e) {
            throw new PdfReadException("The file is not a readable PDF", e);
        }
    }
}