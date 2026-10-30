package com.kate.dewtally.validation;

import com.kate.dewtally.document.SourceFormat;
import com.kate.dewtally.format.ZugferdAttachments;
import com.kate.dewtally.invoice.Severity;
import de.kosit.validationtool.api.Check;
import de.kosit.validationtool.api.Configuration;
import de.kosit.validationtool.api.InputFactory;
import de.kosit.validationtool.api.Result;
import de.kosit.validationtool.impl.DefaultCheck;
import de.kosit.validationtool.impl.xml.ProcessorProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Official XRechnung validation with the KoSIT validator: XML schema, EN 16931 rules (BR-...)
 * and German XRechnung rules (BR-DE-...). Only applied to invoices that declare an XRechnung profile.

 * Runs after dewtally's own rules
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class KositRule implements InvoiceRule {

    private static final Logger log = LoggerFactory.getLogger(KositRule.class);

    private static final int MAX_RULE_ID_LENGTH = 50;
    private static final int MAX_MESSAGE_LENGTH = 2000;

    private final Check check;

    public KositRule(@Value("${dewtally.validation.kosit.enabled:true}") boolean enabled,
                     @Value("${dewtally.validation.kosit.scenarios:kosit/xrechnung/scenarios.xml}") String scenarios) {
        this.check = enabled ? createCheck(Path.of(scenarios)) : null;
    }

    private static Check createCheck(Path scenarios) {
        if (!Files.exists(scenarios)) {
            throw new IllegalStateException("KoSIT scenarios not found at " + scenarios.toAbsolutePath()
                    + ". Download the XRechnung validator configuration into kosit/xrechnung/ (see README).");
        }
        log.info("Loading KoSIT validator configuration from {}", scenarios.toAbsolutePath());
        Configuration configuration = Configuration.load(scenarios.toAbsolutePath().toUri())
                .build(ProcessorProvider.getProcessor());
        return new DefaultCheck(configuration);
    }

    private static boolean isXRechnung(String profile) {
        return profile != null && profile.toLowerCase(Locale.ROOT).contains("xrechnung");
    }

    // KoSIT checks XML; for ZUGFeRD the XML is embedded in the PDF.
    private static Optional<byte[]> invoiceXml(ValidationContext context) {
        if (context.sourceFormat() != SourceFormat.ZUGFERD) {
            return Optional.of(context.content());
        }
        try {
            return ZugferdAttachments.extractInvoiceXml(context.content());
        } catch (IOException e) {
            log.warn("Could not extract XML from ZUGFeRD PDF for KoSIT validation: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static List<Finding> toFindings(Result result) {
        if (!result.isProcessingSuccessful()) {
            return List.of(Finding.error("KOSIT-PROCESSING",
                    limit("Die offizielle XRechnung-Prüfung konnte nicht durchgeführt werden: "
                            + String.join("; ", result.getProcessingErrors()), MAX_MESSAGE_LENGTH)));
        }

        List<Finding> findings = new ArrayList<>(messagesFromReport(result.getReportDocument()));

        // The validator's overall recommendation is the final word: a rejected invoice
        // must end up INVALID even if no single message could be read from the report.
        boolean hasError = findings.stream().anyMatch(finding -> finding.severity() == Severity.ERROR);
        if (!result.isAcceptable() && !hasError) {
            findings.add(Finding.error("KOSIT-REJECT",
                    "Die offizielle XRechnung-Prüfung empfiehlt, die Rechnung abzulehnen."));
        }
        return findings;
    }

    // The KoSIT report lists every problem as a <message level="error|warning|information" code="...">.
    private static List<Finding> messagesFromReport(Document report) {
        List<Finding> findings = new ArrayList<>();
        NodeList elements = report.getElementsByTagName("*");
        for (int i = 0; i < elements.getLength(); i++) {
            if (!(elements.item(i) instanceof Element element) || !isMessage(element)) {
                continue;
            }

            String level = element.getAttribute("level").toLowerCase(Locale.ROOT);
            Severity severity = switch (level) {
                case "error" -> Severity.ERROR;
                case "warning" -> Severity.WARNING;
                default -> null;
            };
            if (severity == null) {
                continue;
            }

            String code = element.getAttribute("code");
            String ruleId = code.isBlank() ? "KOSIT" : limit(code, MAX_RULE_ID_LENGTH);
            String message = limit(element.getTextContent().strip(), MAX_MESSAGE_LENGTH);
            findings.add(new Finding(severity, ruleId, message));
        }
        return findings;
    }

    private static boolean isMessage(Element element) {
        String name = element.getLocalName() != null ? element.getLocalName() : element.getNodeName();
        return name.equals("message") || name.endsWith(":message");
    }

    private static String limit(String value, int maxLength) {
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    @Override
    public List<Finding> check(ValidationContext context) {
        if (check == null || context.content() == null || !isXRechnung(context.profile())) {
            return List.of();
        }

        Optional<byte[]> xml = invoiceXml(context);
        if (xml.isEmpty()) {
            return List.of();
        }

        Result result = check.checkInput(InputFactory.read(xml.get(), "invoice.xml"));
        return toFindings(result);
    }
}