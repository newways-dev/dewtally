package com.kate.dewtally.security;

import com.kate.dewtally.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Runs the rest of the request as the tenant from the token, so the database only shows that tenant's rows.
 * Part of the security filter chain, after the token has been checked.
 */
class TenantFilter extends OncePerRequestFilter {

    // No valid tenant in the token: the controller answers 403, and the database shows nothing anyway.
    private static Optional<UUID> tenantOf(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            return Optional.empty();
        }
        try {
            return Optional.of(CurrentUser.from(token.getToken()).tenantId());
        } catch (ResponseStatusException e) {
            return Optional.empty();
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<UUID> tenant = tenantOf(SecurityContextHolder.getContext().getAuthentication());
        if (tenant.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        try {
            TenantContext.callAs(tenant.get(), () -> {
                chain.doFilter(request, response);
                return null;
            });
        } catch (IOException | ServletException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }
}