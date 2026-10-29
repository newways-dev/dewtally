package com.kate.dewtally.invoice;

/**
 * How many invoices of one tenant have a status; one row of a GROUP BY query.
 */
public record StatusCount(InvoiceStatus status, Long count) {
}