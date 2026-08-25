package com.sushrut.portfolio.backend.service;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.sushrut.portfolio.backend.model.SteamGameResponse;

@Service
public class SteamService {

	private static final Logger log = LoggerFactory.getLogger(SteamService.class);

	@Value("${steam.api.key}")
	private String apiKey;
	@Value("${steam.steam-id}")
	private String steamId;

	// Timeouts prevent a hung Steam API from tying up Tomcat worker threads
	// indefinitely (thread-pool exhaustion under load). 2s connect / 5s read
	// is generous for a healthy Steam call while bounded on failure.
	private final RestTemplate restTemplate = new RestTemplateBuilder()
			.setConnectTimeout(Duration.ofSeconds(2))
			.setReadTimeout(Duration.ofSeconds(5))
			.build();

	// Cache guarded by AtomicReference — concurrent readers see a consistent
	// snapshot (games list + timestamp move together), no torn reads.
	private static final class CacheEntry {
		final List<SteamGameResponse> games;
		final long timestamp;
		CacheEntry(List<SteamGameResponse> games, long timestamp) {
			this.games = games; this.timestamp = timestamp;
		}
	}
	private final AtomicReference<CacheEntry> cache = new AtomicReference<>();
	private static final long CACHE_TTL = 3600000;

	@SuppressWarnings("unchecked")
	public List<SteamGameResponse> getOwnedGames() {
		CacheEntry entry = cache.get();
		if (entry != null && (System.currentTimeMillis() - entry.timestamp) < CACHE_TTL) {
			return entry.games;
		}

		try {
			String url = "https://api.steampowered.com/IPlayerService/GetOwnedGames/v1/" + "?key=" + apiKey
					+ "&steamid=" + steamId + "&include_appinfo=1" + "&include_played_free_games=1" + "&format=json";

			Map<String, Object> body = restTemplate.getForObject(url, Map.class);
			if (body == null) {
				return entry != null ? entry.games : List.of();
			}
			Map<String, Object> response = (Map<String, Object>) body.get("response");
			if (response == null) {
				return entry != null ? entry.games : List.of();
			}
			List<Map<String, Object>> games = (List<Map<String, Object>>) response.get("games");

			if (games == null)
				return List.of();

			List<SteamGameResponse> fresh = games.stream()
					.sorted(Comparator.comparingInt(
							(Map<String, Object> g) -> ((Number) g.getOrDefault("playtime_forever", 0)).intValue())
							.reversed())
					.limit(10).map(g -> {
						int appid = ((Number) g.get("appid")).intValue();
						String name = (String) g.getOrDefault("name", "Unknown");
						int minutes = ((Number) g.getOrDefault("playtime_forever", 0)).intValue();
						int recentMinutes = ((Number) g.getOrDefault("playtime_2weeks", 0)).intValue();

						return new SteamGameResponse(name, Math.round((float) minutes / 60),
								recentMinutes > 0 ? "Recently" : "A while ago", appid);
					}).toList();

			cache.set(new CacheEntry(fresh, System.currentTimeMillis()));
			return fresh;

		} catch (Exception e) {
			log.warn("Failed to fetch Steam games: {}", e.getMessage());
			return entry != null ? entry.games : List.of();
		}
	}
}
