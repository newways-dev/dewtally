package com.kate.dewtally.invoice;

import com.jayway.jsonpath.JsonPath;
import com.kate.dewtally.TestcontainersConfiguration;
import com.kate.dewtally.tenant.Tenant;
import com.kate.dewtally.tenant.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import static com.kate.dewtally.security.TestUsers.clerk;
import static com.kate.dewtally.security.TestUsers.mockMvc;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// KoSIT is disabled: the simplified sample invoice should be VALID by dewtally's own rules.
@SpringBootTest(properties = {
        "dewtally.storage.local-root=target/test-documents",
        "dewtally.validation.kosit.enabled=false"
})
@Import(TestcontainersConfiguration.class)
class InvoiceControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private TenantRepository tenants;

    private MockMvc mvc;
    private UUID tenantId;

    private static byte[] resource(String name) throws IOException {
        try (InputStream in = InvoiceControllerTest.class.getResourceAsStream("/invoices/" + name)) {
            if (in == null) {
                throw new IllegalStateException("Missing test resource /invoices/" + name);
            }
            return in.readAllBytes();
        }
    }

    @BeforeEach
    void setUp() {
        mvc = mockMvc(context);
        tenantId = tenants.save(new Tenant("Test GmbH " + UUID.randomUUID())).getId();
    }

    @Test
    void returnsInvoiceWithLinesAndIssues() throws Exception {
        String invoiceId = uploadInvoice("xrechnung-ubl.xml");

        mvc.perform(get("/api/invoices/{id}", invoiceId).with(clerk(tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(invoiceId))
                .andExpect(jsonPath("$.status").value("VALID"))
                .andExpect(jsonPath("$.invoiceNumber").value("RE-2041"))
                .andExpect(jsonPath("$.grossAmount").value(1428.00))
                .andExpect(jsonPath("$.lines", hasSize(2)))
                .andExpect(jsonPath("$.lines[0].description").value("Consulting"))
                .andExpect(jsonPath("$.issues", hasSize(0)));
    }

    @Test
    void hidesInvoicesOfOtherTenants() throws Exception {
        String invoiceId = uploadInvoice("xrechnung-ubl.xml");
        UUID otherTenant = tenants.save(new Tenant("Other GmbH " + UUID.randomUUID())).getId();

        mvc.perform(get("/api/invoices/{id}", invoiceId).with(clerk(otherTenant)))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsNotFoundForUnknownInvoice() throws Exception {
        mvc.perform(get("/api/invoices/{id}", UUID.randomUUID()).with(clerk(tenantId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void listsInvoicesFilteredByStatus() throws Exception {
        uploadInvoice("xrechnung-ubl.xml");

        mvc.perform(get("/api/invoices").param("status", "VALID").with(clerk(tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].invoiceNumber").value("RE-2041"))
                .andExpect(jsonPath("$.totalItems").value(1));

        mvc.perform(get("/api/invoices").param("status", "INVALID").with(clerk(tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void listsAllInvoicesWithoutStatus() throws Exception {
        uploadInvoice("xrechnung-ubl.xml");
        uploadInvoice("xrechnung-cii.xml");

        mvc.perform(get("/api/invoices").with(clerk(tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.totalItems").value(2));
    }

    @Test
    void rejectsUnknownStatus() throws Exception {
        mvc.perform(get("/api/invoices").param("status", "FOO").with(clerk(tenantId)))
                .andExpect(status().isBadRequest());
    }

    private String uploadInvoice(String name) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", name, "application/xml", resource(name));
        String body = mvc.perform(multipart("/api/documents").file(file).with(clerk(tenantId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.invoiceId");
    }
}