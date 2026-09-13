package com.gwonsystem.backend.service;

import com.gwonsystem.backend.dto.NoticeCreateRequestDto;
import com.gwonsystem.backend.dto.NoticeResponseDto;
import com.gwonsystem.backend.entity.Notice;
import com.gwonsystem.backend.repository.NoticeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeService {

    private final NoticeRepository noticeRepository;

    // 공지사항 전체 목록 조회
    @Transactional(readOnly = true)
    public List<NoticeResponseDto> getAllNotices() {
        log.info("공지사항 전체 목록 조회 호출");
        return noticeRepository.findAllByOrderByIsPinnedDescIdDesc()
                .stream()
                .map(NoticeResponseDto::new)
                .collect(Collectors.toList());
    }

    // 공지사항 단건 조회 (상세보기 및 조회수 증가)
    @Transactional
    public NoticeResponseDto getNoticeById(Long id) {
        log.info("공지사항 상세 조회 - ID: {}", id);
        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 ID의 공지사항이 존재하지 않습니다: " + id));

        notice.increaseViewCount();
        return new NoticeResponseDto(notice);
    }

    // 신규 공지사항 등록
    @Transactional
    public Long createNotice(NoticeCreateRequestDto requestDto) {
        log.info("새로운 공지사항 등록 요청 - 제목: {}", requestDto.getTitle());

        Notice notice = Notice.builder()
                .authorId(requestDto.getAuthorId())
                .category(requestDto.getCategory())
                .title(requestDto.getTitle())
                .content(requestDto.getContent())
                .isPinned(requestDto.getIsPinned())
                .build();

        Notice savedNotice = noticeRepository.save(notice);
        return savedNotice.getId();
    }

    @Transactional
    public void deleteNotice(Long id) {
        log.info("공지사항 삭제 요청 - ID: {}", id);
        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("삭제할 공지사항이 없습니다: " + id));
        noticeRepository.delete(notice);
    }
}