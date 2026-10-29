package com.fidiacorp.credicasa.deployment;

import com.fidiacorp.credicasa.CrediCasaApplication;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Real PostgreSQL 16, isolated on loopback; opt-in Maven profile only. */
class PostgresMigrationIT {
    static EmbeddedPostgres postgres;
    @BeforeAll static void start() throws Exception {
        postgres=EmbeddedPostgres.builder().setPort(0)
                .setServerConfig("listen_addresses","127.0.0.1").start();
        try(var c=postgres.getPostgresDatabase().getConnection();var s=c.createStatement();var r=s.executeQuery("show server_version")) {
            assertTrue(r.next());assertTrue(r.getString(1).startsWith("16."));
        }
    }
    @AfterAll static void stop() throws Exception {if(postgres!=null)postgres.close();}
    String database() throws Exception {
        String name="test_"+UUID.randomUUID().toString().replace("-","");
        try(var c=postgres.getPostgresDatabase().getConnection();var s=c.createStatement()){s.execute("create database "+name);}
        return "jdbc:postgresql://127.0.0.1:"+postgres.getPort()+"/"+name;
    }
    ConfigurableApplicationContext app(String url,boolean migrate,String ddl) {
        return new SpringApplication(CrediCasaApplication.class).run(
            "--server.address=127.0.0.1","--server.port=0",
            "--spring.datasource.url="+url,"--spring.datasource.username=postgres","--spring.datasource.password=",
            "--spring.datasource.driver-class-name=org.postgresql.Driver",
            "--spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
            "--spring.jpa.hibernate.ddl-auto="+ddl,"--spring.flyway.enabled="+migrate,
            "--spring.flyway.baseline-on-migrate=false","--spring.flyway.clean-disabled=true",
            "--app.security.jwt.secret=IsolatedPostgresTestSigningKeyNotForProduction123456789",
            "--app.security.two-factor.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "--logging.level.root=ERROR");
    }
    long scalar(String url,String sql)throws Exception {
        try(var c=DriverManager.getConnection(url,"postgres","");var s=c.createStatement();var r=s.executeQuery(sql)){assertTrue(r.next());return r.getLong(1);}
    }
    @Test void freshDatabaseAndRestart()throws Exception {
        String url=database();
        try(var context=app(url,true,"validate")){assertTrue(context.isActive());}
        try(var c=DriverManager.getConnection(url,"postgres","");var statement=c.createStatement()) {
            statement.execute("insert into accounts(id,first_name,last_name,email,password_hash,roles) values ('"+UUID.randomUUID()+"','Fresh','Restart','restart@example.test','test-only-hash','ROLE_CLIENT')");
        }
        assertEquals(7,scalar(url,"select count(*) from flyway_schema_history where success"));
        try(var context=app(url,true,"validate")){assertTrue(context.isActive());}
        assertEquals(1,scalar(url,"select count(*) from accounts where email='restart@example.test'"));
        assertEquals(7,scalar(url,"select count(*) from flyway_schema_history where success"));
    }
    @Test void hibernateLegacyRequiresExplicitBaselineAndPreservesUnknownOwnership()throws Exception {
        String url=database();
        try(var context=app(url,false,"create")){assertTrue(context.isActive());}
        UUID id=UUID.randomUUID();
        try(var c=DriverManager.getConnection(url,"postgres","");var s=c.createStatement()){
            s.execute("insert into quotations(id,loan_amount,currency,term_months,annual_interest_rate,grace_period_type,grace_period_months,start_date,tea,tem,monthly_tir,tcea,van,total_interest,total_cost,status,created_at) values ('"+id+"',1000,'PEN',12,.08,'NONE',0,'2020-01-01',.08,.006,.006,.08,-20,20,1020,'DRAFT',CURRENT_TIMESTAMP)");
        }
        assertThrows(Exception.class,()->{try(var ignored=app(url,true,"validate")) {fail("Automatic adoption must fail");}});
        // Operator-only action after schema inventory/backup; never enabled in application configuration.
        Flyway.configure().dataSource(url,"postgres","").baselineVersion("0").baselineOnMigrate(false).load().baseline();
        try(var context=app(url,true,"validate")){assertTrue(context.isActive());}
        assertEquals(1,scalar(url,"select count(*) from quotations where id='"+id+"' and owner_id is null and client_id is null and property_id is null and discount_rate_used is null and loan_amount=1000"));
    }
    @Test void unknownNonemptySchemaIsRejected()throws Exception {
        String url=database();
        try(var c=DriverManager.getConnection(url,"postgres","");var s=c.createStatement()){s.execute("create table unknown_business_data(id int)");}
        assertThrows(Exception.class,()->{try(var ignored=app(url,true,"validate")){fail("Unknown schema accepted");}});
        assertEquals(0,scalar(url,"select count(*) from information_schema.tables where table_name='flyway_schema_history'"));
    }
}
