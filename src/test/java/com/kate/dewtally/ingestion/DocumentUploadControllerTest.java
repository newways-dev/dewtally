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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Calls the real HTTP endpoint (without a network) against a real PostgreSQL.
 * Uploaded files go to target/, which "mvn clean" removes.
 */
@SpringBootTest(properties = "dewtally.storage.local-root=target/test-documents")
@Import(TestcontainersConfiguration.class)
@Transactional
class DocumentUploadControllerTest {

    private static final byte[] XML = "<Invoice>RE-2041</Invoice>".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private TenantRepository tenants;

    private MockMvc mvc;

    private static RequestBuilder upload(UUID tenantId, byte[] content) {
        return multipart("/api/documents")
                .file(file(content))
                .header(DocumentUploadController.TENANT_HEADER, tenantId);
    }

    private static MockMultipartFile file(byte[] content) {
        return new MockMultipartFile("file", "rechnung-2041.xml", "application/xml", content);
    }

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
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
    void rejectsAnUploadWithoutTenantHeader() throws Exception {
        mvc.perform(multipart("/api/documents").file(file(XML)))
                .andExpect(status().isBadRequest());
    }

    private UUID newTenant() {
        return tenants.save(new Tenant("Bäckerei Schulz")).getId();
    }
}