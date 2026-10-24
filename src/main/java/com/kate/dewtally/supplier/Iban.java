package com.kate.dewtally.supplier;

import java.math.BigInteger;
import java.util.Locale;

public final class Iban {

    private static final BigInteger NINETY_SEVEN = BigInteger.valueOf(97);

    private Iban() {
    }

    /**
     * "de89 3704 0044 0532 0130 00" → "DE89370400440532013000"; blank → null.
     */
    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
    }

    public static boolean isValid(String value) {
        String iban = normalize(value);
        if (iban == null || !iban.matches("[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}")) {
            return false;
        }
        // Move country code and check digits to the end, turn letters into numbers (A=10 … Z=35).
        String rearranged = iban.substring(4) + iban.substring(0, 4);
        StringBuilder digits = new StringBuilder();
        for (char c : rearranged.toCharArray()) {
            digits.append(Character.getNumericValue(c));
        }
        return new BigInteger(digits.toString()).mod(NINETY_SEVEN).intValue() == 1;
    }
}