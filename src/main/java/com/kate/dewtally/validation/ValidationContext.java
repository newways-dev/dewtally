package com.kate.dewtally.validation;

import com.kate.dewtally.document.SourceFormat;
import com.kate.dewtally.invoice.Invoice;

public record ValidationContext(Invoice invoice, SourceFormat sourceFormat, String profile) {
}