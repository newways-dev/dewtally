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

## Domain gaps

- [x] VAT breakdown per rate (7 % / 19 %) and VAT categories: reverse charge (§ 13b UStG),
  Kleinunternehmer (§ 19 UStG), intra-EU supply; read from XML and by the model, checked by own rules (BR-CO-14,
  BR-CO-17, German rates, exemption reasons), shown on the invoice page
- [ ] Credit notes and cancellations (type 381, 384): negative amounts, link to the original invoice
- [ ] Fuzzy duplicate check: same supplier, amount and date with a different invoice number
- [ ] Fuzzy supplier matching by name ("Muster GmbH" vs "Muster GmbH & Co. KG")
- [ ] Supplier VAT ID check via VIES
- [ ] Approval limits: invoices above a tenant-defined amount need a second approver
- [ ] Allowances and charges with reason and VAT category (today only totals, so the VAT check per category
  is skipped when an invoice has document-level allowances or charges)
- [ ] ADR 0003: GoBD retention (8 years, originals unchanged) vs. DSGVO deletion
- [ ] Mask names and addresses before sending text to the model (needs NER)
- [ ] HTTP client timeout for Spring AI calls (instead of only the virtual-thread time limit)
- [ ] Check that the visible PDF matches the embedded XML (a hybrid invoice can show one thing and pay another)
- [ ] Detect invoices marked as already paid ("bereits beglichen", "bereits bezahlt") to avoid paying twice
- [ ] Clean up sent outbox messages after a retention period
- [ ] Non-blocking retries with delay queues (today the pause between retries blocks the consumer)
- [ ] Alerts: dead letter queue not empty, outbox growing, model latency too high
- [ ] Role management per tenant: an admin of a company assigns roles to its users (today in Keycloak only)
- [ ] Production database login without owner rights (today the app connects as the table owner and switches
  to the restricted role per connection; Liquibase keeps a separate owner login)
- [ ] Separate database users for the application and Keycloak in Azure
- [ ] Documents in Azure Blob Storage instead of a mounted file share
- [ ] PostgreSQL in a private virtual network instead of "allow Azure services"
- [ ] RabbitMQ with persistent storage (today messages are lost when the broker restarts)
- [ ] Terraform state in an Azure Storage backend instead of a local file
- [ ] Scale the worker by queue length (KEDA)
- [ ] Local PostgreSQL data in a named Docker volume (today it lives inside the container)

## Layer 3 — Evals: measuring extraction quality ✅

- [x] Test set of invoices with known correct answers: ZUGFeRD samples and hand-written hard cases
- [x] Eval runner: accuracy per field, "confidently wrong" count, report as a table
- [x] Compare models with numbers, not impressions (ADR 0002: gpt-5-mini vs. gpt-5-nano)
- [x] Prompt: amount due vs. gross amount for partial invoices with prepayments (found by evals)
- [x] Regression check: the eval fails on confident mistakes in critical fields
- [x] VAT breakdown in the evals: categories, rates and amounts per row; hard cases for § 13b, § 19 and two rates

## Layer 4 — Production readiness ✅

- [x] Background processing through RabbitMQ: upload answers 202, status via GET /api/documents/{id}
- [x] Transactional outbox: document and queue message saved in one transaction, so no upload gets lost
- [x] Retries for temporary errors and a dead letter queue for poison messages
- [x] AI extraction in a separate worker, connected via RabbitMQ
- [x] Parse untrusted files (PDF, XML) in the isolated worker with memory and CPU limits
- [x] Metrics and dashboards (Micrometer, Prometheus, Grafana)

## Layer 5 — SaaS ✅

- [x] Login with Keycloak (OpenID Connect); the API only accepts valid tokens
- [x] Tenant and user taken from the token instead of the X-Tenant-Id / X-User-Name headers
- [x] Roles: clerk, approver, admin; permission matrix with deny by default, 401 / 403 as problem details
- [x] Tenant isolation with PostgreSQL Row-Level Security: the database itself only shows the current tenant's rows

## Layer 6 — Cloud deployment

- [x] CI publishes the images to GitHub Container Registry on every merge (api, Keycloak, web; tags: latest,
  commit, version)
- [x] Azure infrastructure with Terraform: Container Apps (api, worker, RabbitMQ, Keycloak, web), PostgreSQL,
  Key Vault with a managed identity, file share for documents
- [x] Budget alert; the whole environment is created and removed with one command (terraform apply / destroy)
- [x] Web UI as a Container App; Keycloak gets the web client's address and secret
- [x] Container images public, so Azure can pull them without registry credentials
- [ ] First deployment to Azure (needs a subscription)
- [ ] Deployment from the pipeline after a merge to main

## Layer 7 — Web UI and showcase ✅

- [x] Next.js app with login through Keycloak (Better Auth, backend for frontend: no token in the browser)
- [x] English and German, light and dark theme, built on shadcn/ui
- [x] Invoice list with status counts, filters and search
- [x] Invoice page: original document (pdf.js viewer, XML as text), findings, lines and totals, VAT breakdown,
  change history
- [x] Review actions by role: correct data, approve (with IBAN confirmation), reject with a reason
- [x] Upload with drag and drop and live processing status
- [x] Supplier master data: add suppliers and IBANs, four-eyes verification, block and unblock
- [x] End-to-end test with Playwright: upload, correction and approval by two users
- [x] Web UI as a standalone Docker image, type check and image build in CI
- [ ] Short demo video

### Web UI: next ideas

- [ ] Readable view of XRechnung files (official KoSIT XRechnung visualization) instead of the raw XML
- [ ] Login also from the local Docker image of the web UI (Keycloak needs one address for browser and container)
- [ ] Run the end-to-end test in CI against the full stack in containers
- [ ] Field values in the history formatted like the rest of the UI (amounts, dates)