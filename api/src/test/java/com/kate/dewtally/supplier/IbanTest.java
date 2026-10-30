package com.kate.dewtally.supplier;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IbanTest {

    @Test
    void acceptsValidIbansWithOrWithoutSpaces() {
        assertThat(Iban.isValid("DE89370400440532013000")).isTrue();
        assertThat(Iban.isValid("de89 3704 0044 0532 0130 00")).isTrue();
        assertThat(Iban.isValid("GB82WEST12345698765432")).isTrue();
    }

    @Test
    void rejectsWrongChecksumAndGarbage() {
        assertThat(Iban.isValid("DE89370400440532013001")).isFalse(); // one digit off
        assertThat(Iban.isValid("XX")).isFalse();
        assertThat(Iban.isValid("")).isFalse();
        assertThat(Iban.isValid(null)).isFalse();
    }

    @Test
    void normalizesForComparison() {
        assertThat(Iban.normalize(" de89 3704 0044 0532 0130 00 ")).isEqualTo("DE89370400440532013000");
        assertThat(Iban.normalize("  ")).isNull();
    }
}