package com.kate.dewtally.xml;

/**
 * Thrown when content is not well-formed XML or was rejected for security reasons (e.g. DOCTYPE).
 */
public class InvalidXmlException extends RuntimeException {

    public InvalidXmlException(String message, Throwable cause) {
        super(message, cause);
    }
}