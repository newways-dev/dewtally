package com.kate.dewtally.invoice;

/**
 * How serious a validation issue is.
 */
public enum Severity {
    // The invoice cannot be processed
    ERROR,
    // Suspicious, a person has to look at it
    WARNING,
    // For information only, no action needed
    INFO
}