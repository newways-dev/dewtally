package com.kate.dewtally.supplier;

/**
 * The requested change is not allowed, e.g. verifying your own IBAN or a duplicate VAT ID.
 */
public class SupplierStateException extends RuntimeException {

    public SupplierStateException(String message) {
        super(message);
    }
}