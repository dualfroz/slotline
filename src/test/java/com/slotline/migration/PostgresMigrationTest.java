package com.slotline.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class PostgresMigrationTest {

    @Test
    void migrationsApplyAndPartialUniqueIndexKeepsOneActiveSlot() throws Exception {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker is not available; skipping the PostgreSQL migration test");

        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();

            Flyway flyway = Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration/postgresql")
                    .load();
            flyway.migrate();

            try (Connection connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {

                long organizationId = single(connection, "SELECT id FROM organization WHERE slug = 'default'");
                long userId = returningId(connection, "INSERT INTO app_user "
                        + "(organization_id, username, password_hash, role, created_at) "
                        + "VALUES (" + organizationId + ", 'pg-user', 'x', 'USER', now()) RETURNING id");
                long resourceId = returningId(connection, "INSERT INTO resource "
                        + "(organization_id, name, description, zone_id, buffer_minutes, created_at, version) "
                        + "VALUES (" + organizationId + ", 'PG court', 'outdoor', 'UTC', 0, now(), 0) RETURNING id");

                long firstReservationId = returningId(connection, activeReservationInsert(resourceId, organizationId,
                        userId));

                boolean duplicateRejected = false;
                try (Statement statement = connection.createStatement()) {
                    statement.execute(activeReservationInsert(resourceId, organizationId, userId));
                } catch (SQLException uniqueViolation) {
                    duplicateRejected = true;
                }
                assertThat(duplicateRejected)
                        .as("partial unique index must reject a second ACTIVE reservation on the same slot")
                        .isTrue();

                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate(
                            "UPDATE reservation SET status = 'CANCELLED' WHERE id = " + firstReservationId);
                }

                boolean rebookAllowed = true;
                try (Statement statement = connection.createStatement()) {
                    statement.execute(activeReservationInsert(resourceId, organizationId, userId));
                } catch (SQLException stillBlocked) {
                    rebookAllowed = false;
                }
                assertThat(rebookAllowed)
                        .as("once the slot is cancelled the same slot may be booked again")
                        .isTrue();
            }
        }
    }

    private String activeReservationInsert(long resourceId, long organizationId, long userId) {
        return "INSERT INTO reservation "
                + "(resource_id, organization_id, user_id, start_time, end_time, status, created_at, version) "
                + "VALUES (" + resourceId + ", " + organizationId + ", " + userId + ", "
                + "TIMESTAMPTZ '2026-09-01 10:00:00+00', TIMESTAMPTZ '2026-09-01 11:00:00+00', "
                + "'ACTIVE', now(), 0)";
    }

    private long single(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private long returningId(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
    }
}
