package com.kate.dewtally.validation;

import com.kate.dewtally.document.SourceFormat;
import com.kate.dewtally.invoice.ExtractionMethod;
import com.kate.dewtally.invoice.Invoice;
import com.kate.dewtally.invoice.InvoiceData;
import com.kate.dewtally.invoice.Severity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the real KoSIT validator with the real XRechnung configuration from kosit/xrechnung/.
 * Loading the configuration takes a few seconds, so it is done once for all tests.
 */
class KositRuleTest {

    private static final String XRECHNUNG_PROFILE =
            "urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0";

    private static KositRule rule;

    @BeforeAll
    static void loadConfiguration() {
        rule = new KositRule(true, "kosit/xrechnung/scenarios.xml");
    }

    private static ValidationContext context(byte[] content, String profile) {
        InvoiceData empty = new InvoiceData(null, null, null, null, null, null, null, null, null, null, null);
        Invoice invoice = new Invoice(UUID.randomUUID(), UUID.randomUUID(), ExtractionMethod.STRUCTURED, empty);
        return new ValidationContext(invoice, SourceFormat.UBL, profile, content);
    }

    private static byte[] resource(String name) throws IOException {
        try (InputStream in = KositRuleTest.class.getResourceAsStream("/invoices/" + name)) {
            if (in == null) {
                throw new IllegalStateException("Missing test resource /invoices/" + name);
            }
            return in.readAllBytes();
        }
    }

    @Test
    void acceptsAnOfficialSampleXRechnung() throws IOException {
        List<Finding> findings = rule.check(context(resource("official-xrechnung-ubl.xml"), XRECHNUNG_PROFILE));

        assertThat(findings).noneMatch(finding -> finding.severity() == Severity.ERROR);
    }

    @Test
    void findsWhatOurSimplifiedSampleIsMissing() throws IOException {
        // Our own sample passes dewtally's rules, but lacks fields XRechnung requires (e.g. seller contact).
        List<Finding> findings = rule.check(context(resource("xrechnung-ubl.xml"), XRECHNUNG_PROFILE));

        assertThat(findings).anyMatch(finding -> finding.severity() == Severity.ERROR);
    }

    @Test
    void skipsInvoicesThatAreNotXRechnung() throws IOException {
        List<Finding> findings = rule.check(context(resource("xrechnung-ubl.xml"), "urn:cen.eu:en16931:2017"));

        assertThat(findings).isEmpty();
    }

    @Test
    void doesNothingWhenDisabled() throws IOException {
        KositRule disabled = new KositRule(false, "does-not-exist.xml");

        assertThat(disabled.check(context(resource("xrechnung-ubl.xml"), XRECHNUNG_PROFILE))).isEmpty();
    }
}