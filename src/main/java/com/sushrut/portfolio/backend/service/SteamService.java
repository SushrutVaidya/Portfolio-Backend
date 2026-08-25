package com.sushrut.portfolio.backend.service;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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
	// Spring Boot 4 removed RestTemplateBuilder — configure the factory directly.
	private final RestTemplate restTemplate = buildRestTemplate();

	private static RestTemplate buildRestTemplate() {
		SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
		f.setConnectTimeout((int) Duration.ofSeconds(2).toMillis());
		f.setReadTimeout((int) Duration.ofSeconds(5).toMillis());
		return new RestTemplate(f);
	}

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
	// TTL for a good response — 1 hour matches Steam's polite-caller guidance.
	private static final long CACHE_TTL_OK   = 3600_000;
	// TTL for a failure / empty response — short enough that Steam recovering
	// (or the operator finally setting STEAM_API_KEY) shows within a few
	// minutes, long enough that we don't hammer Steam on every page load.
	// Trace showed every call was ~400ms because we NEVER cached the "no
	// games returned" path — every request re-hit Steam.
	private static final long CACHE_TTL_FAIL =  120_000;

	@SuppressWarnings("unchecked")
	public List<SteamGameResponse> getOwnedGames() {
		CacheEntry entry = cache.get();
		if (entry != null) {
			long ttl = entry.games.isEmpty() ? CACHE_TTL_FAIL : CACHE_TTL_OK;
			if (System.currentTimeMillis() - entry.timestamp < ttl) {
				return entry.games;
			}
		}

		try {
			String url = "https://api.steampowered.com/IPlayerService/GetOwnedGames/v1/" + "?key=" + apiKey
					+ "&steamid=" + steamId + "&include_appinfo=1" + "&include_played_free_games=1" + "&format=json";

			Map<String, Object> body = restTemplate.getForObject(url, Map.class);
			if (body == null) {
				return cacheAndReturn(entry, List.of());
			}
			Map<String, Object> response = (Map<String, Object>) body.get("response");
			if (response == null) {
				return cacheAndReturn(entry, List.of());
			}
			List<Map<String, Object>> games = (List<Map<String, Object>>) response.get("games");

			if (games == null) {
				return cacheAndReturn(entry, List.of());
			}

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
			// Never let the raw exception message hit logs — it can include
			// the full request URL with the API key as a query param.
			log.warn("Failed to fetch Steam games: {}", scrubSecrets(e.getMessage()));
			// Serve whatever we last had (even empty) and stamp the cache with
			// FAIL TTL so we don't retry on every subsequent request.
			return cacheAndReturn(entry, entry != null ? entry.games : List.of());
		}
	}

	/**
	 * Cache a result (including empty/failure) and return it. Prevents the
	 * "retry Steam on every request" storm that made this endpoint 400ms
	 * every call before this fix.
	 */
	private List<SteamGameResponse> cacheAndReturn(CacheEntry prev, List<SteamGameResponse> fresh) {
		cache.set(new CacheEntry(fresh, System.currentTimeMillis()));
		return fresh;
	}

	/**
	 * Strip anything that looks like a `key=<value>` pair from a message.
	 * RestTemplate error messages routinely include the offending URL, and
	 * the Steam API key sits in that URL as a query param. Never log that.
	 */
	private static String scrubSecrets(String msg) {
		if (msg == null) return "";
		return msg.replaceAll("(?i)(key|token|password)=[^&\\s]+", "$1=***");
	}
}
