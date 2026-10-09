package com.gwonsystem.backend;

import com.gwonsystem.backend.entity.GalleryPhoto;
import com.gwonsystem.backend.repository.GalleryPhotoRepository;
import com.gwonsystem.backend.service.GalleryService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GalleryServiceTest {
    GalleryPhotoRepository repository = mock(GalleryPhotoRepository.class);
    com.gwonsystem.backend.service.SupabaseStorageService storage = mock(com.gwonsystem.backend.service.SupabaseStorageService.class);
    GalleryService service = new GalleryService(repository, storage);

    @Test void rejectsDisguisedAndOversizedUploadsBeforeSaving() {
        assertThrows(ResponseStatusException.class, () -> service.create("Title", "", new MockMultipartFile("photo", "fake.png", "image/png", "not a photo".getBytes()), "author"));
        assertThrows(ResponseStatusException.class, () -> service.create("Title", "", new MockMultipartFile("photo", new byte[1024 * 1024 + 1]), "author"));
        assertThrows(ResponseStatusException.class, () -> service.create("   ", "", new MockMultipartFile("photo", new byte[]{1}), "author"));
        verifyNoInteractions(repository);
    }
    @Test void detectsActualImageTypeInsteadOfTrustingClientMime() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        when(storage.upload(any(), anyString())).thenReturn("gallery/test.png");
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service.create(" Title ", " Description ", new MockMultipartFile("photo", "photo.jpg", "image/jpeg", output.toByteArray()), "author");
        var captor = org.mockito.ArgumentCaptor.forClass(GalleryPhoto.class);
        verify(repository).saveAndFlush(captor.capture());
        assertEquals("image/png", captor.getValue().getContentType());
        assertEquals("Title", captor.getValue().getTitle());
        assertEquals("author", captor.getValue().getCreatedBy());
        assertEquals("gallery/test.png", captor.getValue().getStoragePath());
        assertEquals(0, captor.getValue().getImage().length);
        verify(storage).upload(eq(output.toByteArray()), eq("image/png"));
    }
    @Test void databaseFailureRemovesUploadedObject() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        when(storage.upload(any(), anyString())).thenReturn("gallery/test.png");
        when(repository.saveAndFlush(any())).thenThrow(new IllegalStateException("database failure"));
        assertThrows(IllegalStateException.class, () -> service.create("Title", "", new MockMultipartFile("photo", "photo.png", "image/png", output.toByteArray()), "author"));
        verify(storage).delete("gallery/test.png");
    }
    @Test void transactionRollbackRemovesObjectButCommitKeepsIt() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        when(storage.upload(any(), anyString())).thenReturn("gallery/test.png");
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            service.create("Title", "", new MockMultipartFile("photo", "photo.png", "image/png", output.toByteArray()), "author");
            var callback = org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations().get(0);
            callback.afterCompletion(org.springframework.transaction.support.TransactionSynchronization.STATUS_COMMITTED);
            verify(storage, never()).delete(anyString());
            callback.afterCompletion(org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK);
            verify(storage).delete("gallery/test.png");
        } finally { org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization(); }
    }
    @Test void deletionRemovesStorageObjectAfterDatabaseFlush() {
        var photo = GalleryPhoto.stored("Title", "", "image/png", "gallery/test.png", "author");
        when(repository.findById(1L)).thenReturn(java.util.Optional.of(photo));
        service.delete(1L);
        var order = inOrder(repository, storage);
        order.verify(repository).delete(photo);
        order.verify(repository).flush();
        order.verify(storage).delete("gallery/test.png");
    }
    @Test void databaseDeleteFailureKeepsStorageObject() {
        var photo = GalleryPhoto.stored("Title", "", "image/png", "gallery/test.png", "author");
        when(repository.findById(1L)).thenReturn(java.util.Optional.of(photo));
        doThrow(new IllegalStateException()).when(repository).flush();
        assertThrows(IllegalStateException.class, () -> service.delete(1L));
        verifyNoInteractions(storage);
    }
    @Test void legacyPhotoDeletionDoesNotCallStorage() {
        var photo = new GalleryPhoto("Title", "", "image/png", new byte[]{1}, "author");
        when(repository.findById(1L)).thenReturn(java.util.Optional.of(photo));
        service.delete(1L);
        verify(repository).delete(photo);
        verifyNoInteractions(storage);
    }
}
