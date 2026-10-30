package com.kate.dewtally.extraction;

import com.kate.dewtally.supplier.Iban;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Removes data the language model does not need before invoice text leaves our system
 * (data minimisation, DSGVO Art. 5). IBANs become placeholders and are put back by us afterwards,
 * so the model never sees a real bank account and cannot change one.
 */
final class Masking {

    // Country code, check digits, then 11-30 letters or digits, optionally in groups split by single spaces.
    private static final Pattern IBAN_CANDIDATE = Pattern.compile("\\b[A-Z]{2}\\d{2}(?: ?[A-Z0-9]){11,30}");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+");
    // Only numbers right after a phone label: unlabelled digits may be amounts, dates or invoice numbers.
    private static final Pattern PHONE = Pattern.compile(
            "(?i)\\b(tel\\.?|telefon|fax|mobil|handy|phone)(\\s*:?\\s*)\\+?[\\d(][\\d\\s/().-]{4,}\\d");

    private Masking() {
    }

    static MaskedText mask(String text) {
        if (text == null) {
            return new MaskedText(null, Map.of());
        }

        Map<String, String> ibans = new LinkedHashMap<>();
        StringBuilder out = new StringBuilder();
        Matcher candidate = IBAN_CANDIDATE.matcher(text);
        int copied = 0;
        int searchFrom = 0;
        while (searchFrom < text.length() && candidate.find(searchFrom)) {
            int start = candidate.start();
            int end = validEnd(text, start, candidate.end());
            if (end < 0) {
                searchFrom = start + 1; // not an IBAN (or a fake one): leave it as it is
                continue;
            }
            out.append(text, copied, start).append(tokenFor(ibans, Iban.normalize(text.substring(start, end))));
            copied = end;
            searchFrom = end;
        }
        out.append(text.substring(copied));

        String masked = EMAIL.matcher(out).replaceAll("[EMAIL]");
        masked = PHONE.matcher(masked).replaceAll("$1$2[PHONE]");
        return new MaskedText(masked, Map.copyOf(ibans));
    }

    /**
     * The candidate may have swallowed the next word ("DE89 … 00 BIC"). Returns the longest part
     * that ends at a word boundary and passes the IBAN checksum, or -1 if there is none.
     */
    private static int validEnd(String text, int start, int end) {
        for (int e = end; e > start; e--) {
            if (text.charAt(e - 1) == ' ') {
                continue;
            }
            if (e < text.length() && Character.isLetterOrDigit(text.charAt(e))) {
                continue;
            }
            if (Iban.isValid(text.substring(start, e))) {
                return e;
            }
        }
        return -1;
    }

    // The same IBAN written twice gets the same placeholder.
    private static String tokenFor(Map<String, String> ibans, String iban) {
        for (Map.Entry<String, String> entry : ibans.entrySet()) {
            if (entry.getValue().equals(iban)) {
                return entry.getKey();
            }
        }
        String token = "[IBAN_" + (ibans.size() + 1) + "]";
        ibans.put(token, iban);
        return token;
    }
}