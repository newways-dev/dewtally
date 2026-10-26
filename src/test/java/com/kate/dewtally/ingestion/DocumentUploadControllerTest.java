package com.kate.dewtally.ingestion;

import com.jayway.jsonpath.JsonPath;
import com.kate.dewtally.TestcontainersConfiguration;
import com.kate.dewtally.invoice.*;
import com.kate.dewtally.tenant.Tenant;
import com.kate.dewtally.tenant.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static com.kate.dewtally.security.TestUsers.clerk;
import static com.kate.dewtally.security.TestUsers.mockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Calls the real HTTP endpoint (without a network) against a real PostgreSQL.
 * Uploaded files go to target/, which "mvn clean" removes.
 */
@SpringBootTest(properties = {
        "dewtally.storage.local-root=target/test-documents",
        "dewtally.validation.kosit.enabled=false"
})
@Import(TestcontainersConfiguration.class)
@Transactional
class DocumentUploadControllerTest {

    private static final byte[] XML = "<Invoice>RE-2041</Invoice>".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private InvoiceRepository invoices;

    private MockMvc mvc;

    private static RequestBuilder upload(UUID tenantId, byte[] content) {
        return multipart("/api/documents")
                .file(file(content))
                .with(clerk(tenantId));
    }

    private static MockMultipartFile file(byte[] content) {
        return new MockMultipartFile("file", "rechnung-2041.xml", "application/xml", content);
    }

    private static byte[] resource(String name) throws IOException {
        try (InputStream in = DocumentUploadControllerTest.class.getResourceAsStream("/invoices/" + name)) {
            if (in == null) {
                throw new IllegalStateException("Missing test resource /invoices/" + name);
            }
            return in.readAllBytes();
        }
    }

    @BeforeEach
    void setUp() {
        mvc = mockMvc(context);
    }

    @Test
    void storesANewDocumentAndReturns201() throws Exception {
        UUID tenantId = newTenant();

        mvc.perform(upload(tenantId, XML))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.fileName").value("rechnung-2041.xml"))
                .andExpect(jsonPath("$.sizeBytes").value(XML.length))
                .andExpect(jsonPath("$.sha256").value(Sha256.hex(XML)))
                .andExpect(jsonPath("$.sourceFormat").value("UNKNOWN"));
    }

    @Test
    void createsAndValidatesAnInvoiceFromAnUploadedXRechnung() throws Exception {
        UUID tenantId = newTenant();

        Invoice invoice = uploadInvoice(tenantId, resource("xrechnung-ubl.xml"));

        assertThat(invoice.getInvoiceNumber()).isEqualTo("RE-2041");
        assertThat(invoice.getGrossAmount()).isEqualByComparingTo("1428.00");
        assertThat(invoice.getExtractionMethod()).isEqualTo(ExtractionMethod.STRUCTURED);
        assertThat(invoice.getLines()).hasSize(2);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.VALID);
        assertThat(invoice.getIssues()).isEmpty();
    }

    @Test
    void marksAnInvoiceWithWrongTotalsAsInvalid() throws Exception {
        String wrongTotal = new String(resource("xrechnung-ubl.xml"), StandardCharsets.UTF_8).replace(
                "<cbc:TaxInclusiveAmount currencyID=\"EUR\">1428.00</cbc:TaxInclusiveAmount>",
                "<cbc:TaxInclusiveAmount currencyID=\"EUR\">1500.00</cbc:TaxInclusiveAmount>");

        Invoice invoice = uploadInvoice(newTenant(), wrongTotal.getBytes(StandardCharsets.UTF_8));

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.INVALID);
        assertThat(invoice.getIssues()).extracting(ValidationIssue::getRuleId).contains("BR-CO-15");
    }

    @Test
    void sendsTheSameInvoiceInAnotherFormatToReview() throws Exception {
        UUID tenantId = newTenant();
        // Different files (different hash), but the same supplier and invoice number.
        uploadInvoice(tenantId, resource("xrechnung-ubl.xml"));

        Invoice second = uploadInvoice(tenantId, resource("xrechnung-cii.xml"));

        assertThat(second.getStatus()).isEqualTo(InvoiceStatus.NEEDS_REVIEW);
        assertThat(second.getIssues()).extracting(ValidationIssue::getRuleId).containsExactly("DT-POSSIBLE-DUPLICATE");
    }

    @Test
    void returnsTheExistingDocumentWhenTheSameFileIsUploadedAgain() throws Exception {
        UUID tenantId = newTenant();

        MvcResult first = mvc.perform(upload(tenantId, XML))
                .andExpect(status().isCreated())
                .andReturn();
        String firstId = JsonPath.read(first.getResponse().getContentAsString(), "$.id");

        mvc.perform(upload(tenantId, XML))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(firstId));
    }

    @Test
    void rejectsAnUnknownTenant() throws Exception {
        mvc.perform(upload(UUID.randomUUID(), XML))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsAnEmptyFile() throws Exception {
        mvc.perform(upload(newTenant(), new byte[0]))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsATokenWithoutTenant() throws Exception {
        // A valid login that does not name a company: no data access at all.
        mvc.perform(multipart("/api/documents").file(file(XML)).with(jwt()))
                .andExpect(status().isForbidden());
    }

    private Invoice uploadInvoice(UUID tenantId, byte[] content) throws Exception {
        MvcResult result = mvc.perform(upload(tenantId, content))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invoiceId").isNotEmpty())
                .andReturn();
        String invoiceId = JsonPath.read(result.getResponse().getContentAsString(), "$.invoiceId");
        return invoices.findByIdAndTenantId(UUID.fromString(invoiceId), tenantId).orElseThrow();
    }

    private UUID newTenant() {
        return tenants.save(new Tenant("Bäckerei Schulz")).getId();
    }
}