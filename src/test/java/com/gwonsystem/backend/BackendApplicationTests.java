package com.gwonsystem.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.junit.jupiter.api.AfterAll;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import java.util.Map;

@SpringBootTest
@ActiveProfiles("test-isolated")
class BackendApplicationTests {

    private static EmbeddedPostgres postgres;
    @DynamicPropertySource static void isolatedDatabase(DynamicPropertyRegistry registry) throws Exception {
        postgres=EmbeddedPostgres.builder().setPort(0).start();
        var ds=postgres.getPostgresDatabase();
        String url;
        try(var connection=ds.getConnection()) {
            url=connection.getMetaData().getURL();
            if(!url.matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/postgres.*")) throw new IllegalStateException("Test database must be a disposable localhost cluster");
        }
        // Fixture creation is confined to this NEW temporary cluster, never Supabase.
        var factory=new LocalContainerEntityManagerFactoryBean();factory.setDataSource(ds);
        factory.setPackagesToScan("com.gwonsystem.backend.entity");factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","create","hibernate.show_sql","false",
            "hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
        factory.afterPropertiesSet();factory.destroy();
        registry.add("spring.datasource.url",()->url);
        registry.add("spring.datasource.username",()->"postgres");
        registry.add("spring.datasource.password",()->"");
    }
    @AfterAll static void closeDatabase() throws Exception { if(postgres!=null)postgres.close(); }

    @Test
    void contextLoads() {
    }

}
