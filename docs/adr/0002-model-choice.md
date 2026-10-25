# ADR 0002: Which language model extracts invoice data

## Context

Plain PDF invoices are read by a language model. Cheaper models cost less per call, but a model
that is unsure sends more invoices to a person, and a person's review time costs far more than
an API call. The choice must be based on measurements, not impressions.

## Measurement

Eval runs on 2026-10-25 (`evals/results/`): 18 ZUGFeRD samples, where the embedded XML is the
correct answer, and 6 hand-written hard cases (Skonto, two IBANs, prompt injection, gross line
prices, German formats, document-level discount and shipping). Confidence threshold: 0.8.

|                                               | gpt-5-mini | gpt-5-nano |
|-----------------------------------------------|-----------:|-----------:|
| ZUGFeRD samples fully correct                 |    18 / 18 |    16 / 18 |
| Hard cases fully correct                      |      6 / 6 |      5 / 6 |
| Confidently wrong (mistake, confidence ≥ 0.8) |          0 |          0 |
| Sent to review (confidence < 0.8)             |    1 of 24 |   15 of 24 |

## Decision

Use **gpt-5-mini**.

gpt-5-nano is cheaper per call and its mistakes all had low confidence, so none would have slipped
through. But it is unsure even when it is right: 15 of 24 invoices would go to a person, against
1 of 24 with gpt-5-mini. The saved API cost does not pay for that review time.

## Consequences

- The confidence threshold is calibrated for gpt-5-mini. Confidence values are not comparable between
  models: changing the model means re-running the evals and choosing the threshold again.
- The decision is repeated when new models appear or prices change: run
  `.\mvnw.cmd test -Dtest=ExtractionEval -Dai=true "-Dspring.ai.openai.chat.model=<MODEL>"`
  and compare with the saved results.
- Found along the way: the prompt lists "zu zahlen" as gross amount, which is wrong for partial
  invoices with prepayments (gpt-5-nano took the amount due as the gross amount). To be fixed.