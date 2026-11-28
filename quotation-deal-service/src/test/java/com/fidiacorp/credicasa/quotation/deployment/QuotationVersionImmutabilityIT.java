package com.fidiacorp.credicasa.quotation.deployment;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** US21 en PostgreSQL 16 real: el trigger rechaza UPDATE/DELETE de versiones y el CHECK exige un SHA-256 válido. */
class QuotationVersionImmutabilityIT {
    @Test
    void versionsCannotBeUpdatedOrDeletedOnceIssued() throws Exception {
        try (EmbeddedPostgres pg = EmbeddedPostgres.builder().setPort(0).setServerConfig("listen_addresses", "127.0.0.1").start()) {
            Flyway.configure().dataSource(pg.getPostgresDatabase()).locations("classpath:db/migration").load().migrate();
            UUID offer = UUID.randomUUID();
            UUID version = UUID.randomUUID();
            try (Connection c = pg.getPostgresDatabase().getConnection(); var s = c.createStatement()) {
                s.execute("insert into credit_offers(id,recipient_id,issuer_id,bank_code,origin,property_id,property_title,property_address,"
                        + "property_price,property_currency,loan_amount,currency,annual_interest_rate,term_months,start_date,created_at) values ('"
                        + offer + "','" + UUID.randomUUID() + "','" + UUID.randomUUID() + "','BCP','DEMO','" + UUID.randomUUID()
                        + "','Casa','Lima',250000,'PEN',200000,'PEN',0.08,12,'2026-01-15',now())");
                s.execute("insert into quotation_versions(id,offer_id,version_number,loan_amount,annual_interest_rate,term_months,snapshot_json,"
                        + "snapshot_hash,issued_by,issued_at,expires_at) values ('" + version + "','" + offer + "',1,200000,0.08,12,'{}','"
                        + "a".repeat(64) + "','" + UUID.randomUUID() + "',now(),now() + interval '15 days')");
                assertThatThrownBy(() -> s.execute("update quotation_versions set loan_amount = 1 where id = '" + version + "'"))
                        .isInstanceOf(SQLException.class).hasMessageContaining("inmutable");
                assertThatThrownBy(() -> s.execute("delete from quotation_versions where id = '" + version + "'"))
                        .isInstanceOf(SQLException.class).hasMessageContaining("inmutable");
                assertThatThrownBy(() -> s.execute("insert into quotation_versions(id,offer_id,version_number,loan_amount,annual_interest_rate,"
                        + "term_months,snapshot_json,snapshot_hash,issued_by,issued_at,expires_at) values ('" + UUID.randomUUID() + "','" + offer
                        + "',2,1,0.08,12,'{}','not-a-hash','" + UUID.randomUUID() + "',now(),now() + interval '1 day')"))
                        .isInstanceOf(SQLException.class);
                try (var r = s.executeQuery("select count(*) from quotation_versions")) {
                    r.next();
                    assertThat(r.getInt(1)).isEqualTo(1);
                }
            }
        }
    }
}
