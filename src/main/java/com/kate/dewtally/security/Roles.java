package com.kate.dewtally.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Business roles (realm roles in Keycloak) and how they become Spring Security authorities.
 * Separation of duties: who enters an invoice does not approve it; who maintains bank accounts does not pay.
 */
public final class Roles {

    /**
     * Uploads, checks and corrects invoices.
     */
    public static final String CLERK = "clerk";
    /**
     * Accepts and rejects invoices, verifies IBANs.
     */
    public static final String APPROVER = "approver";
    /**
     * Maintains supplier master data.
     */
    public static final String ADMIN = "admin";

    private Roles() {
    }

    /**
     * Keycloak puts realm roles into {"realm_access": {"roles": [...]}}; each becomes ROLE_<name>.
     */
    public static Collection<GrantedAuthority> authorities(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }
}