package com.kate.dewtally.validation;

import com.kate.dewtally.document.SourceFormat;
import com.kate.dewtally.invoice.Invoice;

public record ValidationContext(Invoice invoice, SourceFormat sourceFormat, String profile, byte[] content) {

    /**
     * For rules and tests that do not need the original file.
     */
    public ValidationContext(Invoice invoice, SourceFormat sourceFormat, String profile) {
        this(invoice, sourceFormat, profile, null);
    }
}