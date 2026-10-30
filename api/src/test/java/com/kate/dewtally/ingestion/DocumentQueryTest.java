package com.kate.dewtally.ingestion;

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

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import static com.kate.dewtally.security.TestUsers.clerk;
import static com.kate.dewtally.security.TestUsers.mockMvc;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The list of recent uploads and the original files, as the web UI uses them.
 */
@SpringBootTest(properties = {
        "dewtally.storage.local-root=target/test-documents",
        "dewtally.validation.kosit.enabled=false"
})
@Import(TestcontainersConfiguration.class)
class DocumentQueryTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private TenantRepository tenants;

    private MockMvc mvc;

    private static byte[] resource(String name) throws IOException {
        try (InputStream in = DocumentQueryTest.class.getResourceAsStream("/invoices/" + name)) {
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
    void listsOnlyTheTenantsDocumentsNewestFirst() throws Exception {
        UUID tenantId = newTenant();
        UUID otherTenant = newTenant();
        upload(tenantId, "first.xml", resource("xrechnung-ubl.xml"));
        upload(tenantId, "second.xml", resource("xrechnung-cii.xml"));
        upload(otherTenant, "foreign.xml", resource("xrechnung-ubl.xml"));

        mvc.perform(get("/api/documents").with(clerk(tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].fileName").value("second.xml"))
                .andExpect(jsonPath("$.items[1].fileName").value("first.xml"))
                .andExpect(jsonPath("$.items[0].processingStatus").value("PROCESSED"))
                .andExpect(jsonPath("$.items[0].invoiceId").isNotEmpty());
    }

    @Test
    void returnsTheOriginalFileSafelyForDisplay() throws Exception {
        UUID tenantId = newTenant();
        byte[] xml = resource("xrechnung-ubl.xml");
        String documentId = upload(tenantId, "rechnung.xml", xml);

        mvc.perform(get("/api/documents/{id}/content", documentId).with(clerk(tenantId)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_XML))
                .andExpect(content().bytes(xml))
                .andExpect(header().string("Content-Disposition", containsString("inline")))
                .andExpect(header().string("Content-Security-Policy", "sandbox"))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    void anotherTenantCannotReadTheFile() throws Exception {
        UUID owner = newTenant();
        UUID otherTenant = newTenant();
        String documentId = upload(owner, "rechnung.xml", resource("xrechnung-ubl.xml"));

        mvc.perform(get("/api/documents/{id}/content", documentId).with(clerk(otherTenant)))
                .andExpect(status().isNotFound());
    }

    // Uploads synchronously (tests run without the queue) and returns the document id.
    private String upload(UUID tenantId, String fileName, byte[] content) throws Exception {
        String body = mvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", fileName, "application/xml", content))
                        .with(clerk(tenantId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private UUID newTenant() {
        return tenants.save(new Tenant("Test GmbH " + UUID.randomUUID())).getId();
    }
}