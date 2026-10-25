package com.kate.dewtally.ingestion;

import com.jayway.jsonpath.JsonPath;
import com.kate.dewtally.TestcontainersConfiguration;
import com.kate.dewtally.tenant.Tenant;
import com.kate.dewtally.tenant.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Upload with background processing through a real RabbitMQ.
 */
@SpringBootTest(properties = {
        "dewtally.processing.async=true",
        "dewtally.storage.local-root=target/test-documents",
        "dewtally.validation.kosit.enabled=false"
})
@Import({TestcontainersConfiguration.class, AsyncProcessingTest.Rabbit.class})
class AsyncProcessingTest {

    private static final String TENANT_HEADER = "X-Tenant-Id";
    @Autowired
    private WebApplicationContext context;
    @Autowired
    private TenantRepository tenants;
    private MockMvc mvc;
    private UUID tenantId;

    private static byte[] resource(String name) throws IOException {
        try (InputStream in = AsyncProcessingTest.class.getResourceAsStream("/invoices/" + name)) {
            if (in == null) {
                throw new IllegalStateException("Missing test resource /invoices/" + name);
            }
            return in.readAllBytes();
        }
    }

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        tenantId = tenants.save(new Tenant("Test GmbH " + UUID.randomUUID())).getId();
    }

    @Test
    void uploadIsAcceptedAndProcessedInTheBackground() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "xrechnung-ubl.xml", "application/xml", resource("xrechnung-ubl.xml"));

        String body = mvc.perform(multipart("/api/documents").file(file).header(TENANT_HEADER, tenantId))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.processingStatus").value("RECEIVED"))
                .andReturn().getResponse().getContentAsString();
        String documentId = JsonPath.read(body, "$.id");

        String processed = waitUntilProcessed(documentId);
        assertThat((String) JsonPath.read(processed, "$.processingStatus")).isEqualTo("PROCESSED");
        String invoiceId = JsonPath.read(processed, "$.invoiceId");
        assertThat(invoiceId).isNotNull();

        mvc.perform(get("/api/invoices/{id}", invoiceId).header(TENANT_HEADER, tenantId))
                .andExpect(status().isOk());
    }

    @Test
    void documentOfAnotherTenantIsNotFound() throws Exception {
        UUID otherTenant = tenants.save(new Tenant("Other GmbH " + UUID.randomUUID())).getId();

        mvc.perform(get("/api/documents/{id}", UUID.randomUUID()).header(TENANT_HEADER, otherTenant))
                .andExpect(status().isNotFound());
    }

    // Polls the document until the background processing has finished (or 30 seconds have passed).
    private String waitUntilProcessed(String documentId) throws Exception {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            String body = mvc.perform(get("/api/documents/{id}", documentId).header(TENANT_HEADER, tenantId))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            if (!"RECEIVED".equals(JsonPath.read(body, "$.processingStatus"))) {
                return body;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Document " + documentId + " was not processed within 30 seconds");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Rabbit {

        @Bean
        @ServiceConnection
        RabbitMQContainer rabbitContainer() {
            return new RabbitMQContainer(DockerImageName.parse("rabbitmq:4-management"));
        }
    }
}