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

## Results

| Run        | Model      | ZUGFeRD samples | Hard cases | Confidently wrong | Sent to review |
|------------|------------|----------------:|-----------:|------------------:|---------------:|
| 2026-10-25 | gpt-5-mini |         18 / 18 |      6 / 6 |                 0 |        1 of 24 |
| 2026-10-25 | gpt-5-nano |         16 / 18 |      5 / 6 |                 0 |       15 of 24 |

Model choice: [ADR 0002](../docs/adr/0002-model-choice.md).

## Known disagreements

- `zugferd_2p0_EN16931_OEPNV.pdf`: the due date 03.10.2018 sits in a table cell next to
  "Der Rechnungsbetrag wurde bereits beglichen". Since the prompt mentions "bereits bezahlt",
  gpt-5-mini sometimes leaves the due date empty. Accepted: the invoice is already paid,
  and an empty value is safer than a wrong one.