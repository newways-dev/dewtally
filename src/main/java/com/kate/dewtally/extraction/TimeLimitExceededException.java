package com.kate.dewtally.extraction;


// A task did not finish within its time limit.
public class TimeLimitExceededException extends RuntimeException {

    public TimeLimitExceededException(String message) {
        super(message);
    }
}