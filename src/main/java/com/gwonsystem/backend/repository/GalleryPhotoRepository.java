package com.gwonsystem.backend.repository;

import com.gwonsystem.backend.entity.GalleryPhoto;
import com.gwonsystem.backend.dto.GalleryPhotoResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface GalleryPhotoRepository extends JpaRepository<GalleryPhoto, Long> {
    // Retrieve metadata only: photo bytes must not be loaded for the gallery list.
    @Query("select new com.gwonsystem.backend.dto.GalleryPhotoResponse(p.id, p.title, p.description, p.createdAt, p.storagePath) from GalleryPhoto p order by p.id desc")
    List<GalleryPhotoResponse> findLatest(Pageable pageable);
}
