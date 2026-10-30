package com.kate.dewtally.security;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/**
 * Test tokens shaped like Keycloak's, so tests need no running Keycloak.
 * Names and roles match the test users in keycloak/dewtally-realm.json; roles become authorities
 * with the same code as in production (Roles.authorities).
 */
public final class TestUsers {

    private TestUsers() {
    }

    /**
     * MockMvc that runs requests through the real security filters.
     */
    public static MockMvc mockMvc(WebApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    public static RequestPostProcessor user(UUID tenantId, String name, String... roles) {
        return jwt()
                .jwt(token -> token
                        .claim(CurrentUser.TENANT_CLAIM, tenantId.toString())
                        .claim("preferred_username", name)
                        .claim("realm_access", Map.of("roles", List.of(roles))))
                .authorities(Roles::authorities);
    }

    /**
     * Uploads, checks and corrects invoices.
     */
    public static RequestPostProcessor clerk(UUID tenantId) {
        return user(tenantId, "anna", Roles.CLERK);
    }

    /**
     * Accepts and rejects invoices, verifies IBANs.
     */
    public static RequestPostProcessor approver(UUID tenantId) {
        return user(tenantId, "ben", Roles.APPROVER);
    }

    /**
     * Manages supplier master data.
     */
    public static RequestPostProcessor admin(UUID tenantId) {
        return user(tenantId, "carla", Roles.ADMIN);
    }
}