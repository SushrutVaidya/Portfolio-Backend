package com.sushrut.portfolio.backend.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Central error handler.
 *
 * All controller exceptions land here so we return a consistent JSON body
 * ({@code {status, error, message}}) instead of the default Spring error
 * page, and so unexpected exceptions never leak stack traces or internal
 * paths to the client. Real cause is logged server-side.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
		String message = ex.getBindingResult().getFieldErrors().stream()
				.map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
				.collect(Collectors.joining("; "));
		return body(HttpStatus.BAD_REQUEST, message.isEmpty() ? "Validation failed" : message);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<Map<String, Object>> handleMalformedJson(HttpMessageNotReadableException ex) {
		// Bad JSON body — Jackson couldn't parse it. Don't echo Jackson's error
		// (has line numbers into user's payload), just say what went wrong.
		return body(HttpStatus.BAD_REQUEST, "Malformed JSON body");
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

	/**
	 * Catch-all — anything not handled above.
	 * Logs the full stack server-side, returns a generic 500 to the client.
	 * Without this, an unhandled RuntimeException would render Spring's
	 * default whitelabel error with a stack trace embedded on some setups.
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<Map<String, Object>> handleAny(Exception ex) {
		log.error("Unhandled exception on request", ex);
		return body(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
	}

	private static ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
		Map<String, Object> b = new LinkedHashMap<>();
		b.put("status", status.value());
		b.put("error", status.getReasonPhrase());
		b.put("message", message);
		return ResponseEntity.status(status).body(b);
	}
}

