package com.kate.dewtally.document;

import com.kate.dewtally.TestcontainersConfiguration;
import com.kate.dewtally.tenant.Tenant;
import com.kate.dewtally.tenant.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against a real PostgreSQL in Docker (Testcontainers) with the real Liquibase schema.
 * Each test runs in a transaction that is rolled back afterwards.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class InvoiceDocumentRepositoryTest {

    private static final String SAME_HASH = "a".repeat(64);
    private static final String PROFILE = "urn:cen.eu:en16931:2017";

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private InvoiceDocumentRepository documents;

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

    @Test
    void savesDocumentAndFindsItByTenantAndHash() {
        Tenant tenant = tenants.save(new Tenant("Muster GmbH"));

        documents.saveAndFlush(document(tenant.getId(), SAME_HASH));

        assertThat(documents.findByTenantIdAndSha256(tenant.getId(), SAME_HASH))
                .isPresent()
                .get()
                .satisfies(found -> {
                    assertThat(found.getFileName()).isEqualTo("rechnung-2041.xml");
                    assertThat(found.getSourceFormat()).isEqualTo(SourceFormat.CII);
                    assertThat(found.getProfile()).isEqualTo(PROFILE);
                    assertThat(found.getUploadedAt()).isNotNull();
                });
    }

    @Test
    void rejectsTheSameFileTwiceForTheSameTenant() {
        Tenant tenant = tenants.save(new Tenant("Muster GmbH"));
        documents.saveAndFlush(document(tenant.getId(), SAME_HASH));

        assertThatThrownBy(() -> documents.saveAndFlush(document(tenant.getId(), SAME_HASH)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsTheSameFileForDifferentTenants() {
        Tenant first = tenants.save(new Tenant("Muster GmbH"));
        Tenant second = tenants.save(new Tenant("Beispiel AG"));

        documents.saveAndFlush(document(first.getId(), SAME_HASH));
        documents.saveAndFlush(document(second.getId(), SAME_HASH));

        assertThat(documents.findByTenantIdAndSha256(first.getId(), SAME_HASH)).isPresent();
        assertThat(documents.findByTenantIdAndSha256(second.getId(), SAME_HASH)).isPresent();
    }
}