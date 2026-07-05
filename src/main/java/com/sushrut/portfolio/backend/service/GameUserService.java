package com.sushrut.portfolio.backend.service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sushrut.portfolio.backend.entities.GameUser;
import com.sushrut.portfolio.backend.model.PlayerCardRequest;
import com.sushrut.portfolio.backend.model.PlayerCardResponse;
import com.sushrut.portfolio.backend.service.impl.GameUserRepository;

@Service
public class GameUserService {
	@Autowired
	private GameUserRepository GameUserRepo;

	public GameUser registerOrGet(String firstName, String lastName) {
		// Canonicalize: Title Case, trim, collapse internal whitespace.
		// Prevents duplicate rows for "sushrut vaidya" vs "Sushrut Vaidya"
		// vs "SUSHRUT  VAIDYA" — the DB unique constraint we just added would
		// reject the concurrent-insert case, but this also fixes the sequential
		// "user typed name slightly differently second visit" scenario.
		String canonFirst = canonicalize(firstName);
		String canonLast  = canonicalize(lastName);
		Optional<GameUser> existing = GameUserRepo.findByFirstNameAndLastName(canonFirst, canonLast);
		if (existing.isPresent()) {
			return existing.get();
		}
		GameUser user = new GameUser();
		user.setFirstName(canonFirst);
		user.setLastName(canonLast);
		return GameUserRepo.save(user);
	}

	private static String canonicalize(String s) {
		if (s == null) return "";
		String cleaned = s.trim().replaceAll("\\s+", " ");
		if (cleaned.isEmpty()) return "";
		// Title-case: first letter upper, rest lower per word
		StringBuilder out = new StringBuilder(cleaned.length());
		boolean nextUpper = true;
		for (int i = 0; i < cleaned.length(); i++) {
			char c = cleaned.charAt(i);
			if (Character.isWhitespace(c)) { out.append(c); nextUpper = true; continue; }
			out.append(nextUpper ? Character.toUpperCase(c) : Character.toLowerCase(c));
			nextUpper = false;
		}
		return out.toString();
	}

	private PlayerCardResponse toResponse(GameUser usr) {
		PlayerCardResponse res = new PlayerCardResponse();
		res.setId(usr.getId());
		res.setBio(usr.getBio());
		res.setFirstName(usr.getFirstName());
		res.setLastName(usr.getLastName());
		res.setClassRole(usr.getClassRole());
		res.setMotto(usr.getMotto());
		res.setPhotoUrl(usr.getPhotoUrl());
		res.setCardStyle(usr.getCardStyle());
		res.setWantedLevel(usr.getWantedLevel());
		res.setWantedText(usr.getWantedText());
		res.setLevel(usr.getLevel());
		res.setXpPercent(usr.getXpPercent());
		res.setProfileComplete(usr.getProfileComplete());
		res.setCreatedAt(usr.getCreatedAt());
		res.setUpdatedAt(usr.getUpdatedAt());

		Map<String, Integer> stats = new LinkedHashMap<>();
		stats.put("DEV", usr.getStatDev());
		stats.put("DESIGN", usr.getStatDesign());
		stats.put("BRAIN", usr.getStatBrain());
		stats.put("SOCIAL", usr.getStatSocial());
		stats.put("GRIND", usr.getStatGrind());
		res.setStats(stats);

		if (usr.getTraits() != null && !usr.getTraits().isBlank()) {
			res.setTraits(Arrays.asList(usr.getTraits().split(",")));
		} else {
			res.setTraits(List.of());
		}

		return res;
	}

	public PlayerCardResponse getCard(UUID id) {
		GameUser usr = GameUserRepo.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
		return toResponse(usr);
	}

	@Transactional
	public PlayerCardResponse updateCard(UUID id, PlayerCardRequest req) {
		GameUser usr = GameUserRepo.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
		if (req.getClassRole() != null)   usr.setClassRole(req.getClassRole());
		if (req.getBio() != null)          usr.setBio(req.getBio());
		if (req.getMotto() != null)        usr.setMotto(req.getMotto());
		if (req.getPhotoUrl() != null)     usr.setPhotoUrl(req.getPhotoUrl());
		if (req.getCardStyle() != null)    usr.setCardStyle(req.getCardStyle());
		if (req.getWantedLevel() != null)  usr.setWantedLevel(req.getWantedLevel());
		if (req.getWantedText() != null)   usr.setWantedText(req.getWantedText());
		if (req.getLevel() != null)        usr.setLevel(req.getLevel());
		if (req.getXpPercent() != null)    usr.setXpPercent(req.getXpPercent());
		if (req.getStatDev() != null)      usr.setStatDev(req.getStatDev());
		if (req.getStatDesign() != null)   usr.setStatDesign(req.getStatDesign());
		if (req.getStatBrain() != null)    usr.setStatBrain(req.getStatBrain());
		if (req.getStatSocial() != null)   usr.setStatSocial(req.getStatSocial());
		if (req.getStatGrind() != null)    usr.setStatGrind(req.getStatGrind());
		if (req.getTraits() != null)       usr.setTraits(req.getTraits());

		usr.setUpdatedAt(LocalDateTime.now());
		GameUserRepo.save(usr);
		return toResponse(usr);
	}

	@Transactional
	public PlayerCardResponse updatePhotoUrl(UUID id, String photoUrl) {
		GameUser usr = GameUserRepo.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
		usr.setPhotoUrl(photoUrl);
		usr.setUpdatedAt(LocalDateTime.now());
		GameUserRepo.save(usr);
		return toResponse(usr);
	}

	@Transactional
	public Map<String, Object> submitScore(UUID id, int wpm, int accuracy) {
		GameUser usr = GameUserRepo.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
		if (wpm > (usr.getBestWpm() == null ? 0 : usr.getBestWpm())) {
			usr.setBestWpm(wpm);
		}
		if (accuracy > (usr.getBestAccuracy() == null ? 0 : usr.getBestAccuracy())) {
			usr.setBestAccuracy(accuracy);
		}
		// level up based on wpm milestone
		int newLevel = Math.max(1, wpm / 20);
		if (newLevel > (usr.getLevel() == null ? 1 : usr.getLevel())) {
			usr.setLevel(newLevel);
		}
		usr.setUpdatedAt(LocalDateTime.now());
		GameUserRepo.save(usr);
		return java.util.Map.of("bestWpm", usr.getBestWpm(), "bestAccuracy", usr.getBestAccuracy(), "level", usr.getLevel());
	}

	public List<Map<String, Object>> getLeaderboard() {
		List<GameUser> users = GameUserRepo.findTop100ByOrderByCreatedAtAsc();
		List<Map<String, Object>> result = new java.util.ArrayList<>();
		for (int i = 0; i < users.size(); i++) {
			GameUser u = users.get(i);
			Map<String, Object> entry = new java.util.LinkedHashMap<>();
			entry.put("rank", i + 1);
			entry.put("id", u.getId());
			entry.put("firstName", u.getFirstName());
			entry.put("lastName", u.getLastName());
			entry.put("classRole", u.getClassRole());
			entry.put("level", u.getLevel());
			entry.put("createdAt", u.getCreatedAt());
			result.add(entry);
		}
		return result;
	}
}
