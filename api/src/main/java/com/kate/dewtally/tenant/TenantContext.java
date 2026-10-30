package com.kate.dewtally.tenant;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The tenant the current code works for. Set for the duration of a block of code (an API request,
 * the processing of one document) and gone afterwards: a ScopedValue cannot leak into the next request.
 * The database connection takes it from here, and Row-Level Security filters every query by it.
 */
public final class TenantContext {

    private static final ScopedValue<UUID> CURRENT = ScopedValue.newInstance();

    private TenantContext() {
    }

    public static Optional<UUID> current() {
        return CURRENT.isBound() ? Optional.of(CURRENT.get()) : Optional.empty();
    }

    public static <T, X extends Throwable> T callAs(UUID tenantId, ScopedValue.CallableOp<? extends T, X> work) throws X {
        return ScopedValue.where(CURRENT, Objects.requireNonNull(tenantId)).call(work);
    }

    public static void runAs(UUID tenantId, Runnable work) {
        ScopedValue.where(CURRENT, Objects.requireNonNull(tenantId)).run(work);
    }
}