package com.kate.dewtally.extraction;

/**
 * The file could not be read as a PDF (broken, not a PDF, or password protected).
 */
public class PdfReadException extends RuntimeException {

    public PdfReadException(String message, Throwable cause) {
        super(message, cause);
    }
}