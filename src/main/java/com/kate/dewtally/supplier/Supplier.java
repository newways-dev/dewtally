package com.kate.dewtally.supplier;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.*;

/**
 * A supplier a tenant buys from (Lieferantenstamm). Aggregate root for its bank accounts.
 */
@Entity
@Table(name = "supplier")
public class Supplier {

    @OneToMany(mappedBy = "supplier", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("addedAt ASC")
    private final List<SupplierIban> ibans = new ArrayList<>();
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "name", nullable = false, length = 300)
    private String name;
    @Column(name = "vat_id", length = 30)
    private String vatId;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SupplierStatus status;
    @Column(name = "created_by", nullable = false, updatable = false, length = 100)
    private String createdBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Supplier() {
        // Required by JPA.
    }

    public Supplier(UUID tenantId, String name, String vatId, String createdBy) {
        this.tenantId = tenantId;
        this.name = name;
        this.vatId = vatId;
        this.createdBy = createdBy;
        this.status = SupplierStatus.ACTIVE;
    }

    /**
     * A new bank account starts as PENDING: it is not trusted until a second person verifies it.
     */
    public void addIban(String iban, String user) {
        String normalized = Iban.normalize(iban);
        if (findIban(normalized).isPresent()) {
            throw new SupplierStateException("IBAN " + normalized + " is already known for this supplier");
        }
        ibans.add(new SupplierIban(this, normalized, user));
    }

    public void verifyIban(String iban, String user) {
        String normalized = Iban.normalize(iban);
        findIban(normalized)
                .orElseThrow(() -> new SupplierStateException("IBAN " + normalized + " is not known for this supplier"))
                .verify(user);
    }

    public void block() {
        this.status = SupplierStatus.BLOCKED;
    }

    public void unblock() {
        this.status = SupplierStatus.ACTIVE;
    }

    private Optional<SupplierIban> findIban(String normalized) {
        return ibans.stream().filter(entry -> entry.getIban().equals(normalized)).findFirst();
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getName() {
        return name;
    }

    public String getVatId() {
        return vatId;
    }

    public SupplierStatus getStatus() {
        return status;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<SupplierIban> getIbans() {
        return Collections.unmodifiableList(ibans);
    }
}