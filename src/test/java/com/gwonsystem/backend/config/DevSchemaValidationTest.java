package com.gwonsystem.backend.config;

import java.util.*;
import javax.sql.DataSource;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.Test;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import static org.junit.jupiter.api.Assertions.*;

/** Hibernate's actual validate action against a disposable localhost database, never Supabase. */
class DevSchemaValidationTest {
    LocalContainerEntityManagerFactoryBean factory(DataSource ds,String action) {
        var f=new LocalContainerEntityManagerFactoryBean();f.setDataSource(ds);
        f.setPackagesToScan("com.gwonsystem.backend.entity");f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto",action,"hibernate.show_sql","false",
            "hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));return f;
    }
    List<String> shape(DataSource ds) throws Exception {
        var rows=new ArrayList<String>();
        try(var c=ds.getConnection();var s=c.createStatement();var r=s.executeQuery(
            "SELECT table_name,column_name,data_type,is_nullable,COALESCE(column_default,'') FROM information_schema.columns "
            + "WHERE table_schema='public' ORDER BY table_name,ordinal_position")) {
            while(r.next())rows.add(r.getString(1)+"/"+r.getString(2)+"/"+r.getString(3)+"/"+r.getString(4)+"/"+r.getString(5));
        }return rows;
    }
    @Test void validateDoesNotChangeExistingSchemaAndMissingSchemaFailsWithoutCreatingTables() throws Exception {
        try(var pg=EmbeddedPostgres.builder().setPort(0).start()) {
            var ds=pg.getPostgresDatabase();
            try(var c=ds.getConnection()) {
                assertTrue(c.getMetaData().getURL().matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/postgres.*"));
            }
            // Test fixture only: create tables in a NEW temporary local cluster.
            var fixture=factory(ds,"create");fixture.afterPropertiesSet();fixture.destroy();
            var before=shape(ds);
            var env=DevEnvironmentGuardTest.environment(DevEnvironmentGuardTest.settings());DevEnvironmentGuardTest.apply(env);
            var validate=factory(ds,env.getProperty("spring.jpa.hibernate.ddl-auto"));
            validate.afterPropertiesSet();validate.destroy();assertEquals(before,shape(ds));
            try(var c=ds.getConnection();var s=c.createStatement()) { s.execute("ALTER TABLE public.members RENAME COLUMN username TO missing_username"); }
            var incompatible=shape(ds);var failed=factory(ds,env.getProperty("spring.jpa.hibernate.ddl-auto"));
            assertThrows(Exception.class,failed::afterPropertiesSet);assertEquals(incompatible,shape(ds));
        }
    }
}
