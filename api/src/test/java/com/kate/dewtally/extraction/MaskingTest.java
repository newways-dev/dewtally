package com.kate.dewtally.extraction;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MaskingTest {

    @Test
    void ibanWithSpacesIsReplacedAndRestored() {
        MaskedText masked = Masking.mask("IBAN: DE89 3704 0044 0532 0130 00 BIC: COBADEFFXXX");

        assertThat(masked.text()).isEqualTo("IBAN: [IBAN_1] BIC: COBADEFFXXX");
        assertThat(masked.restoreIban("[IBAN_1]")).isEqualTo("DE89370400440532013000");
        assertThat(masked.restoreIban("IBAN_1")).isEqualTo("DE89370400440532013000");
    }

    @Test
    void sameIbanGetsSamePlaceholderAndDifferentOnesAreNumbered() {
        MaskedText masked = Masking.mask(
                "DE89370400440532013000 / DE89 3704 0044 0532 0130 00 / DE02120300000000202051");

        assertThat(masked.text()).isEqualTo("[IBAN_1] / [IBAN_1] / [IBAN_2]");
    }

    @Test
    void amountsDatesAndNumbersStayUntouched() {
        String text = "Rechnung Nr. RE-2027-0042 vom 14.03.2027, USt-IdNr. DE123456789, Gesamtbetrag 1.428,00 EUR";

        assertThat(Masking.mask(text).text()).isEqualTo(text);
    }

    @Test
    void emailAndLabelledPhoneAreMasked() {
        MaskedText masked = Masking.mask("Tel.: +49 40 123456-78, E-Mail: buchhaltung@muster.de");

        assertThat(masked.text()).isEqualTo("Tel.: [PHONE], E-Mail: [EMAIL]");
    }

    @Test
    void invalidIbanIsNotMaskedAndStaysForValidation() {
        MaskedText masked = Masking.mask("IBAN DE491700000122001632");

        assertThat(masked.ibans()).isEmpty();
        assertThat(masked.restoreIban("DE491700000122001632")).isEqualTo("DE491700000122001632");
    }

    @Test
    void validIbanThatWasNotInTheDocumentIsDropped() {
        MaskedText masked = Masking.mask("IBAN: [IBAN_1]-free text without any bank account");

        // e.g. a prompt injection made the model return an attacker's account
        assertThat(masked.restoreIban("DE02120300000000202051")).isNull();
    }
}