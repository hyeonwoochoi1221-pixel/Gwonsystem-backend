package com.gwonsystem.backend.service;

import com.gwonsystem.backend.dto.BannerResponse;
import com.gwonsystem.backend.entity.Banner;
import com.gwonsystem.backend.repository.BannerRepository;
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
public class BannerService {
    private final BannerRepository photos;

    @Transactional(readOnly = true)
    public List<BannerResponse> list() { return photos.findLatest(PageRequest.of(0, 24)); }

    @Transactional(readOnly = true)
    public Banner image(Long id) {
        return photos.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사진을 찾을 수 없습니다."));
    }

    @Transactional
    public BannerResponse create(String title, String description, MultipartFile file, String username) {
        title = title == null ? "" : title.trim();
        description = description == null ? "" : description.trim();
        if (title.isEmpty() || title.length() > 80 || description.length() > 300)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "제목은 1~80자, 설명은 300자 이내로 입력해 주세요.");
        if (file.isEmpty() || file.getSize() > 1024 * 1024)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "1MB 이하의 사진을 선택해 주세요.");
        try {
            byte[] bytes = file.getBytes();
            String type = validateImage(bytes);
            photos.lockForCreation();
            if (photos.count() >= 5)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "배너는 최대 5개까지 등록할 수 있습니다.");
            Banner saved = photos.save(new Banner(title, description, type, bytes, username));
            return new BannerResponse(saved.getId(), saved.getTitle(), saved.getDescription(), saved.getCreatedAt());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "사진을 읽을 수 없습니다.");
        }
    }

    @Transactional
    public void delete(Long id) { photos.delete(image(id)); }

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
