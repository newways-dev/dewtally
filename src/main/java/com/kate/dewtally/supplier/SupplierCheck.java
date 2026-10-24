package com.kate.dewtally.supplier;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * What invoice validation needs to know about a supplier. A plain snapshot, so the validation
 * module never touches supplier entities or their lazy collections.
 */
public record SupplierCheck(
        UUID id,
        String name,
        SupplierStatus status,
        Set<String> verifiedIbans,
        Set<String> pendingIbans
) {

    static SupplierCheck from(Supplier supplier) {
        return new SupplierCheck(
                supplier.getId(),
                supplier.getName(),
                supplier.getStatus(),
                ibansWithStatus(supplier, IbanStatus.VERIFIED),
                ibansWithStatus(supplier, IbanStatus.PENDING));
    }

    private static Set<String> ibansWithStatus(Supplier supplier, IbanStatus status) {
        return supplier.getIbans().stream()
                .filter(entry -> entry.getStatus() == status)
                .map(SupplierIban::getIban)
                .collect(Collectors.toUnmodifiableSet());
    }
}