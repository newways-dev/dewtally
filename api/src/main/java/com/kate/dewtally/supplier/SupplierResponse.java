package com.kate.dewtally.supplier;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SupplierResponse(
        UUID id,
        String name,
        String vatId,
        SupplierStatus status,
        String createdBy,
        Instant createdAt,
        List<IbanResponse> ibans
) {

    static SupplierResponse from(Supplier supplier) {
        return new SupplierResponse(
                supplier.getId(),
                supplier.getName(),
                supplier.getVatId(),
                supplier.getStatus(),
                supplier.getCreatedBy(),
                supplier.getCreatedAt(),
                supplier.getIbans().stream().map(IbanResponse::from).toList());
    }

    public record IbanResponse(
            String iban,
            IbanStatus status,
            String addedBy,
            Instant addedAt,
            String verifiedBy,
            Instant verifiedAt
    ) {
        static IbanResponse from(SupplierIban entry) {
            return new IbanResponse(entry.getIban(), entry.getStatus(), entry.getAddedBy(),
                    entry.getAddedAt(), entry.getVerifiedBy(), entry.getVerifiedAt());
        }
    }
}