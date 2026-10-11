package com.gwonsystem.backend.service;

import com.gwonsystem.backend.dto.NoticeCreateRequestDto;
import com.gwonsystem.backend.entity.*;
import com.gwonsystem.backend.repository.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.access.AccessDeniedException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProdNoticeAuthorPolicyTest {
    final MemberRepository members=mock(MemberRepository.class);
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final ProdNoticeAuthorPolicy policy=new ProdNoticeAuthorPolicy(members,jdbc);
    final NoticeRepository notices=mock(NoticeRepository.class);
    final NoticeService service=new NoticeService(notices,policy);
    final org.springframework.security.core.Authentication auth=new UsernamePasswordAuthenticationToken(
        "writer",null,List.of(new SimpleGrantedAuthority("ROLE_SUPERVISOR")));

    Member member(Role role) {
        var member=new Member();member.setId(17L);member.setRole(role);
        when(members.findByUsername("writer")).thenReturn(Optional.of(member));return member;
    }
    NoticeCreateRequestDto request(Long suppliedId) {
        var request=new NoticeCreateRequestDto();request.setAuthorId(suppliedId);
        request.setTitle("Notice");request.setContent("Content");return request;
    }
    @Test void currentMemberDeterminesAuthorRegardlessOfClientIdOrStaleSessionRole() {
        for(Role role:List.of(Role.ROLE_REGULAR,Role.ROLE_SUPERVISOR)) {
            member(role);
            for(Long forged:Arrays.asList(null,1L,999L))
                assertEquals(new NoticeAuthorPolicy.Author(null,17L),policy.resolve(request(forged),auth));
        }
        member(Role.ROLE_ASSOCIATE);
        assertThrows(AccessDeniedException.class,()->policy.resolve(request(999L),auth));
        var member=member(Role.ROLE_SUPERVISOR);member.setId(null);
        assertThrows(AuthenticationCredentialsNotFoundException.class,()->policy.resolve(request(999L),auth));
        when(members.findByUsername("writer")).thenReturn(Optional.empty());
        assertThrows(AuthenticationCredentialsNotFoundException.class,()->policy.resolve(request(999L),auth));
    }
    @Test void unauthenticatedAnonymousAndBlankNameFailBeforeDatabaseLookup() {
        var request=request(999L);
        assertThrows(AuthenticationCredentialsNotFoundException.class,()->policy.resolve(request,null));
        assertThrows(AuthenticationCredentialsNotFoundException.class,()->policy.resolve(request,new UsernamePasswordAuthenticationToken("writer",null)));
        assertThrows(AuthenticationCredentialsNotFoundException.class,()->policy.resolve(request,new AnonymousAuthenticationToken("key","anonymous",List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")))));
        assertThrows(AuthenticationCredentialsNotFoundException.class,()->policy.resolve(request,new UsernamePasswordAuthenticationToken(" ",null,List.of())));
        verifyNoInteractions(members,jdbc);
    }
    @Test void createWritesOnlyServerMemberAndNullLegacyAuthorWithoutJpaFallback() {
        member(Role.ROLE_SUPERVISOR);
        when(jdbc.queryForObject(contains("INSERT INTO public.notices"),eq(Long.class),any(Object[].class))).thenReturn(77L);
        assertEquals(77L,service.createNotice(request(999L),auth));
        verify(jdbc).queryForObject(contains("VALUES (NULL,"),eq(Long.class),eq(17L),isNull(),eq("Notice"),eq("Content"),eq(0),eq(false),any(),any());
        verify(notices,never()).save(any());
    }
    @Test void deleteRechecksDatabaseMembershipAndRoleBeforeRepositoryAccess() {
        assertThrows(AuthenticationCredentialsNotFoundException.class,()->service.deleteNotice(1L,null));
        member(Role.ROLE_ASSOCIATE);assertThrows(AccessDeniedException.class,()->service.deleteNotice(1L,auth));
        verifyNoInteractions(notices);
        for(Role role:List.of(Role.ROLE_REGULAR,Role.ROLE_SUPERVISOR)) {
            member(role);var notice=Notice.builder().title("Notice").content("Content").build();
            when(notices.findById(1L)).thenReturn(Optional.of(notice));service.deleteNotice(1L,auth);
            verify(notices).delete(notice);clearInvocations(notices);
        }
    }
    @Test void unreadyProductionSchemaFailsClosedAndOnlyReadsCatalog() {
        when(jdbc.queryForObject(anyString(),eq(Boolean.class))).thenReturn(false);
        assertThrows(IllegalStateException.class,policy::afterPropertiesSet);
        when(jdbc.queryForObject(anyString(),eq(Boolean.class))).thenReturn(true);policy.afterPropertiesSet();
        verify(jdbc,times(2)).queryForObject(argThat(sql->sql.stripLeading().startsWith("SELECT EXISTS")
            &&sql.contains("a.attnotnull")&&sql.contains("NOT legacy.attnotnull")
            &&sql.contains("c.convalidated")&&sql.contains("NOT c.condeferrable")),eq(Boolean.class));
        verifyNoInteractions(members);
    }
}
