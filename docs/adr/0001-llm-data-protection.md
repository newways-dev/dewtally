# ADR 0001: Data protection when sending invoices to a language model

## Context

Invoices read from plain PDFs are sent to an external language model (OpenAI) for extraction.
The text contains personal and business data: bank accounts, e-mail addresses, phone numbers,
names of contact persons, addresses. DSGVO Art. 5 requires data minimisation.

## Decision

1. Before sending, `Masking` replaces data the model does not need:
    - IBANs (only those passing the checksum) → `[IBAN_1]`, `[IBAN_2]`, …
    - e-mail addresses → `[EMAIL]`
    - phone numbers after a label (Tel., Fax, Mobil) → `[PHONE]`
2. The model answers with the placeholder; we put the real IBAN back ourselves.
   A valid IBAN in the answer that was not in the document is dropped. So the IBAN on an
   invoice always comes from the document, which also protects against prompt injection
   that tries to change the bank account.
3. Structured e-invoices (XRechnung, ZUGFeRD) are never sent to the model.
4. The invoice text is never logged.

## Consequences

- The model sees less personal data; extraction quality is unchanged (it does not need these values).
- Names of persons and postal addresses are still sent. Masking them needs named entity recognition;
  postponed (residual risk).
- Unlabelled phone numbers are not masked, because digits without a label may be amounts or dates.

## Before production

- Data processing agreement (AVV) with the provider; check data retention and EU data residency options.
- Alternatives: the same model through Azure OpenAI in an EU region, or a local model.
- Mention the processing in the privacy notice (Datenschutzerklärung) and the record of processing activities.