package com.kate.dewtally.invoice;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * An invoice as it was issued (a snapshot, not a link to supplier master data).
 * Aggregate root: lines and validation issues are only changed through this class.
 */
@Entity
@Table(name = "invoice")
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    // Plain id instead of a JPA relation: documents and invoices are separate modules.
    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private InvoiceStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "extraction_method", nullable = false, length = 20)
    private ExtractionMethod extractionMethod;

    @Column(name = "invoice_number", length = 100)
    private String invoiceNumber;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "supplier_name", length = 300)
    private String supplierName;

    @Column(name = "supplier_vat_id", length = 30)
    private String supplierVatId;

    @Column(name = "buyer_reference", length = 100)
    private String buyerReference;

    @Column(name = "iban", length = 34)
    private String iban;

    // Money is always BigDecimal, never double.
    @Column(name = "net_amount", precision = 15, scale = 2)
    private BigDecimal netAmount;

    @Column(name = "tax_amount", precision = 15, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "gross_amount", precision = 15, scale = 2)
    private BigDecimal grossAmount;

    // Optimistic locking: two reviewers cannot silently overwrite each other's changes.
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<InvoiceLine> lines = new ArrayList<>();

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ValidationIssue> issues = new ArrayList<>();

    protected Invoice() {
        // Required by JPA.
    }

    public Invoice(UUID tenantId, UUID documentId, ExtractionMethod extractionMethod, InvoiceData data) {
        this.tenantId = tenantId;
        this.documentId = documentId;
        this.extractionMethod = extractionMethod;
        this.status = InvoiceStatus.RECEIVED;
        applyData(data);
    }

    public void addLine(InvoiceLine line) {
        line.attachTo(this);
        lines.add(line);
    }

    public void addIssue(Severity severity, String ruleId, String message) {
        issues.add(new ValidationIssue(this, severity, ruleId, message));
    }

    /**
     * Sets the status from the collected issues. Called once, after validation:
     * any ERROR makes the invoice INVALID, otherwise any WARNING sends it to review.
     */
    public void applyValidationResult() {
        if (status != InvoiceStatus.RECEIVED) {
            throw new IllegalStateException(
                    "Validation result can only be applied to a RECEIVED invoice, but status is " + status);
        }

        boolean hasError = issues.stream().anyMatch(issue -> issue.getSeverity() == Severity.ERROR);
        boolean hasWarning = issues.stream().anyMatch(issue -> issue.getSeverity() == Severity.WARNING);

        if (hasError) {
            status = InvoiceStatus.INVALID;
        } else if (hasWarning) {
            status = InvoiceStatus.NEEDS_REVIEW;
        } else {
            status = InvoiceStatus.VALID;
        }
    }

    private void applyData(InvoiceData data) {
        this.invoiceNumber = data.invoiceNumber();
        this.issueDate = data.issueDate();
        this.dueDate = data.dueDate();
        this.currency = data.currency();
        this.supplierName = data.supplierName();
        this.supplierVatId = data.supplierVatId();
        this.buyerReference = data.buyerReference();
        this.iban = data.iban();
        this.netAmount = data.netAmount();
        this.taxAmount = data.taxAmount();
        this.grossAmount = data.grossAmount();
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public ExtractionMethod getExtractionMethod() {
        return extractionMethod;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public String getCurrency() {
        return currency;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public String getSupplierVatId() {
        return supplierVatId;
    }

    public String getBuyerReference() {
        return buyerReference;
    }

    public String getIban() {
        return iban;
    }

    public BigDecimal getNetAmount() {
        return netAmount;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public BigDecimal getGrossAmount() {
        return grossAmount;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<InvoiceLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    public List<ValidationIssue> getIssues() {
        return Collections.unmodifiableList(issues);
    }
}