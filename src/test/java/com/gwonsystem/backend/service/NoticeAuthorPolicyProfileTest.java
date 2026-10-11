package com.gwonsystem.backend.service;

import com.gwonsystem.backend.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NoticeAuthorPolicyProfileTest {
    void check(Class<?> expected,String... profiles) {
        try(var context=new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles(profiles);
            var jdbc=mock(JdbcTemplate.class);
            when(jdbc.queryForObject(anyString(),eq(Boolean.class))).thenReturn(true);
            context.registerBean(MemberRepository.class,()->mock(MemberRepository.class));
            context.registerBean(JdbcTemplate.class,()->jdbc);
            context.register(DevNoticeAuthorPolicy.class,ProdNoticeAuthorPolicy.class);context.refresh();
            assertEquals(1,context.getBeansOfType(NoticeAuthorPolicy.class).size());
            assertInstanceOf(expected,context.getBean(NoticeAuthorPolicy.class));
        }
    }
    @Test void devChoosesDevOnly(){check(DevNoticeAuthorPolicy.class,"dev");}
    @Test void prodChoosesProdOnly(){check(ProdNoticeAuthorPolicy.class,"prod");}
    @Test void defaultChoosesProdOnly(){check(ProdNoticeAuthorPolicy.class);}
    @Test void isolatedTestChoosesNonDevPolicy(){check(ProdNoticeAuthorPolicy.class,"test-isolated");}
}
