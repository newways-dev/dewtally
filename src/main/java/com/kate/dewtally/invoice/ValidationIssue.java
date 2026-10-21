package com.kate.dewtally.invoice;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Something wrong or suspicious about an invoice, with the rule that found it,
 */
@Entity
@Table(name = "validation_issue")
public class ValidationIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false, updatable = false)
    private Invoice invoice;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 10)
    private Severity severity;

    @Column(name = "rule_id", nullable = false, length = 50)
    private String ruleId;

    @Column(name = "message", nullable = false, length = 2000)
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ValidationIssue() {
        // Required by JPA.
    }

    // Package-private: issues are only created through Invoice.addIssue().
    ValidationIssue(Invoice invoice, Severity severity, String ruleId, String message) {
        this.invoice = invoice;
        this.severity = severity;
        this.ruleId = ruleId;
        this.message = message;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getRuleId() {
        return ruleId;
    }

    public String getMessage() {
        return message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}