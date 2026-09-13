package com.gwonsystem.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class NoticeCreateRequestDto {

    private Long authorId;

    @NotBlank(message = "카테고리를 선택해 주세요.")
    private String category;

    @NotBlank(message = "공지 제목은 필수입니다.")
    private String title;

    @NotBlank(message = "공지 본문은 필수입니다.")
    private String content;

    private Boolean isPinned;
}