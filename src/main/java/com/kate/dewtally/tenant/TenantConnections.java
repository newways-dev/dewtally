package com.kate.dewtally.tenant;

import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.hibernate.service.UnknownUnwrapTypeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Hands out database connections for Hibernate. Before a connection is used, it switches to the
 * application role (which cannot bypass Row-Level Security) and sets the tenant. Without a tenant,
 * the setting stays empty and the database shows no tenant data at all.
 * Liquibase and Hibernate's schema check use "any connection" and keep the owner's rights.
 */
class TenantConnections implements MultiTenantConnectionProvider<String> {

    static final String APP_ROLE = "dewtally_app";
    private static final Logger log = LoggerFactory.getLogger(TenantConnections.class);

    private final DataSource dataSource;

    TenantConnections(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Connection getAnyConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public Connection getConnection(String tenant) throws SQLException {
        Connection connection = dataSource.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT set_config('app.tenant_id', ?, false), set_config('role', ?, false)")) {
            statement.setString(1, RowLevelSecurityConfig.NO_TENANT.equals(tenant) ? "" : tenant);
            statement.setString(2, APP_ROLE);
            statement.execute();
            return connection;
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
    }

    @Override
    public void releaseConnection(String tenant, Connection connection) throws SQLException {
        // Back to the owner before the connection returns to the pool. If this fails, nothing leaks:
        // the next getConnection sets role and tenant again.
        try (Statement statement = connection.createStatement()) {
            statement.execute("RESET ROLE; RESET app.tenant_id");
        } catch (SQLException e) {
            log.debug("Could not reset role and tenant of a connection: {}", e.getMessage());
        } finally {
            connection.close();
        }
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> type) {
        return type.isInstance(this) || type.isInstance(dataSource);
    }

    @Override
    public <T> T unwrap(Class<T> type) {
        if (type.isInstance(this)) {
            return type.cast(this);
        }
        if (type.isInstance(dataSource)) {
            return type.cast(dataSource);
        }
        throw new UnknownUnwrapTypeException(type);
    }
}