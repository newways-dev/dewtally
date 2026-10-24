package com.kate.dewtally.supplier;

/**
 * @param iban optional first bank account; it starts as PENDING like any other new IBAN
 */
public record CreateSupplierRequest(String name, String vatId, String iban) {
}