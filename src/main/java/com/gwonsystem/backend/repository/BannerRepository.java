package com.gwonsystem.backend.repository;

import com.gwonsystem.backend.entity.Banner;
import com.gwonsystem.backend.dto.BannerResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface BannerRepository extends JpaRepository<Banner, Long> {
    // Serialize create operations across application instances before checking the limit.
    @org.springframework.data.jpa.repository.Modifying
    @Query(value = "LOCK TABLE home_banners IN EXCLUSIVE MODE", nativeQuery = true)
    void lockForCreation();
    // Retrieve metadata only: photo bytes must not be loaded for the gallery list.
    @Query("select new com.gwonsystem.backend.dto.BannerResponse(p.id, p.title, p.description, p.createdAt) from Banner p order by p.id desc")
    List<BannerResponse> findLatest(Pageable pageable);
}
