package com.sushrut.portfolio.backend.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.sushrut.portfolio.backend.entities.GameUser;
import com.sushrut.portfolio.backend.model.JukeboxTrack;
import com.sushrut.portfolio.backend.model.PlayerCardRequest;
import com.sushrut.portfolio.backend.model.PlayerCardResponse;
import com.sushrut.portfolio.backend.model.StatsResponse;
import com.sushrut.portfolio.backend.model.SteamGameResponse;
import com.sushrut.portfolio.backend.service.AuthTokenService;
import com.sushrut.portfolio.backend.service.StatsService;
import com.sushrut.portfolio.backend.service.SteamService;
import com.sushrut.portfolio.backend.service.GameUserService;
import com.sushrut.portfolio.backend.service.PhotoUploadService;
import com.sushrut.portfolio.backend.service.RickrollService;
import com.sushrut.portfolio.backend.service.JukeboxService;
import com.sushrut.portfolio.backend.service.PrintRequestService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class controller {

	@Autowired
	private StatsService ss;

	@Autowired
	private RickrollService rickrollService;

	@Autowired
	private JukeboxService jukeboxService;

	@Autowired
	private PrintRequestService printRequestService;

	@Autowired
	private GameUserService gUserService;

	@Autowired
	private SteamService steamService;

	@Autowired
	private PhotoUploadService photoUploadService;

	@Autowired
	private AuthTokenService auth;

	@GetMapping("/stats")
	public StatsResponse getStats() {
		return ss.getStats();
	}

	@GetMapping("/rickroll")
	public Map<String, Integer> incrementRickroll() {
		int count = rickrollService.incrementAndGet();
		return Map.of("count", count);
	}

	@GetMapping("/rickroll/count")
	public Map<String, Integer> getRickrollCount() {
		return Map.of("count", rickrollService.getCount());
	}

	@PostMapping("/user")
	public ResponseEntity<?> registerUser(@RequestBody Map<String, String> body) {
		String firstName = body.get("firstName");
		String lastName  = body.get("lastName");
		if (firstName == null || firstName.trim().length() < 2
				|| lastName == null || lastName.trim().length() < 2) {
			return ResponseEntity.badRequest()
					.body(Map.of("error", "Name must be at least 2 characters"));
		}
		if (firstName.trim().isBlank() || lastName.trim().isBlank()) {
			return ResponseEntity.badRequest()
					.body(Map.of("error", "Name cannot be whitespace only"));
		}
		GameUser user = gUserService.registerOrGet(firstName.trim(), lastName.trim());
		// Mint an HMAC token bound to this user's UUID. Client stores it and
		// sends X-DQ-Token on every mutating request. Deterministic — same user
		// always gets the same token, so existing users re-registering with the
		// same name get their token back (no data migration needed).
		String token = auth.generateToken(user.getId());
		return ResponseEntity.ok(Map.of(
			"id",        user.getId(),
			"firstName", user.getFirstName(),
			"lastName",  user.getLastName(),
			"token",     token
		));
	}

	@GetMapping("/user/{id}/card")
	public ResponseEntity<PlayerCardResponse> getCard(@PathVariable UUID id) {
		return ResponseEntity.ok(gUserService.getCard(id));
	}

	@PutMapping("/user/{id}/card")
	public ResponseEntity<PlayerCardResponse> updateCard(@PathVariable UUID id,
			@Valid @RequestBody PlayerCardRequest req, HttpServletRequest http) {
		auth.assertOwns(id, http);
		return ResponseEntity.ok(gUserService.updateCard(id, req));
	}

	@PostMapping("/user/{id}/photo")
	public ResponseEntity<PlayerCardResponse> uploadPhoto(@PathVariable UUID id,
			@RequestParam("file") MultipartFile file, HttpServletRequest http) {
		auth.assertOwns(id, http);
		String url = photoUploadService.saveUserPhoto(id, file);
		return ResponseEntity.ok(gUserService.updatePhotoUrl(id, url));
	}

	@PostMapping("/score")
	public ResponseEntity<Map<String, Object>> submitScore(@RequestBody Map<String, Object> body,
			HttpServletRequest http) {
		// Narrow catch: only bad UUID / missing payload = 400. Everything else
		// (DB down, user missing, etc.) propagates to GlobalExceptionHandler
		// which returns a structured response with a real error message and logs.
		Object rawUserId = body.get("userId");
		if (rawUserId == null) {
			return ResponseEntity.badRequest().body(Map.of("error", "userId required"));
		}
		UUID userId;
		try {
			userId = UUID.fromString(rawUserId.toString());
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(Map.of("error", "userId must be a valid UUID"));
		}
		// Auth check after we know userId parses — otherwise error would be
		// "unauthorized" when the real issue is a malformed request.
		auth.assertOwns(userId, http);
		int wpm      = Math.min(300, Math.max(0, ((Number) body.getOrDefault("wpm",      0)).intValue()));
		int accuracy = Math.min(100, Math.max(0, ((Number) body.getOrDefault("accuracy", 0)).intValue()));
		return ResponseEntity.ok(gUserService.submitScore(userId, wpm, accuracy));
	}

	@GetMapping("/leaderboard")
	public ResponseEntity<List<Map<String, Object>>> getLeaderboard() {
		return ResponseEntity.ok(gUserService.getLeaderboard());
	}

	@GetMapping("/steam/games")
	public ResponseEntity<List<SteamGameResponse>> getSteamGames() {
		return ResponseEntity.ok(steamService.getOwnedGames());
	}

	@GetMapping("/jukebox/tracks")
	public ResponseEntity<List<JukeboxTrack>> getJukeboxTracks() {
		return ResponseEntity.ok(jukeboxService.getTracks());
	}

	@GetMapping("/print-request/count")
	public ResponseEntity<Map<String, Object>> getPrintCount() {
		long count = printRequestService.getCount();
		return ResponseEntity.ok(Map.of("claimed", count, "remaining", Math.max(0, 25 - count), "full", count >= 25));
	}

	@PostMapping("/print-request")
	public ResponseEntity<Map<String, Object>> submitPrintRequest(@RequestBody Map<String, String> body) {
		try {
			return ResponseEntity.ok(printRequestService.submit(body));
		} catch (org.springframework.web.server.ResponseStatusException e) {
			return ResponseEntity.status(e.getStatusCode())
					.body(Map.of("error", e.getReason() != null ? e.getReason() : "Request failed"));
		}
	}

}
