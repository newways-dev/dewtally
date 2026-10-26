package com.kate.dewtally.security;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Writes security errors (401, 403) as application/problem+json, like every other API error.
 * They happen in the security filter, before any controller or @ControllerAdvice is involved.
 */
final class ProblemResponses {

    private ProblemResponses() {
    }

    // Only fixed texts are written here, so no JSON escaping is needed.
    static void write(HttpServletResponse response, int status, String title, String detail) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("""
                {"type":"about:blank","title":"%s","status":%d,"detail":"%s"}"""
                .formatted(title, status, detail));
    }
}