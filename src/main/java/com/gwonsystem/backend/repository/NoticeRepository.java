package com.gwonsystem.backend.repository;

import com.gwonsystem.backend.entity.Notice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NoticeRepository extends JpaRepository<Notice, Long> {

    // 고정 공지 우선(true -> false), 그 다음 최신순(id 내림차순) 정렬
    List<Notice> findAllByOrderByIsPinnedDescIdDesc();
}