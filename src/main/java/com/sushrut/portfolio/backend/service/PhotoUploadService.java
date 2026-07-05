package com.sushrut.portfolio.backend.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import net.coobird.thumbnailator.Thumbnails;

@Service
public class PhotoUploadService {

	private static final Logger log = LoggerFactory.getLogger(PhotoUploadService.class);

	private static final long MAX_UPLOAD_BYTES = 10 * 1024 * 1024;
	private static final Set<String> ALLOWED_TYPES = Set.of(
			"image/jpeg", "image/jpg", "image/png", "image/webp", "image/heic");
	private static final int TARGET_DIMENSION = 600;
	private static final float JPEG_QUALITY = 0.75f;

	@Value("${portfolio.uploads.dir:./uploads}")
	private String uploadsDir;

	public String saveUserPhoto(UUID userId, MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No file provided");
		}
		if (file.getSize() > MAX_UPLOAD_BYTES) {
			throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Image must be under 10 MB");
		}
		String contentType = file.getContentType();
		if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
			throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
					"Only JPG, PNG, WEBP, or HEIC images allowed");
		}

		try {
			Path dir = Paths.get(uploadsDir).toAbsolutePath();
			Files.createDirectories(dir);

			Path target = dir.resolve(userId.toString() + ".jpg");

			Thumbnails.of(file.getInputStream())
					.size(TARGET_DIMENSION, TARGET_DIMENSION)
					.outputFormat("jpg")
					.outputQuality(JPEG_QUALITY)
					.toFile(target.toFile());

			return "/uploads/" + userId.toString() + ".jpg?v=" + System.currentTimeMillis();
		} catch (IOException e) {
			// Log the real cause server-side (paths, IO errno, Thumbnailator
			// internals) but return a generic message — avoid leaking system
			// details to unauthenticated callers.
			log.error("Photo upload failed for userId={}", userId, e);
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
					"Could not process image. Try a different file.");
		}
	}
}
