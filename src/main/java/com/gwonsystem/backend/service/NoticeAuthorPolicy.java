package com.gwonsystem.backend.service;

import com.gwonsystem.backend.dto.NoticeCreateRequestDto;
import com.gwonsystem.backend.entity.Notice;
import org.springframework.security.core.Authentication;

/** Profile-specific persistence keeps the legacy production schema untouched. */
public interface NoticeAuthorPolicy {
    record Author(Long legacyUserId, Long memberId) {}
    Author resolve(NoticeCreateRequestDto request, Authentication authentication);
    Long create(Notice notice, Author author);
}
