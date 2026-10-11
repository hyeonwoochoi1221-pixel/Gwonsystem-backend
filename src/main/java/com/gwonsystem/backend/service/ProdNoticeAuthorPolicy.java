package com.gwonsystem.backend.service;

import com.gwonsystem.backend.dto.NoticeCreateRequestDto;
import com.gwonsystem.backend.entity.Role;
import com.gwonsystem.backend.entity.Notice;
import com.gwonsystem.backend.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
@Profile("!dev")
@RequiredArgsConstructor
public class ProdNoticeAuthorPolicy implements NoticeAuthorPolicy, InitializingBean {
    private final MemberRepository members;
    private final JdbcTemplate jdbc;

    @Override public void afterPropertiesSet() {
        // Catalog-only SELECT. No schema creation/repair; absence blocks non-development startup.
        Boolean ready=jdbc.queryForObject("""
            SELECT EXISTS (
              SELECT 1 FROM pg_catalog.pg_attribute a
              JOIN pg_catalog.pg_constraint c ON c.conrelid=a.attrelid AND c.conkey=ARRAY[a.attnum]
              JOIN pg_catalog.pg_attribute m ON m.attrelid=c.confrelid AND c.confkey=ARRAY[m.attnum]
              WHERE a.attrelid='public.notices'::regclass AND a.attname='author_member_id'
                AND NOT a.attisdropped AND a.attnotnull AND a.atttypid='pg_catalog.int8'::regtype
                AND c.contype='f' AND c.confrelid='public.members'::regclass
                AND m.attname='id' AND NOT m.attisdropped AND m.atttypid='pg_catalog.int8'::regtype
                AND c.confdeltype IN ('a','r') AND c.confupdtype IN ('a','r')
                AND c.convalidated AND NOT c.condeferrable
                AND EXISTS (SELECT 1 FROM pg_catalog.pg_attribute legacy
                  WHERE legacy.attrelid='public.notices'::regclass AND legacy.attname='author_id'
                    AND NOT legacy.attisdropped AND NOT legacy.attnotnull AND legacy.atttypid='pg_catalog.int8'::regtype)
            )
            """, Boolean.class);
        if (!Boolean.TRUE.equals(ready))
            throw new IllegalStateException("Production notice author schema is not ready; approved migration required");
    }

    @Override public Author resolve(NoticeCreateRequestDto request, Authentication authentication) {
        if (authentication==null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken || authentication.getName().isBlank())
            throw new AuthenticationCredentialsNotFoundException("Login required");
        var member=members.findByUsername(authentication.getName())
            .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("Login member no longer exists"));
        if (member.getRole()!=Role.ROLE_REGULAR && member.getRole()!=Role.ROLE_SUPERVISOR)
            throw new AccessDeniedException("Current member cannot create notices");
        if (member.getId()==null) throw new AuthenticationCredentialsNotFoundException("Persisted member required");
        // Neither authorId nor a client-supplied author_member_id decides the author.
        return new Author(null, member.getId());
    }

    @Override public Long create(Notice notice, Author author) {
        if (author.memberId()==null) throw new IllegalStateException("Production author is required");
        // Preserve entity defaults; one INSERT also supplies the author to INSERT triggers/RLS.
        // JpaTransactionManager shares the configured DataSource with JdbcTemplate.
        notice.prePersist();
        Long id=jdbc.queryForObject("""
            INSERT INTO public.notices
              (author_id,author_member_id,category,title,content,view_count,is_pinned,created_at,updated_at)
            VALUES (NULL,?,?,?,?,?,?,?,?) RETURNING id
            """,Long.class,author.memberId(),notice.getCategory(),notice.getTitle(),notice.getContent(),
            notice.getViewCount(),notice.getIsPinned(),notice.getCreatedAt(),notice.getUpdatedAt());
        if (id==null) throw new IllegalStateException("Production notice creation failed");
        return id;
    }
}
