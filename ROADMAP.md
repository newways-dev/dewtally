# dewtally roadmap

Built layer by layer. Each layer ends with a working, tested state.
No dates on purpose: the order matters, not the calendar.

## Layer 1 — Structured e-invoices ✅

- [x] Upload with SHA-256 deduplication per tenant
- [x] Format detection: XRechnung UBL, XRechnung CII, ZUGFeRD / Factur-X, plain PDF
- [x] Parsers for UBL and CII (header data and lines)
- [x] Own business rules: required fields, totals (BR-CO-15), line sum, due date, possible duplicates
- [x] Official KoSIT validator with the XRechnung configuration
- [x] Status per invoice (VALID / NEEDS_REVIEW / INVALID) with stored findings
- [x] Read API with paging, errors as RFC 9457 problem details

## Layer 2 — AI extraction and review ✅

- [x] Text extraction from plain PDFs (PDFBox), scans detected and sent to review
- [x] Extraction with a language model (Spring AI, structured output, prompt injection defence)
- [x] Confidence threshold: uncertain results go to review
- [x] Model errors never reject an invoice: a person decides (NEEDS_REVIEW instead of INVALID)
- [x] Review: correct, accept, reject, with a full change history
- [x] Optimistic locking for concurrent reviewers
- [x] Time limit for model calls and a bulkhead for PDF parsing
- [x] Supplier master data with four-eyes verification of IBANs
- [x] Fraud checks: unknown supplier, blocked supplier, unverified or changed IBAN
- [x] Accepting an invoice with a changed IBAN needs an explicit, documented confirmation
- [x] Document-level allowances and charges in parsers, extraction and line sum check
- [x] Mask IBANs, e-mails and phone numbers before sending text to the model (ADR 0001)

## Domain gaps (known, not yet covered)

- [ ] VAT breakdown per rate (7 % / 19 %) and VAT categories: reverse charge (§ 13b UStG),
  Kleinunternehmer (§ 19 UStG), intra-EU supply
- [ ] Credit notes and cancellations (type 381, 384): negative amounts, link to the original invoice
- [ ] Fuzzy duplicate check: same supplier, amount and date with a different invoice number
- [ ] Fuzzy supplier matching by name ("Muster GmbH" vs "Muster GmbH & Co. KG")
- [ ] Supplier VAT ID check via VIES
- [ ] Approval limits: invoices above a tenant-defined amount need a second approver
- [ ] Allowances and charges with reason and VAT rate (today only totals)
- [ ] ADR 0003: GoBD retention (8 years, originals unchanged) vs. DSGVO deletion
- [ ] Mask names and addresses before sending text to the model (needs NER)
- [ ] HTTP client timeout for Spring AI calls (instead of only the virtual-thread time limit)
- [ ] Check that the visible PDF matches the embedded XML (a hybrid invoice can show one thing and pay another)
- [ ] Detect invoices marked as already paid ("bereits beglichen", "bereits bezahlt") to avoid paying twice
- [ ] Clean up sent outbox messages after a retention period

## Layer 3 — Evals: measuring extraction quality ✅

- [x] Test set of invoices with known correct answers: ZUGFeRD samples and hand-written hard cases
- [x] Eval runner: accuracy per field, "confidently wrong" count, report as a table
- [x] Compare models with numbers, not impressions (ADR 0002: gpt-5-mini vs. gpt-5-nano)
- [x] Prompt: amount due vs. gross amount for partial invoices with prepayments (found by evals)
- [x] Regression check: the eval fails on confident mistakes in critical fields

## Layer 4 — Production readiness

- [x] Background processing through RabbitMQ: upload answers 202, status via GET /api/documents/{id}
- [x] Transactional outbox: document and queue message saved in one transaction, so no upload gets lost
- [x] Retries and a dead letter queue for poison messages
- [x] AI extraction in a separate worker, connected via RabbitMQ
- [x] Parse untrusted files (PDF, XML) in the isolated worker with memory and CPU limits
- [ ] Metrics and dashboards (Micrometer, Prometheus, Grafana)

## Layer 5 — SaaS

- [ ] Login and roles (e.g. clerk, approver, admin)
- [ ] Tenant and user taken from the token instead of the X-Tenant-Id / X-User-Name headers
- [ ] Tenant isolation with PostgreSQL Row-Level Security

## Layer 6 — Cloud deployment

- [ ] Azure infrastructure with Terraform
- [ ] CI/CD pipeline from pull request to deployment

## Layer 7 — UI and showcase

- [ ] Web UI: review queue, invoice details, supplier master data
- [ ] Demo data and a short demo video