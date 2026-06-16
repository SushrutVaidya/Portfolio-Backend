package com.sushrut.portfolio.backend.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

	private final RestTemplate restTemplate = new RestTemplate();
	private List<SteamGameResponse> cachedGames;
	private long cacheTimeStamp;
	private static final long CACHE_TTL = 3600000;

	@SuppressWarnings("unchecked")
	public List<SteamGameResponse> getOwnedGames() {
		if (cachedGames != null && (System.currentTimeMillis() - cacheTimeStamp) < CACHE_TTL) {
			return cachedGames;
		}

		try {
			String url = "https://api.steampowered.com/IPlayerService/GetOwnedGames/v1/" + "?key=" + apiKey
					+ "&steamid=" + steamId + "&include_appinfo=1" + "&include_played_free_games=1" + "&format=json";

			Map<String, Object> body = restTemplate.getForObject(url, Map.class);
			Map<String, Object> response = (Map<String, Object>) body.get("response");
			List<Map<String, Object>> games = (List<Map<String, Object>>) response.get("games");

			if (games == null)
				return List.of();

			cachedGames = games.stream()
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

			cacheTimeStamp = System.currentTimeMillis();
			return cachedGames;

		} catch (Exception e) {
			log.warn("Failed to fetch Steam games: {}", e.getMessage());
			return cachedGames != null ? cachedGames : List.of();
		}
	}

}
