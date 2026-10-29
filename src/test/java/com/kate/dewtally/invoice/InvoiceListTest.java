package com.kate.dewtally.invoice;

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
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static com.kate.dewtally.security.TestUsers.clerk;
import static com.kate.dewtally.security.TestUsers.mockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What the invoice list screen needs: counts per status, search and the columns of the table.
 */
@SpringBootTest(properties = {
        "dewtally.storage.local-root=target/test-documents",
        "dewtally.validation.kosit.enabled=false"
})
@Import(TestcontainersConfiguration.class)
class InvoiceListTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private TenantRepository tenants;

    private MockMvc mvc;
    private UUID tenantId;

    private static byte[] resource(String name) throws IOException {
        try (InputStream in = InvoiceListTest.class.getResourceAsStream("/invoices/" + name)) {
            if (in == null) {
                throw new IllegalStateException("Missing test resource /invoices/" + name);
            }
            return in.readAllBytes();
        }
    }

    // The sample invoice with a wrong total: BR-CO-15 makes it INVALID
    private static byte[] wrongTotal() throws IOException {
        return new String(resource("xrechnung-ubl.xml"), StandardCharsets.UTF_8).replace(
                "<cbc:TaxInclusiveAmount currencyID=\"EUR\">1428.00</cbc:TaxInclusiveAmount>",
                "<cbc:TaxInclusiveAmount currencyID=\"EUR\">1500.00</cbc:TaxInclusiveAmount>"
        ).getBytes(StandardCharsets.UTF_8);
    }

    @BeforeEach
    void setUp() {
        mvc = mockMvc(context);
        tenantId = tenants.save(new Tenant("Test GmbH " + UUID.randomUUID())).getId();
    }

    @Test
    void countsInvoicesPerStatusIncludingEmptyOnes() throws Exception {
        upload(resource("xrechnung-ubl.xml"));
        upload(wrongTotal());

        mvc.perform(get("/api/invoices/counts").with(clerk(tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.byStatus.VALID").value(1))
                .andExpect(jsonPath("$.byStatus.INVALID").value(1))
                .andExpect(jsonPath("$.byStatus.NEEDS_REVIEW").value(0))
                .andExpect(jsonPath("$.byStatus.ACCEPTED").value(0));
    }

    @Test
    void searchesByInvoiceNumberIgnoringCase() throws Exception {
        upload(resource("xrechnung-ubl.xml"));

        mvc.perform(get("/api/invoices").param("q", "re-2041").with(clerk(tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].invoiceNumber").value("RE-2041"));

        mvc.perform(get("/api/invoices").param("q", "nothing like this").with(clerk(tenantId)))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void searchTreatsWildcardsAsPlainCharacters() throws Exception {
        upload(resource("xrechnung-ubl.xml"));

        // Without escaping, "%" would match every invoice
        mvc.perform(get("/api/invoices").param("q", "%").with(clerk(tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void listHasTheColumnsOfTheTable() throws Exception {
        upload(resource("xrechnung-ubl.xml"));

        mvc.perform(get("/api/invoices").with(clerk(tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].extractionMethod").value("STRUCTURED"))
                .andExpect(jsonPath("$.items[0].issueCount").value(0))
                .andExpect(jsonPath("$.items[0].status").value("VALID"));
    }

    private void upload(byte[] content) throws Exception {
        mvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", "rechnung.xml", "application/xml", content))
                        .with(clerk(tenantId)))
                .andExpect(status().isCreated());
    }
}