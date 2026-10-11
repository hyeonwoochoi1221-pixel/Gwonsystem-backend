package com.gwonsystem.backend.service;

import com.gwonsystem.backend.dto.NoticeCreateRequestDto;
import com.gwonsystem.backend.entity.Notice;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** Existing non-development author_id behavior; no reference to the new column. */
@Component
@Profile("!dev")
public class LegacyNoticeAuthorPolicy implements NoticeAuthorPolicy {
    @Override public Author resolve(NoticeCreateRequestDto request, Authentication authentication) {
        return new Author(request.getAuthorId(), null);
    }
    @Override public Long create(Notice notice, Author author) {
        throw new IllegalStateException("Legacy notices must use the existing JPA writer");
    }
}
