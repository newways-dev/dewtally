# Extraction eval: ZUGFeRD samples

- Model: `gpt-5-mini`
- Run: 2026-11-03 22:23
- Invoices: 18

## Accuracy per field

| Field | Correct | Total | Accuracy |
|---|---:|---:|---:|
| invoiceNumber | 18 | 18 | 100 % |
| issueDate | 18 | 18 | 100 % |
| dueDate | 7 | 7 | 100 % |
| currency | 18 | 18 | 100 % |
| supplierName (low risk) | 18 | 18 | 100 % |
| supplierVatId | 18 | 18 | 100 % |
| iban | 7 | 7 | 100 % |
| netAmount | 18 | 18 | 100 % |
| taxAmount | 17 | 18 | 94 % |
| grossAmount | 18 | 18 | 100 % |
| vatRows (low risk) | 18 | 18 | 100 % |
| vatAmounts (low risk) | 18 | 18 | 100 % |
| lineCount (low risk) | 17 | 17 | 100 % |
| lineNetSum | 17 | 17 | 100 % |
| allowanceTotal | 5 | 5 | 100 % |
| chargeTotal | 4 | 4 | 100 % |

## Summary

- Invoices fully correct: 17 of 18 (94 %)
- Fields correct: 236 of 237 (100 %, minimum 95 %)
- **Confidently wrong** (wrong value in a critical field, confidence ≥ 0.8): 0 (must be 0)
- With omissions (value printed but returned empty): 0
- Failed to process: 0 (must be 0)

## Invoices

| Invoice | Confidence | Result |
|---|---:|---|
| Facture_FR_BASICWL.pdf | 0.90 | ✅ all 12 fields |
| MustangGnuaccountingBeispielRE-20201121_508.pdf | 0.86 | ✅ all 14 fields |
| zugferd_2p0_BASIC_Taxifahrt.pdf | 0.96 | ✅ all 13 fields |
| zugferd_2p0_EN16931_1_Teilrechnung.pdf | 0.82 | ✅ all 14 fields |
| zugferd_2p0_EN16931_AbweichenderZahlungsempf.pdf | 0.95 | ✅ all 13 fields |
| zugferd_2p0_EN16931_Einfach.pdf | 0.86 | ✅ all 12 fields |
| zugferd_2p0_EN16931_Gutschrift.pdf | 0.93 | ✅ all 12 fields |
| zugferd_2p0_EN16931_Haftpflichtversicherung_Versicherungssteuer.pdf | 0.92 | ✅ all 12 fields |
| zugferd_2p0_EN16931_Innergemeinschaftliche_Lieferungen.pdf | 0.93 | ✅ all 14 fields |
| zugferd_2p0_EN16931_Kraftfahrversicherung_Bruttopreise.pdf | 0.70 | ✅ all 12 fields |
| zugferd_2p0_EN16931_Miete.pdf | 0.86 | ✅ all 12 fields |
| zugferd_2p0_EN16931_OEPNV.pdf | 0.92 | ✅ all 14 fields |
| zugferd_2p0_EN16931_Physiotherapeut.pdf | 0.88 | ✅ all 14 fields |
| zugferd_2p0_EN16931_Rabatte.pdf | 0.86 | ✅ all 14 fields |
| zugferd_2p0_EN16931_Rechnungskorrektur.pdf | 0.78 | ❌ 1 of 13 fields wrong |
| zugferd_2p0_EN16931_SEPA_Prenotification.pdf | 0.85 | ✅ all 12 fields |
| zugferd_2p0_EXTENDED_Fremdwaehrung.pdf | 0.78 | ✅ all 16 fields |
| zugferd_2p0_EXTENDED_Warenrechnung.pdf | 0.88 | ✅ all 14 fields |

## Mistakes

**zugferd_2p0_EN16931_Rechnungskorrektur.pdf** (confidence 0.78)

- wrong taxAmount: expected `-1.12`, got `1.12`

