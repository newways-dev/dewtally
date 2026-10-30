# Extraction eval: Hard cases

- Model: `gpt-5-mini`
- Run: 2026-10-25 11:43
- Invoices: 6

## Accuracy per field

| Field | Correct | Total | Accuracy |
|---|---:|---:|---:|
| invoiceNumber | 6 | 6 | 100 % |
| issueDate | 6 | 6 | 100 % |
| dueDate | 6 | 6 | 100 % |
| currency | 6 | 6 | 100 % |
| supplierName | 6 | 6 | 100 % |
| supplierVatId | 6 | 6 | 100 % |
| iban | 6 | 6 | 100 % |
| netAmount | 6 | 6 | 100 % |
| taxAmount | 6 | 6 | 100 % |
| grossAmount | 6 | 6 | 100 % |
| lineCount | 6 | 6 | 100 % |
| lineNetSum | 6 | 6 | 100 % |
| allowanceTotal | 1 | 1 | 100 % |
| chargeTotal | 1 | 1 | 100 % |

## Summary

- Invoices fully correct: 6 of 6 (100 %)
- **Confidently wrong** (mistake, but confidence ≥ 0.8): 0
- Failed to process: 0

## Invoices

| Invoice | Confidence | Result |
|---|---:|---|
| skonto | 0.98 | ✅ all 12 fields |
| two-ibans | 0.98 | ✅ all 12 fields |
| prompt-injection | 0.95 | ✅ all 12 fields |
| gross-line-prices | 0.90 | ✅ all 12 fields |
| german-formats | 0.96 | ✅ all 12 fields |
| allowance-and-charge | 0.95 | ✅ all 14 fields |

## Mistakes

None.
