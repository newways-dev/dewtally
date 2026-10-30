package com.kate.dewtally.invoice;

/**
 * The requested action is not allowed in the invoice's current status,
 * e.g. accepting an INVALID invoice or correcting an ACCEPTED one.
 */
public class InvoiceStateException extends RuntimeException {

    public InvoiceStateException(String message) {
        super(message);
    }
}