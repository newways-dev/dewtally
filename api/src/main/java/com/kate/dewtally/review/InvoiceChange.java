package com.kate.dewtally.review;

import com.kate.dewtally.invoice.FieldChange;
import com.kate.dewtally.invoice.InvoiceStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * One entry of an invoice's audit trail. Never updated or deleted: the history must stay
 * exactly as it happened (GoBD). Also, the source of "model said X, person corrected to Y" for evals.
 */
@Entity
@Table(name = "invoice_change")
public class InvoiceChange {

    private static final int MAX_VALUE_LENGTH = 2000;
    private static final int MAX_USER_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "invoice_id", nullable = false, updatable = false)
    private UUID invoiceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, updatable = false, length = 20)
    private ChangeType changeType;

    @Column(name = "field", updatable = false, length = 100)
    private String field;

    @Column(name = "old_value", updatable = false, length = MAX_VALUE_LENGTH)
    private String oldValue;

    @Column(name = "new_value", updatable = false, length = MAX_VALUE_LENGTH)
    private String newValue;

    @Column(name = "comment", updatable = false, length = MAX_VALUE_LENGTH)
    private String comment;

    @Column(name = "changed_by", nullable = false, updatable = false, length = MAX_USER_LENGTH)
    private String changedBy;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    protected InvoiceChange() {
        // Required by JPA.
    }

    private InvoiceChange(UUID tenantId, UUID invoiceId, ChangeType changeType, String field,
                          String oldValue, String newValue, String comment, String changedBy) {
        this.tenantId = tenantId;
        this.invoiceId = invoiceId;
        this.changeType = changeType;
        this.field = field;
        this.oldValue = limit(oldValue, MAX_VALUE_LENGTH);
        this.newValue = limit(newValue, MAX_VALUE_LENGTH);
        this.comment = limit(comment, MAX_VALUE_LENGTH);
        this.changedBy = limit(changedBy, MAX_USER_LENGTH);
        this.changedAt = Instant.now();
    }

    static InvoiceChange correction(UUID tenantId, UUID invoiceId, FieldChange change, String changedBy) {
        return new InvoiceChange(tenantId, invoiceId, ChangeType.CORRECTION, change.field(),
                change.oldValue(), change.newValue(), null, changedBy);
    }

    static InvoiceChange decision(UUID tenantId, UUID invoiceId, ChangeType type,
                                  InvoiceStatus from, InvoiceStatus to, String comment, String changedBy) {
        return new InvoiceChange(tenantId, invoiceId, type, "status",
                from.name(), to.name(), comment, changedBy);
    }

    private static String limit(String value, int maxLength) {
        return value != null && value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    public UUID getId() {
        return id;
    }

    public ChangeType getChangeType() {
        return changeType;
    }

    public String getField() {
        return field;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public String getComment() {
        return comment;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}