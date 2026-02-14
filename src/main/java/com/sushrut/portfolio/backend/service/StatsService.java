package com.sushrut.portfolio.backend.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.sushrut.portfolio.backend.model.StatsResponse;

@Service
public class StatsService {
	@Value("${portfolio.location}")
	private String location;
	@Value("${portfolio.game}")
	private String gameName;
	@Value("${portfolio.songName}")
	private String songName;

	private String getSongURL() {
		String[] songs = { "Funkadelic.mp3", // 12am - 4am
				"Lawrie.mp3", // 4am - 8am
				"Aladeeeeen.mp3", // 8am - 12pm
				"ManMazeUmalunGele.mp3", // 12pm - 4pm
				"EverybodyWantsToRuleTheWorldJoshGad.mp3", // 4pm - 8pm
				"miMorchaNelaNahi.mp3" // 8pm - 12am
		};
		int hour = LocalDateTime.now().getHour();
		int slot = hour / 4;
		return "/audio/" + songs[slot];
	}

	public StatsResponse getStats() {
		LocalDateTime now = LocalDateTime.now();

		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("h:mm");
		String currenttime = now.format(formatter).toLowerCase();
		String prewviewURL = getSongURL();
		return new StatsResponse(location, gameName, currenttime, System.currentTimeMillis(), songName, prewviewURL);
	}

}
