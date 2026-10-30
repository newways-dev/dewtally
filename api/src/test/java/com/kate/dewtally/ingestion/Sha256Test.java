package com.kate.dewtally.ingestion;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class Sha256Test {

    @Test
    void matchesTheOfficialTestVector() {
        // Known SHA-256 of "abc" from the NIST specification.
        assertThat(Sha256.hex("abc".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void differentContentGivesDifferentHashes() {
        assertThat(Sha256.hex("RE-2041".getBytes(StandardCharsets.UTF_8)))
                .isNotEqualTo(Sha256.hex("RE-2042".getBytes(StandardCharsets.UTF_8)));
    }
}