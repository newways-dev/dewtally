package com.kate.dewtally.supplier;

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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import static com.kate.dewtally.security.TestUsers.*;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Supplier master data protects against invoice fraud. The sample invoice is from Muster GmbH
 * (VAT ID DE123456789) with IBAN DE89370400440532013000.
 * The admin (carla) maintains suppliers; a second person (ben, approver) verifies IBANs.
 */
@SpringBootTest(properties = {
        "dewtally.storage.local-root=target/test-documents",
        "dewtally.validation.kosit.enabled=false"
})
@Import(TestcontainersConfiguration.class)
class SupplierFraudCheckTest {

    private static final String INVOICE_IBAN = "DE89370400440532013000";
    private static final String OTHER_IBAN = "DE02120300000000202051"; // valid, but not the one on the invoice

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private TenantRepository tenants;

    private MockMvc mvc;
    private UUID tenantId;

    private static byte[] resource(String name) throws IOException {
        try (InputStream in = SupplierFraudCheckTest.class.getResourceAsStream("/invoices/" + name)) {
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
    void ibanNeedsASecondPersonToBeVerified() throws Exception {
        String supplierId = createSupplier("Muster GmbH", "DE123456789", INVOICE_IBAN);

        // carla added the IBAN, so carla may not verify it.
        mvc.perform(post("/api/suppliers/{id}/ibans/{iban}/verify", supplierId, INVOICE_IBAN)
                        .with(admin(tenantId)))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/suppliers/{id}/ibans/{iban}/verify", supplierId, INVOICE_IBAN)
                        .with(approver(tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ibans[0].status").value("VERIFIED"))
                .andExpect(jsonPath("$.ibans[0].addedBy").value("carla"))
                .andExpect(jsonPath("$.ibans[0].verifiedBy").value("ben"));
    }

    @Test
    void invalidIbanIsRejected() throws Exception {
        mvc.perform(post("/api/suppliers")
                        .with(admin(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Muster GmbH\",\"iban\":\"DE89370400440532013001\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void knownSupplierWithVerifiedIbanRaisesNoSupplierFindings() throws Exception {
        String supplierId = createSupplier("Muster GmbH", "DE123456789", INVOICE_IBAN);
        verify(supplierId, INVOICE_IBAN, approver(tenantId));

        getInvoice(uploadSampleInvoice())
                .andExpect(jsonPath("$.status").value("VALID"))
                .andExpect(jsonPath("$.issues[*].ruleId", not(hasItem("DT-IBAN-CHANGED"))));
    }

    @Test
    void changedIbanIsHighRiskAndNeedsExplicitConfirmation() throws Exception {
        String supplierId = createSupplier("Muster GmbH", "DE123456789", OTHER_IBAN);
        verify(supplierId, OTHER_IBAN, approver(tenantId));

        String invoiceId = uploadSampleInvoice();
        getInvoice(invoiceId)
                .andExpect(jsonPath("$.status").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.issues[*].ruleId", hasItem("DT-IBAN-CHANGED")));

        // A normal click on "accept" is not enough.
        mvc.perform(post("/api/invoices/{id}/accept", invoiceId).with(approver(tenantId)))
                .andExpect(status().isConflict());

        // Confirming without saying how is not enough either.
        mvc.perform(post("/api/invoices/{id}/accept", invoiceId)
                        .with(approver(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ibanConfirmed\":true}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/invoices/{id}/accept", invoiceId)
                        .with(approver(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ibanConfirmed\":true,\"comment\":\"Herr Muster telefonisch unter bekannter Nummer bestätigt\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    void invoiceFromUnknownSupplierNeedsReview() throws Exception {
        // The tenant maintains master data, but this invoice's supplier is not in it.
        createSupplier("Andere AG", "DE999999999", null);

        getInvoice(uploadSampleInvoice())
                .andExpect(jsonPath("$.status").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.issues[*].ruleId", hasItem("DT-UNKNOWN-SUPPLIER")));
    }

    // Created by the admin (carla); a first IBAN is added by her as well.
    private String createSupplier(String name, String vatId, String iban) throws Exception {
        String ibanJson = iban == null ? "" : ",\"iban\":\"" + iban + "\"";
        String body = mvc.perform(post("/api/suppliers")
                        .with(admin(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"vatId\":\"" + vatId + "\"" + ibanJson + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private void verify(String supplierId, String iban, RequestPostProcessor user) throws Exception {
        mvc.perform(post("/api/suppliers/{id}/ibans/{iban}/verify", supplierId, iban).with(user))
                .andExpect(status().isOk());
    }

    private String uploadSampleInvoice() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "xrechnung-ubl.xml", "application/xml", resource("xrechnung-ubl.xml"));
        String body = mvc.perform(multipart("/api/documents").file(file).with(clerk(tenantId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.invoiceId");
    }

    private ResultActions getInvoice(String invoiceId) throws Exception {
        return mvc.perform(get("/api/invoices/{id}", invoiceId).with(clerk(tenantId)))
                .andExpect(status().isOk());
    }
}