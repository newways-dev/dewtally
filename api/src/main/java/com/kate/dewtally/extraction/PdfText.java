package com.kate.dewtally.extraction;

/**
 * Text read from a PDF.
 *
 * @param text           normalised text of the extracted pages (may be cut at the character limit)
 * @param pageCount      number of pages in the file
 * @param extractedPages number of pages the text was taken from (limited by configuration)
 * @param hasTextLayer   false for scans: the PDF contains images of text, but no text itself
 */
public record PdfText(String text, int pageCount, int extractedPages, boolean hasTextLayer) {
}