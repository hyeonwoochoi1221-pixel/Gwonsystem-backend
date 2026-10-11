package com.gwonsystem.backend;

import com.gwonsystem.backend.dto.NoticeCreateRequestDto;
import com.gwonsystem.backend.entity.*;
import com.gwonsystem.backend.entity.Role;
import com.gwonsystem.backend.repository.*;
import com.gwonsystem.backend.service.*;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import javax.sql.DataSource;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.dao.DataIntegrityViolationException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Fresh local cluster only: never a configured application/Supabase DataSource. */
class NoticeMemberAuthorIntegrationTest {
    @Configuration @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses=NoticeRepository.class)
    static class TestConfiguration {
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) { return factory(ds,"validate"); }
        @Bean JpaTransactionManager transactionManager(jakarta.persistence.EntityManagerFactory emf) { return new JpaTransactionManager(emf); }
        @Bean JdbcTemplate jdbcTemplate(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean DevNoticeAuthorPolicy authorPolicy(MemberRepository members,JdbcTemplate jdbc) { return spy(new DevNoticeAuthorPolicy(members,jdbc)); }
        @Bean NoticeService noticeService(NoticeRepository notices,DevNoticeAuthorPolicy authors) { return new NoticeService(notices,authors); }
    }
    static LocalContainerEntityManagerFactoryBean factory(DataSource ds,String action) {
        var f=new LocalContainerEntityManagerFactoryBean();f.setDataSource(ds);f.setPackagesToScan("com.gwonsystem.backend.entity");
        f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto",action,
            "hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));return f;
    }
    NoticeCreateRequestDto request() {
        var request=new NoticeCreateRequestDto();request.setAuthorId(999L);request.setTitle("Test notice");
        request.setCategory("Test");request.setContent("Isolated fixture");request.setIsPinned(false);return request;
    }
    @Test void realForeignKeysTransactionsAndLegacyReadsRemainIntact() throws Exception {
        try(var pg=EmbeddedPostgres.builder().setPort(0).start()) {
            var ds=pg.getPostgresDatabase();
            try(var c=ds.getConnection()) { assertTrue(c.getMetaData().getURL().matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/postgres.*")); }
            var fixture=factory(ds,"create");fixture.afterPropertiesSet();fixture.destroy();
            var jdbc=new JdbcTemplate(ds);
            jdbc.execute("CREATE TABLE public.users(id integer PRIMARY KEY)");
            jdbc.execute("ALTER TABLE public.notices ADD CONSTRAINT notices_author_id_fkey FOREIGN KEY(author_id) REFERENCES public.users(id) ON DELETE SET NULL");
            // Execute the EXACT migration DDL body in this isolated PG14 cluster.
            // Human confirmation/PG17 guard is intentionally not replaced or executed.
            String migration=Files.readString(Path.of("docs/sql/03-notice-member-author.dev.sql"));
            // The unedited full script must fail closed without creating the column.
            try(var connection=ds.getConnection();var statement=connection.createStatement()) {
                connection.setAutoCommit(false);
                assertThrows(java.sql.SQLException.class,()->statement.execute(migration));connection.rollback();
            }
            assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='notices' AND column_name='author_member_id'",Integer.class));
            String ddl=migration.substring(migration.indexOf("ALTER TABLE public.notices ADD COLUMN"),migration.indexOf("COMMIT;"));
            jdbc.execute(ddl);
            try(var context=new AnnotationConfigApplicationContext()) {
                context.registerBean(DataSource.class,()->ds);context.register(TestConfiguration.class);context.refresh();
                var members=context.getBean(MemberRepository.class);var notices=context.getBean(NoticeRepository.class);
                var service=context.getBean(NoticeService.class);var policy=context.getBean(DevNoticeAuthorPolicy.class);
                var member=new Member();member.setUsername("dev_admin_test");member.setEmail("dev@example.test");
                member.setLastName("Test");member.setFirstName("Admin");member.setRole(Role.ROLE_SUPERVISOR);
                member=members.saveAndFlush(member);
                var auth=new UsernamePasswordAuthenticationToken("dev_admin_test",null,List.of(new SimpleGrantedAuthority("ROLE_SUPERVISOR")));
                assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM public.users",Integer.class));
                Long id=service.createNotice(request(),auth);
                assertEquals(member.getId(),jdbc.queryForObject("SELECT author_member_id FROM public.notices WHERE id=?",Long.class,id));
                assertNull(jdbc.queryForObject("SELECT author_id FROM public.notices WHERE id=?",Long.class,id));
                assertThrows(DataIntegrityViolationException.class,()->members.deleteById(members.findByUsername("dev_admin_test").orElseThrow().getId()));
                // A real FK failure must roll back the entire notice transaction.
                long before=notices.count();
                doAnswer(call -> { Long created=(Long)call.callRealMethod();
                    jdbc.update("UPDATE public.notices SET author_member_id=-1 WHERE id=?",created);return null; })
                    .when(policy).create(any(Notice.class),any());
                assertThrows(DataIntegrityViolationException.class,()->service.createNotice(request(),auth));
                assertEquals(before,notices.count());doCallRealMethod().when(policy).create(any(Notice.class),any());
                jdbc.update("INSERT INTO public.users(id) VALUES (42)");
                var legacy=notices.saveAndFlush(Notice.builder().authorId(42L).title("Legacy").content("Legacy text").category("Test").build());
                jdbc.update("DELETE FROM public.users WHERE id=42");
                assertNull(jdbc.queryForObject("SELECT author_id FROM public.notices WHERE id=?",Long.class,legacy.getId()));
                assertEquals(2,service.getAllNotices().size());
                var detail=service.getNoticeById(legacy.getId());assertEquals(1,detail.getViewCount());
                var response=new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(detail);
                var keys=new HashSet<String>();response.fieldNames().forEachRemaining(keys::add);
                assertEquals(Set.of("id","category","title","content","viewCount","isPinned","date"),keys);
                service.deleteNotice(legacy.getId());service.deleteNotice(id);
                assertEquals(0,notices.count());assertEquals(1,members.count());
            }
            // Missing/wrong schema is never repaired by startup check.
            jdbc.execute("ALTER TABLE public.notices DROP CONSTRAINT notices_author_member_id_fkey");
            assertThrows(IllegalStateException.class,()->new DevNoticeAuthorPolicy(mock(MemberRepository.class),jdbc).afterPropertiesSet());
        }
    }
}
