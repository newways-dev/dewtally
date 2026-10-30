package com.kate.dewtally.invoice;

import java.util.Map;

/**
 * Number of invoices per status, for the summary on the invoice list. Every status is present, also with 0.
 */
public record InvoiceCountsResponse(Map<InvoiceStatus, Long> byStatus, long total) {
}