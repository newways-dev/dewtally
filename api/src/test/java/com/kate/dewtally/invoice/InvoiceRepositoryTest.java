package com.kate.dewtally.invoice;

import com.kate.dewtally.TestcontainersConfiguration;
import com.kate.dewtally.document.InvoiceDocument;
import com.kate.dewtally.document.InvoiceDocumentRepository;
import com.kate.dewtally.document.SourceFormat;
import com.kate.dewtally.tenant.Tenant;
import com.kate.dewtally.tenant.TenantContext;
import com.kate.dewtally.tenant.TenantRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against a real PostgreSQL in Docker (Testcontainers) with the real Liquibase schema and Row-Level Security.
 * Every database call runs as a tenant, like in a real request.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class InvoiceRepositoryTest {

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private InvoiceDocumentRepository documents;

    @Autowired
    private InvoiceRepository invoices;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private TransactionTemplate transactions;

    @BeforeEach
    void setUp() {
        transactions = new TransactionTemplate(transactionManager);
    }

    @Test
    void savesInvoiceWithLinesAndIssuesAndLoadsItBack() {
        UUID tenantId = newTenant();

        inTenant(tenantId, () -> {
            Invoice invoice = new Invoice(tenantId, newDocument(tenantId), ExtractionMethod.STRUCTURED, TestInvoices.sampleData());
            invoice.addLine(TestInvoices.travelLine(2));
            invoice.addLine(TestInvoices.consultingLine(1));
            invoice.addIssue(Severity.WARNING, "DT-POSSIBLE-DUPLICATE", "Same supplier and invoice number already exist");
            invoice.applyValidationResult();

            invoices.saveAndFlush(invoice);
            // Forget all cached objects, so the next read really comes from the database.
            entityManager.clear();

            Invoice loaded = invoices.findByIdAndTenantId(invoice.getId(), tenantId).orElseThrow();
            assertThat(loaded.getStatus()).isEqualTo(InvoiceStatus.NEEDS_REVIEW);
            assertThat(loaded.getGrossAmount()).isEqualByComparingTo("1428.00");
            assertThat(loaded.getLines()).extracting(InvoiceLine::getLineNo).containsExactly(1, 2);
            assertThat(loaded.getIssues())
                    .singleElement()
                    .extracting(ValidationIssue::getRuleId)
                    .isEqualTo("DT-POSSIBLE-DUPLICATE");
            return null;
        });
    }

    @Test
    void doesNotReturnAnInvoiceOfAnotherTenant() {
        UUID owner = newTenant();
        UUID otherTenant = newTenant();
        UUID invoiceId = inTenant(owner, () -> invoices.saveAndFlush(
                new Invoice(owner, newDocument(owner), ExtractionMethod.STRUCTURED, TestInvoices.sampleData())).getId());

        assertThat(inTenant(otherTenant, () -> invoices.findByIdAndTenantId(invoiceId, otherTenant))).isEmpty();
        // Row-Level Security: even a lookup by id alone, without any tenant filter, finds nothing.
        assertThat(inTenant(otherTenant, () -> invoices.findById(invoiceId))).isEmpty();
    }

    @Test
    void allowsOnlyOneInvoicePerDocument() {
        UUID tenantId = newTenant();
        UUID documentId = inTenant(tenantId, () -> newDocument(tenantId));
        inTenant(tenantId, () -> invoices.saveAndFlush(
                new Invoice(tenantId, documentId, ExtractionMethod.STRUCTURED, TestInvoices.sampleData())));

        assertThatThrownBy(() -> inTenant(tenantId, () -> invoices.saveAndFlush(
                new Invoice(tenantId, documentId, ExtractionMethod.STRUCTURED, TestInvoices.sampleData()))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID newTenant() {
        return tenants.save(new Tenant("Tenant " + UUID.randomUUID())).getId();
    }

    // Call inside inTenant: the document is saved as that tenant.
    private UUID newDocument(UUID tenantId) {
        String randomHash = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        InvoiceDocument document = new InvoiceDocument(
                tenantId, "rechnung.xml", "application/xml", 1_024,
                randomHash, "local/" + UUID.randomUUID(), SourceFormat.CII, null);
        return documents.saveAndFlush(document).getId();
    }

    private <T> T inTenant(UUID tenantId, Supplier<T> work) {
        return TenantContext.callAs(tenantId, () -> transactions.execute(status -> work.get()));
    }
}