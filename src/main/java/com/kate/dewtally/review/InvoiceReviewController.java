package com.kate.dewtally.review;

import com.kate.dewtally.invoice.InvoiceDetailsResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Review actions on an invoice.
 * Tenant and user come from headers for now
 */
@RestController
@RequestMapping("/api/invoices/{id}")
public class InvoiceReviewController {

    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String USER_HEADER = "X-User-Name";

    private final InvoiceReviewService reviews;

    public InvoiceReviewController(InvoiceReviewService reviews) {
        this.reviews = reviews;
    }

    @PutMapping("/data")
    public InvoiceDetailsResponse correct(@RequestHeader(TENANT_HEADER) UUID tenantId,
                                          @RequestHeader(value = USER_HEADER, defaultValue = "unknown") String user,
                                          @PathVariable UUID id,
                                          @RequestBody CorrectInvoiceRequest request) {
        if (request.version() == null || request.data() == null || request.lines() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "version, data and lines are required");
        }
        return reviews.correct(tenantId, id, request.version(), request.data(), request.lines(), user);
    }

    @PostMapping("/accept")
    public InvoiceDetailsResponse accept(@RequestHeader(TENANT_HEADER) UUID tenantId,
                                         @RequestHeader(value = USER_HEADER, defaultValue = "unknown") String user,
                                         @PathVariable UUID id,
                                         @RequestBody(required = false) DecisionRequest request) {
        String comment = request == null ? null : request.comment();
        boolean ibanConfirmed = request != null && Boolean.TRUE.equals(request.ibanConfirmed());
        if (ibanConfirmed && (comment == null || comment.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Describe how the IBAN was confirmed (who was called, on which known phone number)");
        }
        return reviews.accept(tenantId, id, comment, ibanConfirmed, user);
    }

    @PostMapping("/reject")
    public InvoiceDetailsResponse reject(@RequestHeader(TENANT_HEADER) UUID tenantId,
                                         @RequestHeader(value = USER_HEADER, defaultValue = "unknown") String user,
                                         @PathVariable UUID id,
                                         @RequestBody(required = false) DecisionRequest request) {
        if (request == null || request.comment() == null || request.comment().isBlank()) {
            // A rejection is sent back to the supplier, so it always needs a reason.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A reason is required to reject an invoice");
        }
        return reviews.reject(tenantId, id, request.comment().strip(), user);
    }

    @GetMapping("/changes")
    public List<InvoiceChangeResponse> history(@RequestHeader(TENANT_HEADER) UUID tenantId,
                                               @PathVariable UUID id) {
        return reviews.history(tenantId, id);
    }
}