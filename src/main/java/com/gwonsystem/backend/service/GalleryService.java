package com.gwonsystem.backend.service;

import com.gwonsystem.backend.dto.GalleryPhotoResponse;
import com.gwonsystem.backend.entity.GalleryPhoto;
import com.gwonsystem.backend.repository.GalleryPhotoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class GalleryService {
    private final GalleryPhotoRepository photos;
    private final SupabaseStorageService storage;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GalleryService.class);

    @Transactional(readOnly = true)
    public List<GalleryPhotoResponse> list() { return photos.findLatest(PageRequest.of(0, 24)).stream().map(p -> new GalleryPhotoResponse(p.id(), p.title(), p.description(), p.createdAt(), storage.publicUrl(p.imageUrl()))).toList(); }

    @Transactional(readOnly = true)
    public GalleryPhoto image(Long id) {
        return photos.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사진을 찾을 수 없습니다."));
    }

    public String publicImageUrl(GalleryPhoto photo) { return storage.publicUrl(photo.getStoragePath()); }

    @Transactional
    public void delete(Long id) {
        GalleryPhoto photo = image(id);
        photos.delete(photo);
        photos.flush();
        if (photo.getStoragePath() != null) {
            try { storage.delete(photo.getStoragePath()); }
            catch (RuntimeException e) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "사진 파일을 삭제하지 못했습니다. 다시 시도해 주세요.");
            }
        }
    }

    private void cleanup(String path) {
        try { storage.delete(path); }
        catch (RuntimeException e) { log.error("Gallery rollback cleanup failed for object {}. Manual cleanup required.", path); }
    }

    @Transactional
    public GalleryPhotoResponse create(String title, String description, MultipartFile file, String username) {
        title = title == null ? "" : title.trim();
        description = description == null ? "" : description.trim();
        if (title.isEmpty() || title.length() > 80 || description.length() > 300)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "제목은 1~80자, 설명은 300자 이내로 입력해 주세요.");
        if (file.isEmpty() || file.getSize() > 1024 * 1024)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "1MB 이하의 사진을 선택해 주세요.");
        try {
            byte[] bytes = file.getBytes();
            String type = validateImage(bytes);
            String path = storage.upload(bytes, type);
            boolean synchronizedTransaction = org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive();
            if (synchronizedTransaction) {
                org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCompletion(int status) {
                        if (status == STATUS_ROLLED_BACK) cleanup(path);
                    }
                });
            }
            try {
                GalleryPhoto saved = photos.saveAndFlush(GalleryPhoto.stored(title, description, type, path, username));
                return new GalleryPhotoResponse(saved.getId(), saved.getTitle(), saved.getDescription(), saved.getCreatedAt(), storage.publicUrl(path));
            } catch (RuntimeException e) {
                if (!synchronizedTransaction) cleanup(path);
                throw e;
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "사진을 읽을 수 없습니다.");
        }
    }

    private String validateImage(byte[] bytes) throws IOException {
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalidImage();
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals("jpeg") && !format.equals("png")) throw invalidImage();
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || (long) width * height > 20_000_000) throw invalidImage();
                if (reader.read(0) == null) throw invalidImage();
                return "image/" + format;
            } finally { reader.dispose(); }
        }
    }
    private ResponseStatusException invalidImage() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "유효한 JPG 또는 PNG 사진을 선택해 주세요. 사진은 최대 2천만 화소까지 지원합니다.");
    }
}
