package com.kate.dewtally.security;

import com.kate.dewtally.TestcontainersConfiguration;
import com.kate.dewtally.tenant.Tenant;
import com.kate.dewtally.tenant.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static com.kate.dewtally.security.TestUsers.clerk;
import static com.kate.dewtally.security.TestUsers.mockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The API is closed without a token. Test tokens are simulated, so no Keycloak is needed here.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AuthenticationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private TenantRepository tenants;

    private MockMvc mvc;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        mvc = mockMvc(context);
        tenantId = tenants.save(new Tenant("Test GmbH " + UUID.randomUUID())).getId();
    }

    @Test
    void apiWithoutTokenIsUnauthorized() throws Exception {
        mvc.perform(get("/api/invoices"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void apiWithValidTokenIsOpen() throws Exception {
        mvc.perform(get("/api/invoices").with(clerk(tenantId)))
                .andExpect(status().isOk());
    }

    @Test
    void validTokenWithoutCompanyIsForbidden() throws Exception {
        // jwt() without a tenant_id claim: logged in, but not a member of any company.
        mvc.perform(get("/api/invoices").with(jwt()))
                .andExpect(status().isForbidden());
    }

    // Whether the app is healthy depends on RabbitMQ etc. (503 when it is down); here we only check
    // that the health check is reachable without a token.
    @Test
    void healthCheckNeedsNoToken() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .isNotIn(401, 403));
    }
}