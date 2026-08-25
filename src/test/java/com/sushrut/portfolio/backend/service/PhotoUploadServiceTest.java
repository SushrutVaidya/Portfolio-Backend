package com.sushrut.portfolio.backend.service;

import java.io.ByteArrayInputStream;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Validation-only tests for PhotoUploadService. Actual thumbnail generation
 * is exercised in PhotoUploadIT with a real image file — here we assert
 * the endpoint's guardrails reject bad input before any I/O.
 */
@ExtendWith(MockitoExtension.class)
class PhotoUploadServiceTest {

    @InjectMocks private PhotoUploadService service;

    @TempDir Path tmp;

    private void configureUploadsDir() {
        ReflectionTestUtils.setField(service, "uploadsDir", tmp.toString());
    }

    @Test
    void rejects_null() {
        configureUploadsDir();
        assertThatThrownBy(() -> service.saveUserPhoto(UUID.randomUUID(), null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("No file");
    }

    @Test
    void rejects_empty() {
        configureUploadsDir();
        MultipartFile f = new MockMultipartFile("file", "x.jpg", "image/jpeg", new byte[0]);
        assertThatThrownBy(() -> service.saveUserPhoto(UUID.randomUUID(), f))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("No file");
    }

    @Test
    void rejects_oversized() {
        configureUploadsDir();
        // 10MB+1 byte
        byte[] tooBig = new byte[10 * 1024 * 1024 + 1];
        MultipartFile f = new MockMultipartFile("file", "big.jpg", "image/jpeg", tooBig);
        assertThatThrownBy(() -> service.saveUserPhoto(UUID.randomUUID(), f))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("10 MB");
    }

    @Test
    void rejects_disallowedContentType() {
        configureUploadsDir();
        MultipartFile f = new MockMultipartFile("file", "x.svg", "image/svg+xml", new byte[] { 1, 2, 3 });
        assertThatThrownBy(() -> service.saveUserPhoto(UUID.randomUUID(), f))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("JPG, PNG");
    }

    @Test
    void rejects_missingContentType() {
        configureUploadsDir();
        MultipartFile f = new MockMultipartFile("file", "x", null, new byte[] { 1, 2, 3 });
        assertThatThrownBy(() -> service.saveUserPhoto(UUID.randomUUID(), f))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("JPG, PNG");
    }
}
