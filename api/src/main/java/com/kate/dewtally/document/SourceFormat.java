package com.kate.dewtally.document;

/**
 * Syntax of an uploaded file, detected from its content (never from the file extension).
 */
public enum SourceFormat {
    // XML in UBL syntax (XRechnung UBL, Peppol BIS, plain EN 16931).
    UBL,
    // XML in UN/CEFACT CII syntax (XRechnung CII, plain EN 16931).
    CII,
    // PDF with an embedded CII invoice (ZUGFeRD / Factur-X).
    ZUGFERD,
    // PDF without structured invoice data.
    PDF,
    // Anything else.
    UNKNOWN
}