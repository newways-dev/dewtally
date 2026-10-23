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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

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

    private static final String TENANT_HEADER = "X-Tenant-Id";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private TenantRepository tenants;

    private MockMvc mvc;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        tenantId = tenants.save(new Tenant("Test GmbH " + UUID.randomUUID())).getId();
    }

    @Test
    void unknownInvoiceIsNotFoundProblem() throws Exception {
        mvc.perform(get("/api/invoices/{id}", UUID.randomUUID()).header(TENANT_HEADER, tenantId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Invoice not found"));
    }

    @Test
    void missingTenantHeaderIsBadRequestProblem() throws Exception {
        mvc.perform(get("/api/invoices"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void malformedTenantIdIsBadRequestProblem() throws Exception {
        mvc.perform(get("/api/invoices").header(TENANT_HEADER, "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void unknownStatusIsBadRequestProblem() throws Exception {
        mvc.perform(get("/api/invoices").param("status", "FOO").header(TENANT_HEADER, tenantId))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void uploadForUnknownTenantIsNotFoundProblem() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.xml", "application/xml", "<a/>".getBytes());

        mvc.perform(multipart("/api/documents").file(file).header(TENANT_HEADER, UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void emptyUploadIsBadRequestProblem() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("file", "empty.xml", "application/xml", new byte[0]);

        mvc.perform(multipart("/api/documents").file(empty).header(TENANT_HEADER, tenantId))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }
}