package com.kate.dewtally.document;

/**
 * Format of an uploaded invoice file, detected on upload.
 */
public enum SourceFormat {
    XRECHNUNG_UBL,
    XRECHNUNG_CII,
    ZUGFERD,
    PDF,
    UNKNOWN
}