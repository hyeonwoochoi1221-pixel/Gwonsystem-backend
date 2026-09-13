package com.gwonsystem.backend.dto;

import com.gwonsystem.backend.entity.Notice;
import lombok.Getter;
import java.time.format.DateTimeFormatter;

@Getter
public class NoticeResponseDto {

    private final Long id;
    private final String category;
    private final String title;
    private final String content;
    private final Integer viewCount;
    private final Boolean isPinned;
    private final String date; // YYYY-MM-DD 포맷 변환 문자열

    public NoticeResponseDto(Notice notice) {
        this.id = notice.getId();
        this.category = notice.getCategory();
        this.title = notice.getTitle();
        this.content = notice.getContent();
        this.viewCount = notice.getViewCount();
        this.isPinned = notice.getIsPinned();
        this.date = notice.getCreatedAt() != null
                ? notice.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                : "";
    }
}