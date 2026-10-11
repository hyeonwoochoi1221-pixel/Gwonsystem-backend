package com.gwonsystem.backend.service;

import com.gwonsystem.backend.dto.NoticeCreateRequestDto;
import com.gwonsystem.backend.entity.*;
import com.gwonsystem.backend.repository.MemberRepository;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.access.AccessDeniedException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DevNoticeAuthorPolicyTest {
    @Test void sessionUsernameDeterminesAuthorAndCurrentDatabaseRoleIsRequired() {
        var members=mock(MemberRepository.class);var policy=new DevNoticeAuthorPolicy(members,mock(JdbcTemplate.class));
        var request=new NoticeCreateRequestDto();request.setAuthorId(999L);
        var authentication=new UsernamePasswordAuthenticationToken("dev_admin_test",null,List.of(new SimpleGrantedAuthority("ROLE_SUPERVISOR")));
        var member=new Member();member.setId(17L);member.setRole(Role.ROLE_SUPERVISOR);
        when(members.findByUsername("dev_admin_test")).thenReturn(Optional.of(member));
        assertEquals(new NoticeAuthorPolicy.Author(null,17L),policy.resolve(request,authentication));
        member.setRole(Role.ROLE_REGULAR);assertEquals(17L,policy.resolve(request,authentication).memberId());
        member.setRole(Role.ROLE_ASSOCIATE);assertThrows(AccessDeniedException.class,()->policy.resolve(request,authentication));
        when(members.findByUsername("dev_admin_test")).thenReturn(Optional.empty());
        assertThrows(AuthenticationCredentialsNotFoundException.class,()->policy.resolve(request,authentication));
    }
    @Test void anonymousAndUnauthenticatedPrincipalsAreRejectedBeforeLookup() {
        var members=mock(MemberRepository.class);var policy=new DevNoticeAuthorPolicy(members,mock(JdbcTemplate.class));
        var request=new NoticeCreateRequestDto();
        assertThrows(AuthenticationCredentialsNotFoundException.class,()->policy.resolve(request,null));
        assertThrows(AuthenticationCredentialsNotFoundException.class,()->policy.resolve(request,new UsernamePasswordAuthenticationToken("name",null)));
        assertThrows(AuthenticationCredentialsNotFoundException.class,()->policy.resolve(request,
            new AnonymousAuthenticationToken("key","anonymous",List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")))));
        verifyNoInteractions(members);
    }
    @Test void missingSchemaBlocksDev() {
        var jdbc=mock(JdbcTemplate.class);var policy=new DevNoticeAuthorPolicy(mock(MemberRepository.class),jdbc);
        when(jdbc.queryForObject(anyString(),eq(Boolean.class))).thenReturn(false);
        assertThrows(IllegalStateException.class,policy::afterPropertiesSet);
        when(jdbc.queryForObject(anyString(),eq(Boolean.class))).thenReturn(true);policy.afterPropertiesSet();
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
}
