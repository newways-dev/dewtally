package com.kate.dewtally.ingestion;

import com.jayway.jsonpath.JsonPath;
import com.kate.dewtally.TestcontainersConfiguration;
import com.kate.dewtally.tenant.Tenant;
import com.kate.dewtally.tenant.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Messages that keep failing end up in the dead letter queue instead of blocking the queue or getting lost.
 * Retries are made fast here (2 attempts, 100 ms) to keep the test short.
 */
@SpringBootTest(properties = {
        "dewtally.processing.async=true",
        "dewtally.storage.local-root=target/test-documents",
        "dewtally.validation.kosit.enabled=false",
        "dewtally.outbox.poll-interval-ms=200",
        "spring.rabbitmq.listener.simple.retry.max-attempts=2",
        "spring.rabbitmq.listener.simple.retry.initial-interval=100ms",
        "spring.rabbitmq.listener.simple.retry.multiplier=1"
})
@Import({TestcontainersConfiguration.class, AsyncProcessingTest.Rabbit.class})
class DeadLetterTest {

    private static final String TENANT_HEADER = "X-Tenant-Id";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private RabbitTemplate rabbit;

    // The real processor, with single methods replaced where a test says so.
    @MockitoSpyBean
    private DocumentProcessor processor;

    private MockMvc mvc;
    private UUID tenantId;

    private static byte[] resource(String name) throws IOException {
        try (InputStream in = DeadLetterTest.class.getResourceAsStream("/invoices/" + name)) {
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
    void documentThatKeepsFailingTemporarilyEndsInTheDeadLetterQueueAndIsMarkedFailed() throws Exception {
        // Simulates e.g. a database that stays unavailable for every attempt.
        doThrow(new TransientProcessingException(UUID.randomUUID(), new RuntimeException("database unavailable")))
                .when(processor).process(any(UUID.class));

        String documentId = upload();

        assertThat(waitForDeadLetter(documentId)).as("message in the dead letter queue").isNotNull();
        verify(processor, atLeast(2)).process(UUID.fromString(documentId));
        assertThat(waitForStatus(documentId, "FAILED")).isTrue();
    }

    @Test
    void poisonMessageEndsInTheDeadLetterQueue() {
        // Not a document id at all: the listener can never handle it.
        rabbit.convertAndSend(ProcessingQueue.NAME, "not-a-document-id");

        assertThat(waitForDeadLetter("not-a-document-id")).isNotNull();
    }

    private String upload() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "xrechnung-ubl.xml", "application/xml", resource("xrechnung-ubl.xml"));
        String body = mvc.perform(multipart("/api/documents").file(file).header(TENANT_HEADER, tenantId))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    // Reads the dead letter queue until the expected message shows up (or 20 seconds have passed).
    private Message waitForDeadLetter(String payload) {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            Message message = rabbit.receive(ProcessingQueue.DEAD_LETTER, 1_000);
            if (message != null && payload.equals(new String(message.getBody(), StandardCharsets.UTF_8))) {
                return message;
            }
        }
        return null;
    }

    private boolean waitForStatus(String documentId, String expected) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            String body = mvc.perform(get("/api/documents/{id}", documentId).header(TENANT_HEADER, tenantId))
                    .andReturn().getResponse().getContentAsString();
            if (expected.equals(JsonPath.read(body, "$.processingStatus"))) {
                return true;
            }
            Thread.sleep(200);
        }
        return false;
    }
}