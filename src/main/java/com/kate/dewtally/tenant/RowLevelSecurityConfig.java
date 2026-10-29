package com.kate.dewtally.tenant;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.UUID;

/**
 * Connects Hibernate to Row-Level Security: every session asks TenantContext for the tenant,
 * and its connections carry that tenant to PostgreSQL.
 */
@Configuration(proxyBeanMethods = false)
class RowLevelSecurityConfig {

    // Hibernate needs some tenant value; this one means "no tenant": the database shows nothing.
    static final String NO_TENANT = "none";

    @Bean
    HibernatePropertiesCustomizer rowLevelSecurity(DataSource dataSource) {
        return properties -> {
            properties.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, new TenantConnections(dataSource));
            properties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, new CurrentTenant());
        };
    }

    static final class CurrentTenant implements CurrentTenantIdentifierResolver<String> {

        @Override
        public String resolveCurrentTenantIdentifier() {
            return TenantContext.current().map(UUID::toString).orElse(NO_TENANT);
        }

        @Override
        public boolean validateExistingCurrentSessions() {
            return false;
        }
    }
}