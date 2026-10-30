package com.kate.dewtally.validation;

import com.kate.dewtally.invoice.ExtractionMethod;
import com.kate.dewtally.invoice.Invoice;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Sends invoices to review when the language model itself was not sure about the data.
 * The threshold is a starting value; layer 3 (evals) will choose it from measured numbers.
 */
@Component
public class ExtractionConfidenceRule implements InvoiceRule {

    private final BigDecimal minConfidence;

    public ExtractionConfidenceRule(@Value("${dewtally.extraction.min-confidence:0.8}") BigDecimal minConfidence) {
        this.minConfidence = minConfidence;
    }

    @Override
    public List<Finding> check(ValidationContext context) {
        Invoice invoice = context.invoice();
        if (invoice.getExtractionMethod() != ExtractionMethod.LLM) {
            return List.of();
        }

        BigDecimal confidence = invoice.getExtractionConfidence();
        if (confidence == null || confidence.compareTo(minConfidence) < 0) {
            return List.of(Finding.warning("DT-LOW-CONFIDENCE",
                    "Die automatische Erfassung ist unsicher (Konfidenz " + confidence
                            + "). Bitte die Daten prüfen."));
        }
        return List.of();
    }
}