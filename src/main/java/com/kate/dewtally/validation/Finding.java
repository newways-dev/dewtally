package com.kate.dewtally.validation;

import com.kate.dewtally.invoice.Severity;

public record Finding(Severity severity, String ruleId, String message) {

    static Finding error(String ruleId, String message) {
        return new Finding(Severity.ERROR, ruleId, message);
    }

    static Finding warning(String ruleId, String message) {
        return new Finding(Severity.WARNING, ruleId, message);
    }
}