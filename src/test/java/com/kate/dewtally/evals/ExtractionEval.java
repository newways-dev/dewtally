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
import com.kate.dewtally.parsing.ParsedLine;
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
 * Measures how well the language model extracts invoice data. Costs money and is not deterministic,
 * so it is not part of the normal build. Two sets:
 * <ul>
 *   <li>ZUGFeRD samples: the embedded XML is the correct answer for the visible text.
 *       {@code .\mvnw.cmd test -Dtest=ExtractionEval#zugferdSamples -Dai=true}</li>
 *   <li>Hard cases: hand-written invoices with traps ({@link HardCases}).
 *       {@code .\mvnw.cmd test -Dtest=ExtractionEval#hardCases -Dai=true}</li>
 * </ul>
 */
@SpringBootTest(properties = "dewtally.validation.kosit.enabled=false")
@Import(TestcontainersConfiguration.class)
@EnabledIfSystemProperty(named = "ai", matches = "true")
class ExtractionEval {

    private static final Path INVOICES = Path.of("evals", "zugferd");
    private static final Path REPORTS = Path.of("target", "evals");

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

    /**
     * @param strict also check that the model leaves a field empty when the invoice does not contain it
     *               (used for hand-written cases, where we know exactly what is printed)
     */
    static List<FieldResult> compare(ParsedInvoice truth, ParsedInvoice actual, boolean strict) {
        InvoiceData expected = truth.data();
        InvoiceData got = actual.data();
        List<FieldResult> results = new ArrayList<>();

        text(results, "invoiceNumber", expected.invoiceNumber(), got.invoiceNumber());
        exact(results, "issueDate", expected.issueDate(), got.issueDate());
        if (strict && expected.dueDate() == null) {
            // "Zahlbar innerhalb von 30 Tagen" has no date; calculating one is inventing a value.
            results.add(new FieldResult("dueDate", got.dueDate() == null, "—", show(got.dueDate())));
        } else {
            exact(results, "dueDate", expected.dueDate(), got.dueDate());
        }
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
            // Catches gross prices taken as net line amounts.
            BigDecimal expectedSum = lineNetSum(truth);
            if (strict && expectedSum == null) {
                // No net line amounts printed: the model must leave them empty, not calculate or copy gross.
                BigDecimal actualSum = lineNetSum(actual);
                results.add(new FieldResult("lineNetSum", actualSum == null, "—", show(actualSum)));
            } else {
                amount(results, "lineNetSum", expectedSum, lineNetSum(actual));
            }
        }
        return results;
    }

    // Null if there are no lines or a line has no net amount.
    private static BigDecimal lineNetSum(ParsedInvoice invoice) {
        if (invoice.lines().isEmpty()) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (ParsedLine line : invoice.lines()) {
            if (line.netAmount() == null) {
                return null;
            }
            sum = sum.add(line.netAmount());
        }
        return sum;
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

    // ---------- runs ----------

    // Keeps markdown tables intact.
    private static String cell(String value) {
        return value == null ? "—" : value.replace("|", "\\|").replace("\n", " ");
    }

    @Test
    void zugferdSamples() throws IOException {
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
            results.add(evaluatePdf(file));
        }
        write("ZUGFeRD samples", results, REPORTS.resolve("zugferd-report.md"));
    }

    @Test
    void hardCases() throws IOException {
        List<InvoiceResult> results = new ArrayList<>();
        for (HardCases.Case hardCase : HardCases.ALL) {
            System.out.println("Evaluating " + hardCase.name() + " ...");
            results.add(evaluateText(hardCase.name(), hardCase.expected(), hardCase.text(), true));
        }
        write("Hard cases", results, REPORTS.resolve("hard-cases-report.md"));
    }

    private InvoiceResult evaluatePdf(Path file) {
        String name = file.getFileName().toString();
        try {
            byte[] pdf = Files.readAllBytes(file);
            ParsedInvoice truth = parser.parse(SourceFormat.ZUGFERD, pdf)
                    .orElseThrow(() -> new IllegalStateException("No embedded invoice XML"));
            PdfText text = pdfTextExtractor.extract(pdf);
            if (!text.hasTextLayer()) {
                return InvoiceResult.failed(name, "PDF has no text layer");
            }
            return evaluateText(name, truth, text.text(), false);
        } catch (Exception e) {
            return InvoiceResult.failed(name, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private InvoiceResult evaluateText(String name, ParsedInvoice truth, String text, boolean strict) {
        try {
            InvoiceExtraction extraction = extractor.extract(text);
            return new InvoiceResult(name, extraction.confidence(), compare(truth, extraction.invoice(), strict), null);
        } catch (Exception e) {
            // One broken invoice must not stop the whole eval run.
            return InvoiceResult.failed(name, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    // ---------- report ----------

    private void write(String title, List<InvoiceResult> results, Path file) throws IOException {
        String report = report(title, results);
        Files.createDirectories(file.getParent());
        Files.writeString(file, report);
        System.out.println(report);
        System.out.println("Report written to " + file.toAbsolutePath());
    }

    private String report(String title, List<InvoiceResult> results) {
        StringBuilder md = new StringBuilder();
        md.append("# Extraction eval: ").append(title).append("\n\n");
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

        md.append("\n## Invoices\n\n| Invoice | Confidence | Result |\n|---|---:|---|\n");
        for (InvoiceResult result : results) {
            md.append("| ").append(result.name()).append(" | ")
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
            md.append("**").append(result.name()).append("** (confidence ")
                    .append(String.format(Locale.ROOT, "%.2f", result.confidence())).append(")\n\n");
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

    record InvoiceResult(String name, double confidence, List<FieldResult> fields, String error) {

        static InvoiceResult failed(String name, String error) {
            return new InvoiceResult(name, 0, List.of(), error);
        }

        boolean allCorrect() {
            return error == null && fields.stream().allMatch(FieldResult::correct);
        }
    }
}