# Evals

Measuring how well the language model extracts invoice data. See layer 3 in [ROADMAP.md](../ROADMAP.md).

## Invoices in `zugferd/`

ZUGFeRD / Factur-X PDFs contain the correct answer: the embedded XML. The model only gets the visible
text of the PDF; its answer is compared with the XML field by field. No manual labelling needed.

Source: [ZUGFeRD corpus](https://github.com/ZUGFeRD/corpus) (`ZUGFeRDv2/correct`), Apache License 2.0.
Files are unchanged. They are sample invoices with fictitious companies.

## Excluded

- `PHP_@gpFacturX/sample_inofficial_20190125_atgp_factur-x_v_1_0.pdf`: the visible PDF (German invoice from
  "Bei Spiel GmbH") and the embedded XML (French invoice from "Au bon moulin") are two different invoices,
  so the XML is not a valid answer for the visible text.

## Hard cases

`HardCases.java` (in `src/test/java/.../evals`) holds hand-written invoices with known traps:
Skonto (not a discount), the buyer's IBAN next to the seller's, prompt injection, line prices
including VAT, German number and date formats, document-level discount and shipping.
In these cases a value that is not printed must stay empty: calculating it counts as a mistake.

## Running

    .\mvnw.cmd test -Dtest=ExtractionEval#zugferdSamples -Dai=true
    .\mvnw.cmd test -Dtest=ExtractionEval#hardCases -Dai=true

Reports are written to `target/evals/`; snapshots worth keeping go to `results/`.