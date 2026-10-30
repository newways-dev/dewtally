package com.kate.dewtally.invoice;

/**
 * How the invoice data was obtained.
 */
public enum ExtractionMethod {
    // Parsed from structured XML (XRechnung, ZUGFeRD)
    STRUCTURED,
    // Extracted from an unstructured PDF by a language model
    LLM,
    // Entered or corrected by a person
    MANUAL
}