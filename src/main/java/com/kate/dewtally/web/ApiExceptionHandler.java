package com.kate.dewtally.web;

import com.kate.dewtally.invoice.InvoiceStateException;
import com.kate.dewtally.storage.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every error of the API into an RFC 9457 problem response (application/problem+json).
 * <p>
 * The base class already handles Spring's own errors the same way: missing header, wrong UUID,
 * unknown enum value (400), file too large (413), ResponseStatusException (its status), ...
 * Here we only add our own exceptions and a safe fallback.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    // The action does not fit the invoice's status, e.g. accepting an INVALID invoice.
    @ExceptionHandler(InvoiceStateException.class)
    ProblemDetail handleInvoiceState(InvoiceStateException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    // Someone else changed the invoice in the meantime: reload and try again.
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleConcurrentChange(OptimisticLockingFailureException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The invoice was changed by someone else. Please reload it and try again.");
    }

    @ExceptionHandler(StorageException.class)
    ProblemDetail handleStorage(StorageException e) {
        log.error("Document storage failed", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "The document could not be stored. Please try again later.");
    }

    // Last line of defence: anything unexpected. The details go to the log, never to the client,
    // because exception messages can contain SQL, file paths or other internals.
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception e) {
        log.error("Unexpected error", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred.");
    }
}