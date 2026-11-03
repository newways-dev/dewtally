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
including VAT, German number and date formats, document-level discount and shipping, a prepayment,
reverse charge under § 13b UStG (with the buyer's VAT ID printed too), a small business under § 19 UStG (only a tax
number, no VAT ID) and two VAT rates on one invoice.
In these cases a value that is not printed must stay empty: calculating it counts as a mistake.

## What is compared

Header fields, line count and line net sum, document-level allowances and charges, and the VAT breakdown:
`vatRows` (the same categories and rates, e.g. `S 19 | S 7` or `AE 0`) and `vatAmounts` (the same taxable
amount and VAT per row).

## Running

    .\mvnw.cmd test -Dtest=ExtractionEval -Dai=true
    .\mvnw.cmd test -Dtest=ExtractionEval#hardCases -Dai=true
    .\mvnw.cmd test -Dtest=ExtractionEval -Dai=true "-Dspring.ai.openai.chat.model=<MODEL>"

Reports are written to `target/evals/`; snapshots worth keeping go to `results/`.

## Regression check

The eval fails (after writing the report) if:

- any wrong value in a critical field has confidence ≥ 0.8 ("confidently wrong"),
- any invoice could not be processed,
- less than 95 % of all fields are correct.

Critical fields decide how much is paid, to whom and when: amounts, IBAN, invoice number, dates,
VAT ID. `supplierName`, `lineCount`, `vatRows` and `vatAmounts` are low risk: other checks catch them (suppliers are
matched by VAT ID first; the line sum check catches missing lines; the VAT rules BR-CO-14
and DT-VAT-BASE-TOTAL catch a wrong breakdown, which matters for booking, not for how much is paid).
Omissions (a printed value returned empty) are reported separately: an empty field is visible to rules
and people, a wrong value is not.

## Results

| Run                        | Model      | ZUGFeRD samples | Hard cases | Confidently wrong | Sent to review |
|----------------------------|------------|----------------:|-----------:|------------------:|---------------:|
| 2026-10-25                 | gpt-5-mini |         18 / 18 |      6 / 6 |                 0 |        1 of 24 |
| 2026-10-25                 | gpt-5-nano |         16 / 18 |      5 / 6 |                 0 |       15 of 24 |
| 2026-10-25 (prompt v2)     | gpt-5-mini |         18 / 18 |      7 / 7 |                 0 |        0 of 25 |
| 2026-11-03 (VAT breakdown) | gpt-5-mini |         17 / 18 |    10 / 10 |                 0 |        3 of 28 |

With the VAT breakdown, the model got all 18 breakdowns of the ZUGFeRD samples right (including the
intra-EU supply, category K) and all three VAT hard cases (reverse charge, § 19, two rates).

Model choice: [ADR 0002](../docs/adr/0002-model-choice.md).

## Known disagreements

- `zugferd_2p0_EN16931_OEPNV.pdf`: the due date 03.10.2018 sits in a table cell next to
  "Der Rechnungsbetrag wurde bereits beglichen". Since the prompt mentions "bereits bezahlt",
  gpt-5-mini sometimes leaves the due date empty. Accepted: the invoice is already paid,
  and an empty value is safer than a wrong one.
- `zugferd_2p0_EN16931_Rechnungskorrektur.pdf`: in some runs the model drops the minus sign of the VAT total
  of this correction invoice (1.12 instead of -1.12). Caught three times: the confidence is below the
  threshold (review), BR-CO-15 (net + VAT ≠ gross) and BR-CO-14 (VAT breakdown ≠ VAT total) both fail.
- `zugferd_2p0_EN16931_Kraftfahrversicherung_Bruttopreise.pdf`: lines are printed with gross prices; the model
  sometimes leaves the line net amounts empty instead of calculating them, as the prompt asks.