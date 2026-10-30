package com.kate.dewtally.review;

import com.kate.dewtally.invoice.InvoiceData;
import com.kate.dewtally.invoice.InvoiceLineData;

import java.util.List;

/**
 * The complete corrected invoice, as a review form would send it.
 * The server finds out itself which values changed.
 *
 * @param version the invoice version the reviewer saw; protects against overwriting someone else's change
 */
public record CorrectInvoiceRequest(Long version, InvoiceData data, List<InvoiceLineData> lines) {
}