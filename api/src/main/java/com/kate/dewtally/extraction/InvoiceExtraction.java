package com.kate.dewtally.extraction;

import com.kate.dewtally.parsing.ParsedInvoice;

/**
 * Result of extracting an invoice with a language model.
 *
 * @param invoice    data in the same structure the XML parsers produce
 * @param confidence the model's own estimate (0..1); a hint only, calibrated later with evals
 */
public record InvoiceExtraction(ParsedInvoice invoice, double confidence) {
}