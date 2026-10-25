# Extraction eval: Hard cases

- Model: `gpt-5-mini`
- Run: 2026-10-25 15:31
- Invoices: 7

## Accuracy per field

| Field | Correct | Total | Accuracy |
|---|---:|---:|---:|
| invoiceNumber | 7 | 7 | 100 % |
| issueDate | 7 | 7 | 100 % |
| dueDate | 7 | 7 | 100 % |
| currency | 7 | 7 | 100 % |
| supplierName (low risk) | 7 | 7 | 100 % |
| supplierVatId | 7 | 7 | 100 % |
| iban | 7 | 7 | 100 % |
| netAmount | 7 | 7 | 100 % |
| taxAmount | 7 | 7 | 100 % |
| grossAmount | 7 | 7 | 100 % |
| lineCount (low risk) | 7 | 7 | 100 % |
| lineNetSum | 7 | 7 | 100 % |
| allowanceTotal | 1 | 1 | 100 % |
| chargeTotal | 1 | 1 | 100 % |

## Summary

- Invoices fully correct: 7 of 7 (100 %)
- Fields correct: 86 of 86 (100 %, minimum 95 %)
- **Confidently wrong** (wrong value in a critical field, confidence ≥ 0.8): 0 (must be 0)
- With omissions (value printed but returned empty): 0
- Failed to process: 0 (must be 0)

## Invoices

| Invoice | Confidence | Result |
|---|---:|---|
| skonto | 0.95 | ✅ all 12 fields |
| two-ibans | 0.93 | ✅ all 12 fields |
| prompt-injection | 0.86 | ✅ all 12 fields |
| gross-line-prices | 0.90 | ✅ all 12 fields |
| german-formats | 0.95 | ✅ all 12 fields |
| allowance-and-charge | 0.95 | ✅ all 14 fields |
| prepayment | 0.98 | ✅ all 12 fields |

## Mistakes

None.
