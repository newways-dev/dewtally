package com.kate.dewtally.tenant;

import com.kate.dewtally.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PostgreSQL itself keeps tenants apart: every query here deliberately has NO "WHERE tenant_id = ...".
 * If the application ever forgets the filter, the database still only returns the current tenant's rows.
 */
@SpringBootTest(properties = {
        "dewtally.storage.local-root=target/test-documents",
        "dewtally.validation.kosit.enabled=false"
})
@Import(TestcontainersConfiguration.class)
class RowLevelSecurityTest {

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private TransactionTemplate transactions;
    private UUID tenantA;
    private UUID tenantB;
    private UUID supplierOfB;

    @BeforeEach
    void setUp() {
        transactions = new TransactionTemplate(transactionManager);
        tenantA = tenants.save(new Tenant("A GmbH " + UUID.randomUUID())).getId();
        tenantB = tenants.save(new Tenant("B GmbH " + UUID.randomUUID())).getId();
        inTenant(tenantA, () -> insertSupplier(tenantA, "Supplier of A"));
        supplierOfB = inTenant(tenantB, () -> insertSupplier(tenantB, "Supplier of B"));
        inTenant(tenantB, () -> insertIban(supplierOfB));
    }

    @Test
    void seesOnlyRowsOfItsOwnTenant() {
        List<Object> names = inTenant(tenantA, () -> entityManager
                .createNativeQuery("SELECT name FROM supplier")
                .getResultList());

        assertThat(names).containsExactly("Supplier of A");
    }

    @Test
    void seesNothingWithoutATenant() {
        Number count = transactions.execute(status -> (Number) entityManager
                .createNativeQuery("SELECT count(*) FROM supplier")
                .getSingleResult());

        assertThat(count.longValue()).isZero();
    }

    @Test
    void cannotInsertRowsForAnotherTenant() {
        assertThatThrownBy(() -> inTenant(tenantA, () -> insertSupplier(tenantB, "Planted by A")))
                .hasStackTraceContaining("row-level security");
    }

    @Test
    void cannotChangeRowsOfAnotherTenant() {
        Integer updated = inTenant(tenantA, () -> entityManager
                .createNativeQuery("UPDATE supplier SET status = 'BLOCKED' WHERE id = :id")
                .setParameter("id", supplierOfB)
                .executeUpdate());

        assertThat(updated).isZero();
    }

    @Test
    void childRowsAreVisibleOnlyWithTheirParent() {
        String countIbans = "SELECT count(*) FROM supplier_iban WHERE supplier_id = :id";

        Number seenByA = inTenant(tenantA, () -> (Number) entityManager
                .createNativeQuery(countIbans).setParameter("id", supplierOfB).getSingleResult());
        Number seenByB = inTenant(tenantB, () -> (Number) entityManager
                .createNativeQuery(countIbans).setParameter("id", supplierOfB).getSingleResult());

        assertThat(seenByA.longValue()).isZero();
        assertThat(seenByB.longValue()).isEqualTo(1);
    }

    private <T> T inTenant(UUID tenantId, Supplier<T> work) {
        return TenantContext.callAs(tenantId, () -> transactions.execute(status -> work.get()));
    }

    private UUID insertSupplier(UUID tenantId, String name) {
        UUID id = UUID.randomUUID();
        entityManager.createNativeQuery("""
                        INSERT INTO supplier (id, tenant_id, name, status, created_by, created_at, version)
                        VALUES (:id, :tenantId, :name, 'ACTIVE', 'test', now(), 0)
                        """)
                .setParameter("id", id)
                .setParameter("tenantId", tenantId)
                .setParameter("name", name)
                .executeUpdate();
        return id;
    }

    private UUID insertIban(UUID supplierId) {
        UUID id = UUID.randomUUID();
        entityManager.createNativeQuery("""
                        INSERT INTO supplier_iban (id, supplier_id, iban, status, added_by, added_at)
                        VALUES (:id, :supplierId, 'DE89370400440532013000', 'PENDING', 'test', now())
                        """)
                .setParameter("id", id)
                .setParameter("supplierId", supplierId)
                .executeUpdate();
        return id;
    }
}