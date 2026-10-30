package com.kate.dewtally.security;

import com.jayway.jsonpath.JsonPath;
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

import java.io.InputStream;
import java.util.UUID;

import static com.kate.dewtally.security.TestUsers.*;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The most important SaaS guarantee: a user of company B can neither see nor change anything of
 * company A, whatever they put into the request.
 */
@SpringBootTest(properties = {
        "dewtally.storage.local-root=target/test-documents",
        "dewtally.validation.kosit.enabled=false"
})
@Import(TestcontainersConfiguration.class)
class TenantIsolationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private TenantRepository tenants;

    private MockMvc mvc;
    private UUID companyA;
    private UUID companyB;
    private String documentId;
    private String invoiceId;

    @BeforeEach
    void setUp() throws Exception {
        mvc = mockMvc(context);
        companyA = tenants.save(new Tenant("Company A " + UUID.randomUUID())).getId();
        companyB = tenants.save(new Tenant("Company B " + UUID.randomUUID())).getId();

        byte[] xml;
        try (InputStream in = getClass().getResourceAsStream("/invoices/xrechnung-ubl.xml")) {
            xml = in.readAllBytes();
        }
        String body = mvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", "invoice.xml", "application/xml", xml))
                        .with(clerk(companyA)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        documentId = JsonPath.read(body, "$.id");
        invoiceId = JsonPath.read(body, "$.invoiceId");
    }

    @Test
    void otherCompanyCannotSeeTheInvoiceOrTheDocument() throws Exception {
        mvc.perform(get("/api/invoices/{id}", invoiceId).with(clerk(companyB)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/documents/{id}", documentId).with(clerk(companyB)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/invoices").with(clerk(companyB)))
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void otherCompanyCannotDecideOnTheInvoice() throws Exception {
        mvc.perform(post("/api/invoices/{id}/accept", invoiceId)
                        .with(approver(companyB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"Not mine to accept\"}"))
                .andExpect(status().isNotFound());

        // Still untouched for its owner.
        mvc.perform(get("/api/invoices/{id}", invoiceId).with(clerk(companyA)))
                .andExpect(jsonPath("$.status").value("VALID"));
    }

    @Test
    void theOldTenantHeaderIsIgnored() throws Exception {
        // Before layer 5 this header decided the company. Now only the token does.
        mvc.perform(get("/api/invoices/{id}", invoiceId)
                        .header("X-Tenant-Id", companyA)
                        .with(clerk(companyB)))
                .andExpect(status().isNotFound());
    }
}