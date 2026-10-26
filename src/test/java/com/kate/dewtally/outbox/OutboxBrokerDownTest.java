package com.kate.dewtally.outbox;

import com.jayway.jsonpath.JsonPath;
import com.kate.dewtally.TestcontainersConfiguration;
import com.kate.dewtally.tenant.Tenant;
import com.kate.dewtally.tenant.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import static com.kate.dewtally.security.TestUsers.clerk;
import static com.kate.dewtally.security.TestUsers.mockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RabbitMQ is unreachable (nothing listens on port 1). The upload must still succeed,
 * and the message must wait in the outbox instead of getting lost.
 */
@SpringBootTest(properties = {
        "dewtally.processing.async=true",
        "dewtally.storage.local-root=target/test-documents",
        "dewtally.validation.kosit.enabled=false",
        "spring.rabbitmq.host=localhost",
        "spring.rabbitmq.port=1",
        "dewtally.outbox.poll-interval-ms=200"
})
@Import(TestcontainersConfiguration.class)
class OutboxBrokerDownTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private OutboxRepository outbox;

    private static byte[] resource(String name) throws IOException {
        try (InputStream in = OutboxBrokerDownTest.class.getResourceAsStream("/invoices/" + name)) {
            if (in == null) {
                throw new IllegalStateException("Missing test resource /invoices/" + name);
            }
            return in.readAllBytes();
        }
    }

    @Test
    void uploadSucceedsAndTheMessageWaitsInTheOutbox() throws Exception {
        MockMvc mvc = mockMvc(context);
        UUID tenantId = tenants.save(new Tenant("Test GmbH " + UUID.randomUUID())).getId();
        MockMultipartFile file = new MockMultipartFile(
                "file", "xrechnung-cii.xml", "application/xml", resource("xrechnung-cii.xml"));

        String body = mvc.perform(multipart("/api/documents").file(file).with(clerk(tenantId)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String documentId = JsonPath.read(body, "$.id");

        // The relay tries, fails and keeps the message.
        OutboxMessage message = waitForFailedAttempt(documentId);
        assertThat(message.getDestination()).isEqualTo("dewtally.documents.received");
        assertThat(message.getSentAt()).isNull();
        assertThat(message.getLastError()).isNotBlank();

        mvc.perform(get("/api/documents/{id}", documentId).with(clerk(tenantId)))
                .andExpect(jsonPath("$.processingStatus").value("RECEIVED"));
    }

    private OutboxMessage waitForFailedAttempt(String documentId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            List<OutboxMessage> messages = outbox.findByPayload(documentId);
            if (messages.size() == 1 && messages.getFirst().getAttempts() > 0) {
                return messages.getFirst();
            }
            Thread.sleep(200);
        }
        throw new AssertionError("The relay did not try to send the message for document " + documentId);
    }
}