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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Upload of plain PDFs. The language model is replaced by a mock: tests stay free, fast and repeatable.
 * The real model is tested separately in LlmInvoiceExtractorManualTest.
 */
@SpringBootTest(properties = {
        "dewtally.storage.local-root=target/test-documents",
        "dewtally.validation.kosit.enabled=false",
        "dewtally.extraction.min-confidence=0.8"
})
@Import(TestcontainersConfiguration.class)
class PdfUploadTest {

    private static final String TENANT_HEADER = "X-Tenant-Id";

    private static final byte[] TEXT_PDF = TestPdfs.withText(
            "Muster GmbH, Muehlenweg 1, 20095 Hamburg",
            "Rechnung Nr. RE-2041 vom 14.03.2027",
            "Gesamtbetrag 1.428,00 EUR");

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private TenantRepository tenants;

    @MockitoBean
    private LlmInvoiceExtractor llmExtractor;

    private MockMvc mvc;
    private UUID tenantId;

    // What the model would return for our sample invoice; only the gross amount and confidence vary.
    private static InvoiceExtraction extraction(BigDecimal grossAmount, double confidence) {
        InvoiceData data = new InvoiceData(
                "RE-2041",
                LocalDate.of(2027, 3, 14),
                LocalDate.of(2027, 4, 13),
                "EUR",
                "Muster GmbH",
                "DE123456789",
                "04011000-12345-34",
                "DE89370400440532013000",
                new BigDecimal("1200.00"),
                new BigDecimal("228.00"),
                grossAmount);
        List<ParsedLine> lines = List.of(
                new ParsedLine(1, "Consulting", new BigDecimal("10"), "Std.",
                        new BigDecimal("100.00"), new BigDecimal("1000.00"), new BigDecimal("19")),
                new ParsedLine(2, "Reisekosten", new BigDecimal("1"), "Stück",
                        new BigDecimal("200.00"), new BigDecimal("200.00"), new BigDecimal("19")));
        return new InvoiceExtraction(new ParsedInvoice(data, lines), confidence);
    }

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        tenantId = tenants.save(new Tenant("Test GmbH " + UUID.randomUUID())).getId();
    }

    @Test
    void confidentConsistentExtractionIsValid() throws Exception {
        when(llmExtractor.extract(anyString())).thenReturn(extraction(new BigDecimal("1428.00"), 0.95));

        getInvoice(upload(TEXT_PDF))
                .andExpect(jsonPath("$.status").value("VALID"))
                .andExpect(jsonPath("$.extractionMethod").value("LLM"))
                .andExpect(jsonPath("$.extractionConfidence").value(0.95))
                .andExpect(jsonPath("$.invoiceNumber").value("RE-2041"));
    }

    @Test
    void lowConfidenceGoesToReview() throws Exception {
        when(llmExtractor.extract(anyString())).thenReturn(extraction(new BigDecimal("1428.00"), 0.4));

        getInvoice(upload(TEXT_PDF))
                .andExpect(jsonPath("$.status").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.issues[*].ruleId", hasItem("DT-LOW-CONFIDENCE")));
    }

    @Test
    void ruleErrorsOnExtractedDataGoToReviewNotInvalid() throws Exception {
        // 1200 + 228 != 1500: with XML this is INVALID; with a model it may be a reading mistake.
        when(llmExtractor.extract(anyString())).thenReturn(extraction(new BigDecimal("1500.00"), 0.95));

        getInvoice(upload(TEXT_PDF))
                .andExpect(jsonPath("$.status").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.issues[*].ruleId", hasItem("BR-CO-15")));
    }

    @Test
    void failedExtractionGoesToReview() throws Exception {
        when(llmExtractor.extract(anyString())).thenThrow(new IllegalStateException("model unavailable"));

        getInvoice(upload(TEXT_PDF))
                .andExpect(jsonPath("$.status").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.issues[*].ruleId", hasItem("DT-EXTRACTION-FAILED")));
    }

    @Test
    void scansGoToReviewWithoutCallingTheModel() throws Exception {
        getInvoice(upload(TestPdfs.scan()))
                .andExpect(jsonPath("$.status").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.issues[*].ruleId", hasItem("DT-NO-TEXT-LAYER")));

        verify(llmExtractor, never()).extract(anyString());
    }

    private String upload(byte[] pdf) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "rechnung.pdf", "application/pdf", pdf);
        String body = mvc.perform(multipart("/api/documents").file(file).header(TENANT_HEADER, tenantId))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.invoiceId");
    }

    private ResultActions getInvoice(String invoiceId) throws Exception {
        return mvc.perform(get("/api/invoices/{id}", invoiceId).header(TENANT_HEADER, tenantId))
                .andExpect(status().isOk());
    }
}