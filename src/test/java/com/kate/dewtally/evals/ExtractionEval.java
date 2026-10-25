package com.kate.dewtally.evals;

import com.kate.dewtally.TestcontainersConfiguration;
import com.kate.dewtally.document.SourceFormat;
import com.kate.dewtally.extraction.InvoiceExtraction;
import com.kate.dewtally.extraction.LlmInvoiceExtractor;
import com.kate.dewtally.extraction.PdfText;
import com.kate.dewtally.extraction.PdfTextExtractor;
import com.kate.dewtally.invoice.InvoiceData;
import com.kate.dewtally.parsing.InvoiceParser;
import com.kate.dewtally.parsing.ParsedInvoice;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Measures how well the language model extracts invoice data.
 * ZUGFeRD PDFs carry the correct answer in their embedded XML. The model only gets the visible text;
 * its answer is compared with the XML field by field. Costs money and is not deterministic,
 * so it is not part of the normal build:
 * {@code .\mvnw.cmd test -Dtest=ExtractionEval -Dai=true}
 */
@SpringBootTest(properties = "dewtally.validation.kosit.enabled=false")
@Import(TestcontainersConfiguration.class)
@EnabledIfSystemProperty(named = "ai", matches = "true")
class ExtractionEval {

    private static final Path INVOICES = Path.of("evals", "zugferd");
    private static final Path REPORT = Path.of("target", "evals", "extraction-report.md");

    @Autowired
    private InvoiceParser parser;
    @Autowired
    private PdfTextExtractor pdfTextExtractor;
    @Autowired
    private LlmInvoiceExtractor extractor;

    @Value("${spring.ai.openai.chat.model}")
    private String model;
    @Value("${dewtally.extraction.min-confidence}")
    private double minConfidence;

    // ---------- comparison ----------

    static List<FieldResult> compare(ParsedInvoice truth, ParsedInvoice actual) {
        InvoiceData expected = truth.data();
        InvoiceData got = actual.data();
        List<FieldResult> results = new ArrayList<>();

        text(results, "invoiceNumber", expected.invoiceNumber(), got.invoiceNumber());
        exact(results, "issueDate", expected.issueDate(), got.issueDate());
        exact(results, "dueDate", expected.dueDate(), got.dueDate());
        text(results, "currency", expected.currency(), got.currency());
        name(results, "supplierName", expected.supplierName(), got.supplierName());
        text(results, "supplierVatId", expected.supplierVatId(), got.supplierVatId());
        text(results, "iban", expected.iban(), got.iban());
        amount(results, "netAmount", expected.netAmount(), got.netAmount());
        amount(results, "taxAmount", expected.taxAmount(), got.taxAmount());
        amount(results, "grossAmount", expected.grossAmount(), got.grossAmount());
        optionalAmount(results, "allowanceTotal", expected.allowanceTotal(), got.allowanceTotal());
        optionalAmount(results, "chargeTotal", expected.chargeTotal(), got.chargeTotal());
        // Some profiles (BASIC WL, MINIMUM) have no lines in the XML even if the PDF shows them.
        if (!truth.lines().isEmpty()) {
            exact(results, "lineCount", truth.lines().size(), actual.lines().size());
        }
        return results;
    }

    // A field missing in the XML cannot be judged, so it is not counted.
    private static void exact(List<FieldResult> results, String field, Object expected, Object actual) {
        if (expected == null) {
            return;
        }
        results.add(new FieldResult(field, expected.equals(actual), show(expected), show(actual)));
    }

    // Ignores case, spaces and punctuation: "Lieferant GmbH" equals "LIEFERANT GMBH."
    private static void text(List<FieldResult> results, String field, String expected, String actual) {
        if (expected == null) {
            return;
        }
        boolean correct = Objects.equals(normalize(expected), normalize(actual));
        results.add(new FieldResult(field, correct, expected, show(actual)));
    }

    // The model may return the full legal name ("Au bon moulin SARL" for "Au bon moulin"): that is fine.
    // Dropping part of the name is not, so only "actual contains expected" counts.
    private static void name(List<FieldResult> results, String field, String expected, String actual) {
        if (expected == null) {
            return;
        }
        String e = normalize(expected);
        String a = normalize(actual);
        boolean correct = a != null && a.contains(e);
        results.add(new FieldResult(field, correct, expected, show(actual)));
    }

    private static void amount(List<FieldResult> results, String field, BigDecimal expected, BigDecimal actual) {
        if (expected == null) {
            return;
        }
        boolean correct = actual != null && expected.compareTo(actual) == 0;
        results.add(new FieldResult(field, correct, expected.toPlainString(), show(actual)));
    }

    // Allowances and charges: "none" and 0 are the same. Counted only if one side has a value,
    // so an invented discount (e.g. Skonto taken as a discount) is a mistake.
    private static void optionalAmount(List<FieldResult> results, String field,
                                       BigDecimal expected, BigDecimal actual) {
        BigDecimal e = expected == null ? BigDecimal.ZERO : expected;
        BigDecimal a = actual == null ? BigDecimal.ZERO : actual;
        if (e.signum() == 0 && a.signum() == 0) {
            return;
        }
        results.add(new FieldResult(field, e.compareTo(a) == 0, e.toPlainString(), a.toPlainString()));
    }

    private static String normalize(String value) {
        return value == null ? null : value.replaceAll("[^\\p{L}\\p{N}]", "").toLowerCase(Locale.ROOT);
    }

    private static String show(Object value) {
        if (value == null) {
            return "—";
        }
        return value instanceof BigDecimal amount ? amount.toPlainString() : value.toString();
    }

