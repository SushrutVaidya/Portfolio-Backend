package com.sushrut.portfolio.backend.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sushrut.portfolio.backend.entities.GameUser;
import com.sushrut.portfolio.backend.model.StatsResponse;
import com.sushrut.portfolio.backend.model.SteamGameResponse;
import com.sushrut.portfolio.backend.service.StatsService;
import com.sushrut.portfolio.backend.service.SteamService;
import com.sushrut.portfolio.backend.service.GameUserService;
import com.sushrut.portfolio.backend.service.RickrollService;

@RestController
@RequestMapping("/api")
public class controller {
	@Autowired
	private StatsService ss;

	@Autowired
	private RickrollService rickrollService;

	@Autowired
	private RedisTemplate<String, String> redisTemplate;

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

	@GetMapping("/redis/test")
	public ResponseEntity<Map<String, String>> testRedis() {
		try {
			redisTemplate.opsForValue().set("test:key", "test:value");
			String value = redisTemplate.opsForValue().get("test:key");
			return ResponseEntity.ok(Map.of("status", "success", "message", "Redis is connected", "test",
					value != null ? value : "null"));
		} catch (Exception e) {
			return ResponseEntity.status(500)
					.body(Map.of("status", "error", "message", "Redis connection failed: " + e.getMessage()));
		}
	}

	//Leaderboard for users
	@Autowired
	private GameUserService gUserService;

	@PostMapping("/user")
	public ResponseEntity<?> registerUser(@RequestBody Map<String, String> body) {
		String firstName = body.get("firstName");
		String lastName = body.get("lastName");
		if (firstName == null || firstName.trim().length() < 2 || lastName == null || lastName.trim().length() < 2) {
			return ResponseEntity.badRequest()
					.body(Map.of("error", "Name Length is too short honey , Fill in something longer"));
		}
		GameUser user = gUserService.registerOrGet(firstName.trim(), lastName.trim());
		return ResponseEntity.ok(user);

	}

	@Autowired
	private SteamService SS;

	@GetMapping("/steam/games")
	public ResponseEntity<List<SteamGameResponse>> getSteamGames() {
		return ResponseEntity.ok(SS.getOwnedGames());

	}

}
