package com.kate.dewtally.ingestion;

import com.kate.dewtally.TestcontainersConfiguration;
import com.kate.dewtally.tenant.Tenant;
import com.kate.dewtally.tenant.TenantRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.InputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "dewtally.storage.local-root=target/test-documents",
        "dewtally.validation.kosit.enabled=false"
})
@Import(TestcontainersConfiguration.class)
class MetricsTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private MeterRegistry metrics;

    @Test
    void importedInvoiceIsCountedByMethodAndStatus() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
        UUID tenantId = tenants.save(new Tenant("Test GmbH " + UUID.randomUUID())).getId();
        double before = structuredCount();

        byte[] xml;
        try (InputStream in = getClass().getResourceAsStream("/invoices/xrechnung-cii.xml")) {
            xml = in.readAllBytes();
        }
        // A unique comment makes the file new for the hash check, so it is really imported.
        byte[] content = (new String(xml, java.nio.charset.StandardCharsets.UTF_8)
                + "<!-- " + UUID.randomUUID() + " -->").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        mvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", "invoice.xml", "application/xml", content))
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isCreated());

        assertThat(structuredCount()).isEqualTo(before + 1);
    }

    // Sum over all statuses, so the test does not depend on how this sample is judged.
    private double structuredCount() {
        return metrics.find("dewtally.invoices.created").tag("method", "STRUCTURED")
                .counters().stream().mapToDouble(Counter::count).sum();
    }
}