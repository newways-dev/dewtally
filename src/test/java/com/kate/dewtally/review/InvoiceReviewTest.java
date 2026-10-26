package com.kate.dewtally.review;

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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.kate.dewtally.security.TestUsers.*;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Review flow: the model misreads a line (gross instead of net), a clerk corrects it,
 * an approver accepts or rejects.
 */
@SpringBootTest(properties = {
        "dewtally.storage.local-root=target/test-documents",
        "dewtally.validation.kosit.enabled=false"
})
@Import(TestcontainersConfiguration.class)
class InvoiceReviewTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private TenantRepository tenants;

    @MockitoBean
    private LlmInvoiceExtractor llmExtractor;

    private MockMvc mvc;
    private UUID tenantId;

    // The full corrected invoice, as a review form would send it.
    private static String correction(long version, String grossAmount, String line1NetAmount) {
        return """
                {
                  "version": %d,
                  "data": {
                    "invoiceNumber": "RE-2041",
                    "issueDate": "2027-03-14",
                    "dueDate": "2027-04-13",
                    "currency": "EUR",
                    "supplierName": "Muster GmbH",
                    "supplierVatId": "DE123456789",
                    "buyerReference": "04011000-12345-34",
                    "iban": "DE89370400440532013000",
                    "netAmount": 1200.00,
                    "taxAmount": 228.00,
                    "grossAmount": %s
                  },
                  "lines": [
                    {"description": "Consulting", "quantity": 10, "unitCode": "Std.",
                     "unitPrice": 100.00, "netAmount": %s, "taxRate": 19},
                    {"description": "Reisekosten", "quantity": 1, "unitCode": "Stück",
                     "unitPrice": 200.00, "netAmount": 200.00, "taxRate": 19}
                  ]
                }
                """.formatted(version, grossAmount, line1NetAmount);
    }

    private static InvoiceExtraction extractionWithGrossLine() {
        InvoiceData data = new InvoiceData(
                "RE-2041", LocalDate.of(2027, 3, 14), LocalDate.of(2027, 4, 13), "EUR",
                "Muster GmbH", "DE123456789", "04011000-12345-34", "DE89370400440532013000",
                new BigDecimal("1200.00"), new BigDecimal("228.00"), new BigDecimal("1428.00"));
        List<ParsedLine> lines = List.of(
                new ParsedLine(1, "Consulting", new BigDecimal("10"), "Std.",
                        new BigDecimal("100.00"), new BigDecimal("1190.00"), new BigDecimal("19")),
                new ParsedLine(2, "Reisekosten", new BigDecimal("1"), "Stück",
                        new BigDecimal("200.00"), new BigDecimal("200.00"), new BigDecimal("19")));
        return new InvoiceExtraction(new ParsedInvoice(data, lines), 0.95);
    }

    @BeforeEach
    void setUp() {
        mvc = mockMvc(context);
        tenantId = tenants.save(new Tenant("Test GmbH " + UUID.randomUUID())).getId();
        // The model reads line 1 with VAT included: 1190.00 instead of 1000.00 → line sum mismatch.
        when(llmExtractor.extract(anyString())).thenReturn(extractionWithGrossLine());
    }

    @Test
    void correctionRevalidatesAndIsRecorded() throws Exception {
        String invoiceId = uploadPdf();
        assertStatus(invoiceId, "NEEDS_REVIEW");

        mvc.perform(put("/api/invoices/{id}/data", invoiceId)
                        .with(clerk(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(correction(version(invoiceId), "1428.00", "1000.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALID"))
                .andExpect(jsonPath("$.extractionMethod").value("MANUAL"))
                .andExpect(jsonPath("$.issues", hasSize(0)))
                .andExpect(jsonPath("$.lines[0].netAmount").value(1000.00));

        mvc.perform(get("/api/invoices/{id}/changes", invoiceId).with(clerk(tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].field", hasItem("lines[1].netAmount")))
                .andExpect(jsonPath("$[*].oldValue", hasItem("1190.00")))
                .andExpect(jsonPath("$[*].newValue", hasItem("1000.00")))
                .andExpect(jsonPath("$[*].field", hasItem("extractionMethod")))
                // The name comes from the token, not from anything the client sends.
                .andExpect(jsonPath("$[0].changedBy").value("anna"));
    }

    @Test
    void staleVersionIsRejected() throws Exception {
        String invoiceId = uploadPdf();
        long staleVersion = version(invoiceId) - 1;

        mvc.perform(put("/api/invoices/{id}/data", invoiceId)
                        .with(clerk(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(correction(staleVersion, "1428.00", "1000.00")))
                .andExpect(status().isConflict());
    }

    @Test
    void invoiceNeedingReviewCanBeAccepted() throws Exception {
        String invoiceId = uploadPdf();

        mvc.perform(post("/api/invoices/{id}/accept", invoiceId)
                        .with(approver(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"Checked against the paper invoice\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        mvc.perform(get("/api/invoices/{id}/changes", invoiceId).with(clerk(tenantId)))
                .andExpect(jsonPath("$[*].changeType", hasItem("ACCEPTED")))
                .andExpect(jsonPath("$[*].changedBy", hasItem("ben")));
    }

    @Test
    void manuallyEnteredErrorsMakeTheInvoiceInvalidAndBlockAcceptance() throws Exception {
        String invoiceId = uploadPdf();

        // Data confirmed by a person: 1200 + 228 != 1500 is now a real error → INVALID.
        mvc.perform(put("/api/invoices/{id}/data", invoiceId)
                        .with(clerk(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(correction(version(invoiceId), "1500.00", "1000.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INVALID"))
                .andExpect(jsonPath("$.issues[*].ruleId", hasItem("BR-CO-15")));

        mvc.perform(post("/api/invoices/{id}/accept", invoiceId).with(approver(tenantId)))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectionNeedsAReasonAndClosesTheInvoice() throws Exception {
        String invoiceId = uploadPdf();

        mvc.perform(post("/api/invoices/{id}/reject", invoiceId).with(approver(tenantId)))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/invoices/{id}/reject", invoiceId)
                        .with(approver(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"Duplicate of RE-2041\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        // A decided invoice cannot be changed any more.
        mvc.perform(put("/api/invoices/{id}/data", invoiceId)
                        .with(clerk(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(correction(version(invoiceId), "1428.00", "1000.00")))
                .andExpect(status().isConflict());
    }

    private String uploadPdf() throws Exception {
        byte[] pdf = TestPdfs.withText(
                "Muster GmbH, Muehlenweg 1, 20095 Hamburg",
                "Rechnung Nr. RE-2041 vom 14.03.2027",
                "Gesamtbetrag 1.428,00 EUR");
        MockMultipartFile file = new MockMultipartFile("file", "rechnung.pdf", "application/pdf", pdf);
        String body = mvc.perform(multipart("/api/documents").file(file).with(clerk(tenantId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.invoiceId");
    }

    private long version(String invoiceId) throws Exception {
        String body = mvc.perform(get("/api/invoices/{id}", invoiceId).with(clerk(tenantId)))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.version")).longValue();
    }

    private void assertStatus(String invoiceId, String expected) throws Exception {
        mvc.perform(get("/api/invoices/{id}", invoiceId).with(clerk(tenantId)))
                .andExpect(jsonPath("$.status").value(expected));
    }
}