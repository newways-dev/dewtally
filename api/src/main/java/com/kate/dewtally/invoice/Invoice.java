package com.kate.dewtally.invoice;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * An invoice as it was issued (a snapshot, not a link to supplier master data).
 * Aggregate root: lines, validation issues and status are only changed through this class.
 */
@Entity
@Table(name = "invoice")
public class Invoice {

    // A decision (accept/reject) or a correction is only possible while the invoice is still open.
    private static final Set<InvoiceStatus> OPEN = Set.of(
            InvoiceStatus.VALID, InvoiceStatus.INVALID, InvoiceStatus.NEEDS_REVIEW);
    // Findings so dangerous that accepting needs an explicit confirmation, not just a click.
    private static final Set<String> HIGH_RISK_RULES = Set.of("DT-IBAN-CHANGED");
    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private final List<InvoiceLine> lines = new ArrayList<>();
    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<ValidationIssue> issues = new ArrayList<>();
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
    // The language model's own estimate (0.00 - 1.00); null if no model was involved.
    @Column(name = "extraction_confidence", precision = 3, scale = 2)
    private BigDecimal extractionConfidence;
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
    // Discounts and surcharges on the whole invoice (BT-107, BT-108); null if there are none.
    @Column(name = "allowance_total", precision = 15, scale = 2)
    private BigDecimal allowanceTotal;
    @Column(name = "charge_total", precision = 15, scale = 2)
    private BigDecimal chargeTotal;
    // Optimistic locking: two reviewers cannot silently overwrite each other's changes.
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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

    private static void track(List<FieldChange> changes, String field, Object oldValue, Object newValue) {
        if (!same(oldValue, newValue)) {
            changes.add(new FieldChange(field, format(oldValue), format(newValue)));
        }
    }

    // 1200.00 and 1200 are the same amount, even though BigDecimal.equals says otherwise.
    private static boolean same(Object a, Object b) {
        if (a instanceof BigDecimal x && b instanceof BigDecimal y) {
            return x.compareTo(y) == 0;
        }
        return Objects.equals(a, b);
    }

    private static String format(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof BigDecimal amount ? amount.toPlainString() : value.toString();
    }

    public void addLine(InvoiceLine line) {
        line.attachTo(this);
        lines.add(line);
    }

    public void addIssue(Severity severity, String ruleId, String message) {
        issues.add(new ValidationIssue(this, severity, ruleId, message));
    }

    // Package-private: set by InvoiceService when the invoice is created from a model's answer.
    void recordExtractionConfidence(BigDecimal confidence) {
        this.extractionConfidence = confidence;
    }

    /**
     * Sets the status from the collected issues. Called once, after validation:
     * any ERROR makes the invoice INVALID, otherwise any WARNING sends it to review.
     * Exception: data read by a language model may itself be wrong. An error then may be
     * our extraction mistake, not the supplier's, so a person decides instead of rejecting it.
     */
    public void applyValidationResult() {
        if (status != InvoiceStatus.RECEIVED) {
            throw new IllegalStateException(
                    "Validation result can only be applied to a RECEIVED invoice, but status is " + status);
        }

        boolean hasError = issues.stream().anyMatch(issue -> issue.getSeverity() == Severity.ERROR);
        boolean hasWarning = issues.stream().anyMatch(issue -> issue.getSeverity() == Severity.WARNING);

        if (hasError) {
            status = extractionMethod == ExtractionMethod.LLM ? InvoiceStatus.NEEDS_REVIEW : InvoiceStatus.INVALID;
        } else if (hasWarning) {
            status = InvoiceStatus.NEEDS_REVIEW;
        } else {
            status = InvoiceStatus.VALID;
        }
    }

