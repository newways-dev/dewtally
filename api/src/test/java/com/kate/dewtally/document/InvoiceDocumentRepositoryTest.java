package com.kate.dewtally.document;

import com.kate.dewtally.TestcontainersConfiguration;
import com.kate.dewtally.tenant.Tenant;
import com.kate.dewtally.tenant.TenantContext;
import com.kate.dewtally.tenant.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against a real PostgreSQL in Docker (Testcontainers) with the real Liquibase schema and Row-Level Security.
 * Every database call runs as a tenant, like in a real request. Each test uses new tenants, so tests never
 * see each other's data.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class InvoiceDocumentRepositoryTest {

    private static final String SAME_HASH = "a".repeat(64);
    private static final String PROFILE = "urn:cen.eu:en16931:2017";

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private InvoiceDocumentRepository documents;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactions;

    private static InvoiceDocument document(UUID tenantId, String sha256) {
        return new InvoiceDocument(
                tenantId,
                "rechnung-2041.xml",
                "application/xml",
                1_024,
                sha256,
                "local/" + UUID.randomUUID(),
                SourceFormat.CII,
                PROFILE);
    }

    @BeforeEach
    void setUp() {
        transactions = new TransactionTemplate(transactionManager);
    }

    @Test
    void savesDocumentAndFindsItByTenantAndHash() {
        UUID tenantId = newTenant("Muster GmbH");
        inTenant(tenantId, () -> documents.saveAndFlush(document(tenantId, SAME_HASH)));

        Optional<InvoiceDocument> found = inTenant(tenantId, () -> documents.findByTenantIdAndSha256(tenantId, SAME_HASH));

        assertThat(found)
                .isPresent()
                .get()
                .satisfies(document -> {
                    assertThat(document.getFileName()).isEqualTo("rechnung-2041.xml");
                    assertThat(document.getSourceFormat()).isEqualTo(SourceFormat.CII);
                    assertThat(document.getProfile()).isEqualTo(PROFILE);
                    assertThat(document.getUploadedAt()).isNotNull();
                });
    }

    @Test
    void rejectsTheSameFileTwiceForTheSameTenant() {
        UUID tenantId = newTenant("Muster GmbH");
        inTenant(tenantId, () -> documents.saveAndFlush(document(tenantId, SAME_HASH)));

        assertThatThrownBy(() -> inTenant(tenantId, () -> documents.saveAndFlush(document(tenantId, SAME_HASH))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsTheSameFileForDifferentTenants() {
        UUID first = newTenant("Muster GmbH");
        UUID second = newTenant("Beispiel AG");

        inTenant(first, () -> documents.saveAndFlush(document(first, SAME_HASH)));
        inTenant(second, () -> documents.saveAndFlush(document(second, SAME_HASH)));

        assertThat(inTenant(first, () -> documents.findByTenantIdAndSha256(first, SAME_HASH))).isPresent();
        assertThat(inTenant(second, () -> documents.findByTenantIdAndSha256(second, SAME_HASH))).isPresent();
        // Row-Level Security: even when asked for the other tenant's document, the database shows nothing.
        assertThat(inTenant(first, () -> documents.findByTenantIdAndSha256(second, SAME_HASH))).isEmpty();
    }

    private UUID newTenant(String name) {
        return tenants.save(new Tenant(name)).getId();
    }

    private <T> T inTenant(UUID tenantId, Supplier<T> work) {
        return TenantContext.callAs(tenantId, () -> transactions.execute(status -> work.get()));
    }
}