package com.kate.dewtally.extraction;

import com.kate.dewtally.supplier.Iban;

import java.util.Map;

/**
 * Invoice text with sensitive values replaced by placeholders, plus what is needed to undo it.
 *
 * @param text  the text that may be sent to the language model
 * @param ibans placeholder → real IBAN, e.g. "[IBAN_1]" → "DE89370400440532013000"
 */
record MaskedText(String text, Map<String, String> ibans) {

    /**
     * Turns the model's IBAN answer back into the real IBAN. Only our placeholders are trusted:
     * every valid IBAN in the text was replaced, so a valid IBAN in the answer cannot come from
     * the invoice. It was invented or injected, and is dropped.
     */
    String restoreIban(String answer) {
        if (answer == null || answer.isBlank()) {
            return null;
        }
        String key = answer.strip();
        if (!key.startsWith("[")) {
            key = "[" + key + "]"; // the model sometimes drops the brackets
        }
        if (ibans.containsKey(key)) {
            return ibans.get(key);
        }
        // An invalid IBAN was never masked; keep it so validation reports DT-IBAN-INVALID.
        return Iban.isValid(answer) ? null : answer;
    }
}