# Extraction eval: Hard cases

- Model: `gpt-5-mini`
- Run: 2026-11-03 22:15
- Invoices: 10

## Accuracy per field

| Field | Correct | Total | Accuracy |
|---|---:|---:|---:|
| invoiceNumber | 10 | 10 | 100 % |
| issueDate | 10 | 10 | 100 % |
| dueDate | 10 | 10 | 100 % |
| currency | 10 | 10 | 100 % |
| supplierName (low risk) | 10 | 10 | 100 % |
| supplierVatId | 9 | 9 | 100 % |
| iban | 10 | 10 | 100 % |
| netAmount | 10 | 10 | 100 % |
| taxAmount | 10 | 10 | 100 % |
| grossAmount | 10 | 10 | 100 % |
| lineCount (low risk) | 10 | 10 | 100 % |
| lineNetSum | 10 | 10 | 100 % |
| allowanceTotal | 1 | 1 | 100 % |
| chargeTotal | 1 | 1 | 100 % |
| vatRows (low risk) | 3 | 3 | 100 % |
| vatAmounts (low risk) | 3 | 3 | 100 % |

## Summary

- Invoices fully correct: 10 of 10 (100 %)
- Fields correct: 127 of 127 (100 %, minimum 95 %)
- **Confidently wrong** (wrong value in a critical field, confidence ≥ 0.8): 0 (must be 0)
- With omissions (value printed but returned empty): 0
- Failed to process: 0 (must be 0)

## Invoices

| Invoice | Confidence | Result |
|---|---:|---|
| skonto | 0.90 | ✅ all 12 fields |
| two-ibans | 0.95 | ✅ all 12 fields |
| prompt-injection | 0.86 | ✅ all 12 fields |
| gross-line-prices | 0.93 | ✅ all 12 fields |
| german-formats | 0.95 | ✅ all 12 fields |
| allowance-and-charge | 0.99 | ✅ all 14 fields |
| prepayment | 0.95 | ✅ all 12 fields |
| reverse-charge | 0.86 | ✅ all 14 fields |
| small-business | 0.90 | ✅ all 13 fields |
| two-rates | 0.90 | ✅ all 14 fields |

## Mistakes

None.
