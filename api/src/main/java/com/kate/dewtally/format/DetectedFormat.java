package com.kate.dewtally.format;

import com.kate.dewtally.document.SourceFormat;

/**
 * What the detector found out about a file: its syntax and, if declared, its profile.
 */
public record DetectedFormat(SourceFormat format, String profile) {

    static DetectedFormat unknown() {
        return new DetectedFormat(SourceFormat.UNKNOWN, null);
    }
}