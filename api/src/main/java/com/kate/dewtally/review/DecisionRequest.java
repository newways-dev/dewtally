package com.kate.dewtally.review;

/**
 * @param comment       comment for accepting (optional) or reason for rejecting (required)
 * @param ibanConfirmed when accepting an invoice with a changed IBAN: the reviewer confirms they
 *                      checked it with the supplier; the comment must then say how
 */
public record DecisionRequest(String comment, Boolean ibanConfirmed) {
}