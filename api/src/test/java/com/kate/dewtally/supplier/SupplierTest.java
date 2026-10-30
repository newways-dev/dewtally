package com.kate.dewtally.supplier;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupplierTest {

    private final Supplier supplier = new Supplier(UUID.randomUUID(), "Muster GmbH", "DE123456789", "anna");

    @Test
    void newIbanIsPendingUntilASecondPersonVerifiesIt() {
        supplier.addIban("DE89 3704 0044 0532 0130 00", "anna");
        assertThat(supplier.getIbans().getFirst().getStatus()).isEqualTo(IbanStatus.PENDING);

        supplier.verifyIban("DE89370400440532013000", "ben");

        assertThat(supplier.getIbans().getFirst().getStatus()).isEqualTo(IbanStatus.VERIFIED);
        assertThat(supplier.getIbans().getFirst().getVerifiedBy()).isEqualTo("ben");
    }

    @Test
    void nobodyCanVerifyTheirOwnIban() {
        supplier.addIban("DE89370400440532013000", "anna");

        assertThatThrownBy(() -> supplier.verifyIban("DE89370400440532013000", "ANNA"))
                .isInstanceOf(SupplierStateException.class)
                .hasMessageContaining("Four-eyes");
    }

    @Test
    void sameIbanCannotBeAddedTwice() {
        supplier.addIban("DE89370400440532013000", "anna");

        assertThatThrownBy(() -> supplier.addIban("DE89 3704 0044 0532 0130 00", "ben"))
                .isInstanceOf(SupplierStateException.class);
    }
}