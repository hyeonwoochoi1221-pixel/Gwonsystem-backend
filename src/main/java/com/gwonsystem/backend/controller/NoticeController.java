package com.gwonsystem.backend.controller;

import com.gwonsystem.backend.dto.NoticeCreateRequestDto;
import com.gwonsystem.backend.dto.NoticeResponseDto;
import com.gwonsystem.backend.service.NoticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Notice API", description = "사내 공지사항 관리 API")
@RestController
@RequestMapping("/api/notices")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // 프론트엔드 CORS 허용
public class NoticeController {

    private final NoticeService noticeService;

    @Operation(summary = "공지사항 전체 목록 조회", description = "상단 고정 여부 및 최신순으로 정렬된 공지사항 목록을 반환합니다.")
    @GetMapping
    public ResponseEntity<List<NoticeResponseDto>> getAllNotices() {
        return ResponseEntity.ok(noticeService.getAllNotices());
    }

    @Operation(summary = "공지사항 상세 조회", description = "공지사항 단건을 조회하고 조회수를 1 증가시킵니다.")
    @GetMapping("/{id}")
    public ResponseEntity<NoticeResponseDto> getNotice(@PathVariable Long id) {
        return ResponseEntity.ok(noticeService.getNoticeById(id));
    }

    @Operation(summary = "공지사항 등록", description = "새로운 공지사항을 생성합니다.")
    @PostMapping
    public ResponseEntity<Long> createNotice(@Valid @RequestBody NoticeCreateRequestDto requestDto) {
        Long noticeId = noticeService.createNotice(requestDto);
        return ResponseEntity.ok(noticeId);
    }

    @Operation(summary = "공지사항 삭제", description = "공지사항 ID를 기반으로 해당 공지를 삭제합니다.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNotice(@PathVariable Long id) {
        noticeService.deleteNotice(id);
        return ResponseEntity.noContent().build();
    }
}