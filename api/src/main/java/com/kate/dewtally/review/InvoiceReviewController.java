package com.kate.dewtally.review;

import com.kate.dewtally.invoice.InvoiceDetailsResponse;
import com.kate.dewtally.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Review actions on an invoice. Company and user name come from the token, so the change history
 * always shows who really did what.
 */
@RestController
@RequestMapping("/api/invoices/{id}")
public class InvoiceReviewController {

    private final InvoiceReviewService reviews;

    public InvoiceReviewController(InvoiceReviewService reviews) {
        this.reviews = reviews;
    }

    @PutMapping("/data")
    public InvoiceDetailsResponse correct(CurrentUser user,
                                          @PathVariable UUID id,
                                          @RequestBody CorrectInvoiceRequest request) {
        if (request.version() == null || request.data() == null || request.lines() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "version, data and lines are required");
        }
        return reviews.correct(user.tenantId(), id, request.version(), request.data(), request.lines(), user.name());
    }

    @PostMapping("/accept")
    public InvoiceDetailsResponse accept(CurrentUser user,
                                         @PathVariable UUID id,
                                         @RequestBody(required = false) DecisionRequest request) {
        String comment = request == null ? null : request.comment();
        boolean ibanConfirmed = request != null && Boolean.TRUE.equals(request.ibanConfirmed());
        if (ibanConfirmed && (comment == null || comment.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Describe how the IBAN was confirmed (who was called, on which known phone number)");
        }
        return reviews.accept(user.tenantId(), id, comment, ibanConfirmed, user.name());
    }

    @PostMapping("/reject")
    public InvoiceDetailsResponse reject(CurrentUser user,
                                         @PathVariable UUID id,
                                         @RequestBody(required = false) DecisionRequest request) {
        if (request == null || request.comment() == null || request.comment().isBlank()) {
            // A rejection is sent back to the supplier, so it always needs a reason.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A reason is required to reject an invoice");
        }
        return reviews.reject(user.tenantId(), id, request.comment().strip(), user.name());
    }

    @GetMapping("/changes")
    public List<InvoiceChangeResponse> history(CurrentUser user, @PathVariable UUID id) {
        return reviews.history(user.tenantId(), id);
    }
}