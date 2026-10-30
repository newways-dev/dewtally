package com.kate.dewtally.supplier;

public enum SupplierStatus {
    // Invoices from this supplier are expected
    ACTIVE,
    // Do not pay: e.g. contract ended, fraud suspicion
    BLOCKED
}