package com.kate.dewtally.supplier;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * One bank account of a supplier. Who added it and who verified it is kept for the audit trail.
 */
@Entity
@Table(name = "supplier_iban")
public class SupplierIban {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false, updatable = false)
    private Supplier supplier;

    @Column(name = "iban", nullable = false, updatable = false, length = 34)
    private String iban;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private IbanStatus status;

    @Column(name = "added_by", nullable = false, updatable = false, length = 100)
    private String addedBy;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    @Column(name = "verified_by", length = 100)
    private String verifiedBy;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    protected SupplierIban() {
        // Required by JPA.
    }

    // Package-private: IBANs are only added through Supplier.addIban().
    SupplierIban(Supplier supplier, String iban, String addedBy) {
        this.supplier = supplier;
        this.iban = iban;
        this.addedBy = addedBy;
        this.status = IbanStatus.PENDING;
        this.addedAt = Instant.now();
    }

    void verify(String user) {
        if (status == IbanStatus.VERIFIED) {
            throw new SupplierStateException("IBAN " + iban + " is already verified");
        }
        if (addedBy.equalsIgnoreCase(user)) {
            throw new SupplierStateException(
                    "Four-eyes principle: the person who added an IBAN cannot verify it");
        }
        this.status = IbanStatus.VERIFIED;
        this.verifiedBy = user;
        this.verifiedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getIban() {
        return iban;
    }

    public IbanStatus getStatus() {
        return status;
    }

    public String getAddedBy() {
        return addedBy;
    }

    public Instant getAddedAt() {
        return addedAt;
    }

    public String getVerifiedBy() {
        return verifiedBy;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }
}