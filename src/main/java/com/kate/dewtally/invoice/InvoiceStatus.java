package com.kate.dewtally.invoice;

/**
 * Lifecycle of an invoice:
 * RECEIVED -> VALID | INVALID | NEEDS_REVIEW -> ACCEPTED -> EXPORTED
 */
public enum InvoiceStatus {
    RECEIVED,
    VALID,
    INVALID,
    NEEDS_REVIEW,
    ACCEPTED,
    EXPORTED
}