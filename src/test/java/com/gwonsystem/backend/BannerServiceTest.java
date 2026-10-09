package com.gwonsystem.backend;

import com.gwonsystem.backend.entity.Banner;
import com.gwonsystem.backend.repository.BannerRepository;
import com.gwonsystem.backend.service.BannerService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BannerServiceTest {
    BannerRepository repository = mock(BannerRepository.class);
    BannerService service = new BannerService(repository);

    @Test void rejectsDisguisedAndOversizedUploadsBeforeSaving() {
        assertThrows(ResponseStatusException.class, () -> service.create("Title", "", new MockMultipartFile("photo", "fake.png", "image/png", "not a photo".getBytes()), "author"));
        assertThrows(ResponseStatusException.class, () -> service.create("Title", "", new MockMultipartFile("photo", new byte[1024 * 1024 + 1]), "author"));
        assertThrows(ResponseStatusException.class, () -> service.create("   ", "", new MockMultipartFile("photo", new byte[]{1}), "author"));
        verifyNoInteractions(repository);
    }
    @Test void detectsActualImageTypeInsteadOfTrustingClientMime() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service.create(" Title ", " Description ", new MockMultipartFile("photo", "photo.jpg", "image/jpeg", output.toByteArray()), "author");
        var captor = org.mockito.ArgumentCaptor.forClass(Banner.class);
        verify(repository).save(captor.capture());
        assertEquals("image/png", captor.getValue().getContentType());
        assertEquals("Title", captor.getValue().getTitle());
        assertEquals("author", captor.getValue().getCreatedBy());
        assertArrayEquals(output.toByteArray(), captor.getValue().getImage());
    }
    @Test void sixthBannerIsRejectedBeforeSaving() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        when(repository.count()).thenReturn(5L);
        var failure = assertThrows(ResponseStatusException.class, () -> service.create("Title", "", new MockMultipartFile("photo", "banner.png", "image/png", output.toByteArray()), "admin"));
        assertEquals(org.springframework.http.HttpStatus.CONFLICT, failure.getStatusCode());
        var order = inOrder(repository);
        order.verify(repository).lockForCreation();
        order.verify(repository).count();
        verify(repository, never()).save(any());
    }
}
