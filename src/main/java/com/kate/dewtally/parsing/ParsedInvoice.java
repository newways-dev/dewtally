package com.kate.dewtally.parsing;

import com.kate.dewtally.invoice.InvoiceData;

import java.util.List;

/**
 * Result of reading a structured e-invoice: header data and lines.
 * Nothing is validated here; missing or odd values are judged in the validation step.
 */
public record ParsedInvoice(InvoiceData data, List<ParsedLine> lines) {
}