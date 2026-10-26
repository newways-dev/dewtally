package com.kate.dewtally.web;

import com.kate.dewtally.TestcontainersConfiguration;
import com.kate.dewtally.tenant.Tenant;
import com.kate.dewtally.tenant.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static com.kate.dewtally.security.TestUsers.clerk;
import static com.kate.dewtally.security.TestUsers.mockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Every error of the API must come back as application/problem+json with the right status.
 */
@SpringBootTest(properties = {
        "dewtally.storage.local-root=target/test-documents",
        "dewtally.validation.kosit.enabled=false"
})
@Import(TestcontainersConfiguration.class)
class ApiExceptionHandlerTest {

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
    void unknownInvoiceIsNotFoundProblem() throws Exception {
        mvc.perform(get("/api/invoices/{id}", UUID.randomUUID()).with(clerk(tenantId)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Invoice not found"));
    }

    @Test
    void tokenWithoutCompanyIsForbiddenProblem() throws Exception {
        mvc.perform(get("/api/invoices").with(jwt()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void malformedCompanyInTokenIsForbiddenProblem() throws Exception {
        mvc.perform(get("/api/invoices").with(jwt().jwt(token -> token.claim("tenant_id", "not-a-uuid"))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void unknownStatusIsBadRequestProblem() throws Exception {
        mvc.perform(get("/api/invoices").param("status", "FOO").with(clerk(tenantId)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void uploadForUnknownTenantIsNotFoundProblem() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.xml", "application/xml", "<a/>".getBytes());

        mvc.perform(multipart("/api/documents").file(file).with(clerk(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void emptyUploadIsBadRequestProblem() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("file", "empty.xml", "application/xml", new byte[0]);

        mvc.perform(multipart("/api/documents").file(empty).with(clerk(tenantId)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }
}