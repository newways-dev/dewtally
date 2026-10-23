package com.kate.dewtally.ingestion;

import com.jayway.jsonpath.JsonPath;
import com.kate.dewtally.TestcontainersConfiguration;
import com.kate.dewtally.extraction.InvoiceExtraction;
import com.kate.dewtally.extraction.LlmInvoiceExtractor;
import com.kate.dewtally.extraction.TestPdfs;
import com.kate.dewtally.invoice.InvoiceData;
import com.kate.dewtally.parsing.ParsedInvoice;
import com.kate.dewtally.parsing.ParsedLine;
import com.kate.dewtally.tenant.Tenant;
import com.kate.dewtally.tenant.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A failure during processing must not leave a document without an invoice forever:
 * nothing half-done is saved, and uploading the same file again processes it again.
 */
@SpringBootTest(properties = {
        "dewtally.storage.local-root=target/test-documents",
        "dewtally.validation.kosit.enabled=false"
})
@Import(TestcontainersConfiguration.class)
class ReprocessingTest {

    private static final String TENANT_HEADER = "X-Tenant-Id";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private TenantRepository tenants;

    @MockitoBean
    private LlmInvoiceExtractor llmExtractor;

    private MockMvc mvc;
    private UUID tenantId;

    private static InvoiceExtraction extraction(String currency) {
        InvoiceData data = new InvoiceData(
                "RE-2041", LocalDate.of(2027, 3, 14), LocalDate.of(2027, 4, 13), currency,
                "Muster GmbH", "DE123456789", "04011000-12345-34", "DE89370400440532013000",
                new BigDecimal("1200.00"), new BigDecimal("228.00"), new BigDecimal("1428.00"));
        List<ParsedLine> lines = List.of(
                new ParsedLine(1, "Consulting", new BigDecimal("10"), "Std.",
                        new BigDecimal("100.00"), new BigDecimal("1000.00"), new BigDecimal("19")),
                new ParsedLine(2, "Reisekosten", new BigDecimal("1"), "Stück",
                        new BigDecimal("200.00"), new BigDecimal("200.00"), new BigDecimal("19")));
        return new InvoiceExtraction(new ParsedInvoice(data, lines), 0.95);
    }

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        tenantId = tenants.save(new Tenant("Test GmbH " + UUID.randomUUID())).getId();
    }

    @Test
    void failedProcessingIsRetriedOnTheNextUpload() throws Exception {
        // First answer cannot be stored ("EURO" does not fit the 3-character currency column),
        // the second one is fine.
        when(llmExtractor.extract(anyString()))
                .thenReturn(extraction("EURO"))
                .thenReturn(extraction("EUR"));

        byte[] pdf = TestPdfs.withText(
                "Muster GmbH, Muehlenweg 1, 20095 Hamburg",
                "Rechnung Nr. RE-2041 vom 14.03.2027",
                "Gesamtbetrag 1.428,00 EUR");
        MockMultipartFile file = new MockMultipartFile("file", "rechnung.pdf", "application/pdf", pdf);

        // 1st upload: document is kept, but no (half-saved) invoice exists.
        mvc.perform(multipart("/api/documents").file(file).header(TENANT_HEADER, tenantId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invoiceId").value(nullValue()));

        // 2nd upload of the same file: same document, processed again, now with an invoice.
        String body = mvc.perform(multipart("/api/documents").file(file).header(TENANT_HEADER, tenantId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String invoiceId = JsonPath.read(body, "$.invoiceId");

        mvc.perform(get("/api/invoices/{id}", invoiceId).header(TENANT_HEADER, tenantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALID"))
                .andExpect(jsonPath("$.currency").value("EUR"));
    }
}