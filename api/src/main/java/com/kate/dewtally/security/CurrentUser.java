package com.kate.dewtally.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Who sends the request: company (tenant), user name and roles. Taken only from the signed token,
 * never from headers or the request body, so nobody can act for another company or person.
 * Add it as a parameter to any controller method.
 */
public record CurrentUser(UUID tenantId, String name, Set<String> roles) {

    // Set by Keycloak from the user's group (the company), see keycloak/dewtally-realm.json
    static final String TENANT_CLAIM = "tenant_id";

    static CurrentUser from(Jwt jwt) {
        UUID tenantId = tenantId(jwt.getClaimAsString(TENANT_CLAIM));
        String name = jwt.getClaimAsString("preferred_username");
        if (name == null || name.isBlank()) {
            name = jwt.getSubject();
        }
        return new CurrentUser(tenantId, name, roles(jwt));
    }

    // A valid login without a company (e.g. a user not in any company group) may not see any data.
    private static UUID tenantId(String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The token does not name a company (tenant_id)");
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The token names an invalid company (tenant_id)");
        }
    }

    // Keycloak puts realm roles into {"realm_access": {"roles": [...]}}
    private static Set<String> roles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
            return Set.of();
        }
        return roles.stream().map(String::valueOf).collect(Collectors.toUnmodifiableSet());
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }
}