    private static String summary(InvoiceResult result) {
        if (result.error() != null) {
            return "⚠ " + cell(result.error());
        }
        long wrong = result.fields().stream().filter(field -> !field.correct()).count();
        return wrong == 0 ? "✅ all " + result.fields().size() + " fields" : "❌ " + wrong + " of "
                + result.fields().size() + " fields wrong";
    }

    private static String percent(int part, int whole) {
        return whole == 0 ? "—" : Math.round(100.0 * part / whole) + " %";
    }

    // ---------- run ----------

    // Keeps markdown tables intact.
    private static String cell(String value) {
        return value == null ? "—" : value.replace("|", "\\|").replace("\n", " ");
    }

    @Test
    void measureExtractionQuality() throws IOException {
        List<Path> files;
        try (Stream<Path> stream = Files.list(INVOICES)) {
            files = stream.filter(path -> path.toString().toLowerCase(Locale.ROOT).endsWith(".pdf"))
                    .sorted()
                    .toList();
        }
        assertThat(files).as("PDFs in " + INVOICES.toAbsolutePath()).isNotEmpty();

        List<InvoiceResult> results = new ArrayList<>();
        for (Path file : files) {
            System.out.println("Evaluating " + file.getFileName() + " ...");
            results.add(evaluate(file));
        }

        String report = report(results);
        Files.createDirectories(REPORT.getParent());
        Files.writeString(REPORT, report);
        System.out.println(report);
        System.out.println("Report written to " + REPORT.toAbsolutePath());
    }

    // ---------- report ----------

    private InvoiceResult evaluate(Path file) {
        String name = file.getFileName().toString();
        try {
            byte[] pdf = Files.readAllBytes(file);
            ParsedInvoice truth = parser.parse(SourceFormat.ZUGFERD, pdf)
                    .orElseThrow(() -> new IllegalStateException("No embedded invoice XML"));

            PdfText text = pdfTextExtractor.extract(pdf);
            if (!text.hasTextLayer()) {
                return InvoiceResult.failed(name, "PDF has no text layer");
            }

            InvoiceExtraction extraction = extractor.extract(text.text());
            return new InvoiceResult(name, extraction.confidence(), compare(truth, extraction.invoice()), null);
        } catch (Exception e) {
            // One broken invoice must not stop the whole eval run.
            return InvoiceResult.failed(name, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private String report(List<InvoiceResult> results) {
        StringBuilder md = new StringBuilder();
        md.append("# Extraction eval\n\n");
        md.append("- Model: `").append(model).append("`\n");
        md.append("- Run: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                .append("\n");
        md.append("- Invoices: ").append(results.size()).append("\n\n");

        // Accuracy per field, in the order the fields are compared.
        Map<String, int[]> perField = new LinkedHashMap<>(); // field -> [correct, total]
        for (InvoiceResult result : results) {
            for (FieldResult field : result.fields()) {
                int[] counts = perField.computeIfAbsent(field.field(), key -> new int[2]);
                if (field.correct()) {
                    counts[0]++;
                }
                counts[1]++;
            }
        }
        md.append("## Accuracy per field\n\n| Field | Correct | Total | Accuracy |\n|---|---:|---:|---:|\n");
        perField.forEach((field, counts) -> md.append("| ").append(field).append(" | ").append(counts[0])
                .append(" | ").append(counts[1]).append(" | ").append(percent(counts[0], counts[1])).append(" |\n"));

        long failed = results.stream().filter(result -> result.error() != null).count();
        long allCorrect = results.stream().filter(InvoiceResult::allCorrect).count();
        // The dangerous case: wrong, but the model was sure, so no person would look at it.
        long confidentlyWrong = results.stream()
                .filter(result -> result.error() == null && !result.allCorrect())
                .filter(result -> result.confidence() >= minConfidence)
                .count();

        md.append("\n## Summary\n\n");
        md.append("- Invoices fully correct: ").append(allCorrect).append(" of ").append(results.size())
                .append(" (").append(percent((int) allCorrect, results.size())).append(")\n");
        md.append("- **Confidently wrong** (mistake, but confidence ≥ ").append(minConfidence).append("): ")
                .append(confidentlyWrong).append("\n");
        md.append("- Failed to process: ").append(failed).append("\n");

        md.append("\n## Invoices\n\n| File | Confidence | Result |\n|---|---:|---|\n");
        for (InvoiceResult result : results) {
            md.append("| ").append(result.file()).append(" | ")
                    .append(result.error() == null ? String.format(Locale.ROOT, "%.2f", result.confidence()) : "—")
                    .append(" | ").append(summary(result)).append(" |\n");
        }

        md.append("\n## Mistakes\n\n");
        boolean any = false;
        for (InvoiceResult result : results) {
            List<FieldResult> wrong = result.fields().stream().filter(field -> !field.correct()).toList();
            if (wrong.isEmpty()) {
                continue;
            }
            any = true;
            md.append("**").append(result.file()).append("**\n\n");
            for (FieldResult field : wrong) {
                md.append("- ").append(field.field()).append(": expected `").append(cell(field.expected()))
                        .append("`, got `").append(cell(field.actual())).append("`\n");
            }
            md.append("\n");
        }
        if (!any) {
            md.append("None.\n");
        }
        return md.toString();
    }

    record FieldResult(String field, boolean correct, String expected, String actual) {
    }

    record InvoiceResult(String file, double confidence, List<FieldResult> fields, String error) {

        static InvoiceResult failed(String file, String error) {
            return new InvoiceResult(file, 0, List.of(), error);
        }

        boolean allCorrect() {
            return error == null && fields.stream().allMatch(FieldResult::correct);
        }
    }
}