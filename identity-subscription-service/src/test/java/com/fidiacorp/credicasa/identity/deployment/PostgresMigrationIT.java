package com.fidiacorp.credicasa.identity.deployment;

import com.fidiacorp.credicasa.identity.IdentitySubscriptionApplication;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.sql.DriverManager;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * identity_db sobre PostgreSQL 16 real (embebido, loopback): Flyway propio + Hibernate validate, reinicio idempotente,
 * rechazo de esquemas desconocidos y 0 claves foráneas hacia tablas de otros bounded contexts (ADR-001 / PA-05).
 */
class PostgresMigrationIT {
    static final int MIGRATIONS = 2;
    static final String OWN_TABLES = "'accounts','account_two_factor','revoked_tokens','subscriptions','payment_records'";
    static EmbeddedPostgres postgres;

    @BeforeAll
    static void start() throws Exception {
        postgres = EmbeddedPostgres.builder().setPort(0).setServerConfig("listen_addresses", "127.0.0.1").start();
        try (var c = postgres.getPostgresDatabase().getConnection(); var s = c.createStatement(); var r = s.executeQuery("show server_version")) {
            assertTrue(r.next());
            assertTrue(r.getString(1).startsWith("16."));
        }
    }

    @AfterAll
    static void stop() throws Exception {
        if (postgres != null) postgres.close();
    }

    String database() throws Exception {
        String name = "identity_db_" + UUID.randomUUID().toString().replace("-", "");
        try (var c = postgres.getPostgresDatabase().getConnection(); var s = c.createStatement()) {
            s.execute("create database " + name);
        }
        return "jdbc:postgresql://127.0.0.1:" + postgres.getPort() + "/" + name;
    }

    ConfigurableApplicationContext app(String url) {
        return new SpringApplication(IdentitySubscriptionApplication.class).run(
                "--server.address=127.0.0.1", "--server.port=0",
                "--spring.datasource.url=" + url, "--spring.datasource.username=postgres", "--spring.datasource.password=",
                "--spring.datasource.driver-class-name=org.postgresql.Driver",
                "--spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
                "--spring.jpa.hibernate.ddl-auto=validate", "--spring.flyway.enabled=true",
                "--spring.flyway.baseline-on-migrate=false", "--spring.flyway.clean-disabled=true",
                "--app.security.jwt.secret=IsolatedPostgresTestSigningKeyNotForProduction123456789",
                "--logging.level.root=ERROR",
                "--app.security.two-factor.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
    }

    long scalar(String url, String sql) throws Exception {
        try (var c = DriverManager.getConnection(url, "postgres", ""); var s = c.createStatement(); var r = s.executeQuery(sql)) {
            assertTrue(r.next());
            return r.getLong(1);
        }
    }

    @Test
    void freshDatabaseMigratesValidatesAndRestarts() throws Exception {
        String url = database();
        try (var context = app(url)) {
            assertTrue(context.isActive());
        }
        assertEquals(MIGRATIONS, scalar(url, "select count(*) from flyway_schema_history where success"));
        assertEquals(MIGRATIONS, scalar(url, "select max(cast(version as integer)) from flyway_schema_history where success"));
        long own = OWN_TABLES.split(",").length;
        assertEquals(own, scalar(url, "select count(*) from information_schema.tables where table_schema='public' and table_name in (" + OWN_TABLES + ")"));
        assertEquals(0, scalar(url, "select count(*) from information_schema.tables where table_schema='public' and table_type='BASE TABLE' "
                + "and table_name not in (" + OWN_TABLES + ",'flyway_schema_history')"));
        try (var context = app(url)) {
            assertTrue(context.isActive());
        }
        assertEquals(MIGRATIONS, scalar(url, "select count(*) from flyway_schema_history where success"));
    }

    @Test
    void foreignKeysNeverCrossTheBoundedContext() throws Exception {
        String url = database();
        try (var context = app(url)) {
            assertTrue(context.isActive());
        }
        assertEquals(0, scalar(url, "select count(*) from information_schema.referential_constraints rc "
                + "join information_schema.constraint_column_usage u on u.constraint_name = rc.unique_constraint_name "
                + "where u.table_name not in (" + OWN_TABLES + ")"));
    }

    @Test
    void unknownNonEmptySchemaIsRejected() throws Exception {
        String url = database();
        try (var c = DriverManager.getConnection(url, "postgres", ""); var s = c.createStatement()) {
            s.execute("create table unknown_business_data(id int)");
        }
        assertThrows(Exception.class, () -> {
            try (var ignored = app(url)) {
                fail("Unknown schema accepted");
            }
        });
        assertEquals(0, scalar(url, "select count(*) from information_schema.tables where table_name='flyway_schema_history'"));
    }
}