    /**
     * A person replaces the invoice data. Returns every value that actually changed, for the audit trail.
     * If anything changed, the data counts as entered by a person (MANUAL), old issues are removed
     * and the invoice is back in RECEIVED, ready to be validated again.
     */
    public List<FieldChange> correct(InvoiceData data, List<InvoiceLineData> newLines) {
        requireOpen("corrected");

        List<FieldChange> changes = new ArrayList<>();
        track(changes, "invoiceNumber", invoiceNumber, data.invoiceNumber());
        track(changes, "issueDate", issueDate, data.issueDate());
        track(changes, "dueDate", dueDate, data.dueDate());
        track(changes, "currency", currency, data.currency());
        track(changes, "supplierName", supplierName, data.supplierName());
        track(changes, "supplierVatId", supplierVatId, data.supplierVatId());
        track(changes, "buyerReference", buyerReference, data.buyerReference());
        track(changes, "iban", iban, data.iban());
        track(changes, "netAmount", netAmount, data.netAmount());
        track(changes, "taxAmount", taxAmount, data.taxAmount());
        track(changes, "grossAmount", grossAmount, data.grossAmount());
        track(changes, "allowanceTotal", allowanceTotal, data.allowanceTotal());
        track(changes, "chargeTotal", chargeTotal, data.chargeTotal());
        changes.addAll(correctLines(newLines));

        if (changes.isEmpty()) {
            return changes;
        }

        applyData(data);
        if (extractionMethod != ExtractionMethod.MANUAL) {
            changes.add(new FieldChange("extractionMethod", extractionMethod.name(), ExtractionMethod.MANUAL.name()));
            extractionMethod = ExtractionMethod.MANUAL;
        }
        issues.clear();
        status = InvoiceStatus.RECEIVED;
        return changes;
    }

    public void accept(boolean highRiskConfirmed) {
        if (status != InvoiceStatus.VALID && status != InvoiceStatus.NEEDS_REVIEW) {
            throw new InvoiceStateException("Only VALID or NEEDS_REVIEW invoices can be accepted, status is " + status);
        }
        boolean highRisk = issues.stream().anyMatch(issue -> HIGH_RISK_RULES.contains(issue.getRuleId()));
        if (highRisk && !highRiskConfirmed) {
            throw new InvoiceStateException(
                    "The bank account differs from the known ones. Confirm it with the supplier before accepting.");
        }
        status = InvoiceStatus.ACCEPTED;
    }

    /**
     * A reviewer decides the invoice must not be paid (e.g. wrong, duplicate, not ours).
     */
    public void reject() {
        requireOpen("rejected");
        status = InvoiceStatus.REJECTED;
    }

    private void requireOpen(String action) {
        if (!OPEN.contains(status)) {
            throw new InvoiceStateException("Invoice in status " + status + " cannot be " + action);
        }
    }

    // Lines are updated in place by position; extra lines are added or removed at the end.
    private List<FieldChange> correctLines(List<InvoiceLineData> newLines) {
        List<FieldChange> changes = new ArrayList<>();
        int common = Math.min(lines.size(), newLines.size());

        for (int i = 0; i < common; i++) {
            InvoiceLine line = lines.get(i);
            InvoiceLineData data = newLines.get(i);
            String prefix = "lines[" + line.getLineNo() + "].";
            track(changes, prefix + "description", line.getDescription(), data.description());
            track(changes, prefix + "quantity", line.getQuantity(), data.quantity());
            track(changes, prefix + "unitCode", line.getUnitCode(), data.unitCode());
            track(changes, prefix + "unitPrice", line.getUnitPrice(), data.unitPrice());
            track(changes, prefix + "netAmount", line.getNetAmount(), data.netAmount());
            track(changes, prefix + "taxRate", line.getTaxRate(), data.taxRate());
            line.update(data);
        }

        for (int i = lines.size() - 1; i >= newLines.size(); i--) {
            InvoiceLine removed = lines.remove(i);
            changes.add(new FieldChange("lines[" + removed.getLineNo() + "]", "removed line", null));
        }

        int nextLineNo = lines.stream().mapToInt(InvoiceLine::getLineNo).max().orElse(0) + 1;
        for (int i = common; i < newLines.size(); i++) {
            InvoiceLineData data = newLines.get(i);
            addLine(data.toLine(nextLineNo));
            changes.add(new FieldChange("lines[" + nextLineNo + "]", null, data.summary()));
            nextLineNo++;
        }
        return changes;
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
        this.allowanceTotal = data.allowanceTotal();
        this.chargeTotal = data.chargeTotal();
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

    public BigDecimal getExtractionConfidence() {
        return extractionConfidence;
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

    public BigDecimal getAllowanceTotal() {
        return allowanceTotal;
    }

    public BigDecimal getChargeTotal() {
        return chargeTotal;
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