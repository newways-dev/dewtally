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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Separation of duties: who enters an invoice does not approve it, who maintains bank accounts does not pay.
 */
@SpringBootTest(properties = {
        "dewtally.storage.local-root=target/test-documents",
        "dewtally.validation.kosit.enabled=false"
})
@Import(TestcontainersConfiguration.class)
class RoleTest {

    private static final String ACCEPT = "{\"comment\":\"Looks fine\"}";
    private static final String SUPPLIER = "{\"name\":\"Muster GmbH\",\"vatId\":\"DE123456789\"}";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private TenantRepository tenants;

    private MockMvc mvc;
    private UUID tenantId;
    private String invoiceId;

    @BeforeEach
    void setUp() throws Exception {
        mvc = mockMvc(context);
        tenantId = tenants.save(new Tenant("Test GmbH " + UUID.randomUUID())).getId();
        invoiceId = upload();
    }

    @Test
    void clerkCannotApproveOrMaintainSuppliers() throws Exception {
        mvc.perform(post("/api/invoices/{id}/accept", invoiceId).with(clerk(tenantId))
                        .contentType(MediaType.APPLICATION_JSON).content(ACCEPT))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403));
        mvc.perform(post("/api/suppliers").with(clerk(tenantId))
                        .contentType(MediaType.APPLICATION_JSON).content(SUPPLIER))
                .andExpect(status().isForbidden());
    }

    @Test
    void approverCannotEnterInvoicesOrMaintainSuppliers() throws Exception {
        mvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", "a.xml", "application/xml", "<a/>".getBytes()))
                        .with(approver(tenantId)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/suppliers").with(approver(tenantId))
                        .contentType(MediaType.APPLICATION_JSON).content(SUPPLIER))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCannotApproveInvoices() throws Exception {
        mvc.perform(post("/api/invoices/{id}/accept", invoiceId).with(admin(tenantId))
                        .contentType(MediaType.APPLICATION_JSON).content(ACCEPT))
                .andExpect(status().isForbidden());
    }

    @Test
    void everyBusinessRoleCanRead() throws Exception {
        mvc.perform(get("/api/invoices/{id}", invoiceId).with(clerk(tenantId))).andExpect(status().isOk());
        mvc.perform(get("/api/invoices/{id}", invoiceId).with(approver(tenantId))).andExpect(status().isOk());
        mvc.perform(get("/api/invoices/{id}", invoiceId).with(admin(tenantId))).andExpect(status().isOk());
    }

    @Test
    void loggedInUserWithoutBusinessRoleSeesNothing() throws Exception {
        mvc.perform(get("/api/invoices").with(user(tenantId, "eve")))
                .andExpect(status().isForbidden());
    }

    @Test
    void notLoggedInIsAProblemToo() throws Exception {
        mvc.perform(get("/api/invoices"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    private String upload() throws Exception {
        byte[] xml;
        try (InputStream in = getClass().getResourceAsStream("/invoices/xrechnung-ubl.xml")) {
            xml = in.readAllBytes();
        }
        String body = mvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", "invoice.xml", "application/xml", xml))
                        .with(clerk(tenantId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.invoiceId");
    }
}