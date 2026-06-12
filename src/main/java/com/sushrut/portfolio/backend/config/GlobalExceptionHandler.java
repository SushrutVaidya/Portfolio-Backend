package com.sushrut.portfolio.backend.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
		String message = ex.getBindingResult().getFieldErrors().stream()
				.map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
				.collect(Collectors.joining("; "));
		return body(HttpStatus.BAD_REQUEST, message.isEmpty() ? "Validation failed" : message);
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<Map<String, Object>> handleSizeLimit(MaxUploadSizeExceededException ex) {
		return body(HttpStatus.PAYLOAD_TOO_LARGE, "File too large — max 10 MB");
	}

	@ExceptionHandler(MultipartException.class)
	public ResponseEntity<Map<String, Object>> handleMultipart(MultipartException ex) {
		return body(HttpStatus.BAD_REQUEST, "No file provided or invalid multipart request");
	}

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException ex) {
		HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
		return body(status, ex.getReason() != null ? ex.getReason() : status.getReasonPhrase());
	}

	private static ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
		Map<String, Object> b = new LinkedHashMap<>();
		b.put("status", status.value());
		b.put("error", status.getReasonPhrase());
		b.put("message", message);
		return ResponseEntity.status(status).body(b);
	}
}
