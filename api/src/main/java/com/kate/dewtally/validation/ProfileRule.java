package com.kate.dewtally.validation;

import com.kate.dewtally.document.SourceFormat;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * ZUGFeRD / Factur-X profiles MINIMUM and BASIC WL do not contain all data required by EN 16931.
 * Under German law such invoices are not e-invoices but "sonstige Rechnungen".
 */
@Component
public class ProfileRule implements InvoiceRule {
    private static final List<String> INSUFFICIENT_PROFILE_SUFFIXES = List.of(":minimum", ":basicwl");

    @Override
    public List<Finding> check(ValidationContext context) {
        if (context.sourceFormat() != SourceFormat.ZUGFERD || context.profile() == null) {
            return List.of();
        }

        String profile = context.profile().toLowerCase(Locale.ROOT);
        boolean insufficient = INSUFFICIENT_PROFILE_SUFFIXES.stream().anyMatch(profile::endsWith);
        if (insufficient) {
            return List.of(Finding.warning("DT-NOT-AN-E-INVOICE",
                    "ZUGFeRD-Profil %s erfüllt nicht EN 16931 und gilt nach deutschem Recht nicht als E-Rechnung."
                            .formatted(context.profile())
                            + "Bitte beim Lieferanten eine vollständige E-Rechnung anfordern."));
        }
        return List.of();
    }
}