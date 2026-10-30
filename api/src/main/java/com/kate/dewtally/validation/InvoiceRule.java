package com.kate.dewtally.validation;

import java.util.List;

public interface InvoiceRule {
    List<Finding> check(ValidationContext context);
}