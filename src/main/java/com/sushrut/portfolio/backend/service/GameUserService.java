package com.sushrut.portfolio.backend.service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.sushrut.portfolio.backend.entities.GameUser;
import com.sushrut.portfolio.backend.model.PlayerCardRequest;
import com.sushrut.portfolio.backend.model.PlayerCardResponse;
import com.sushrut.portfolio.backend.service.impl.GameUserRepository;

@Service
public class GameUserService {
	@Autowired
	private GameUserRepository GameUserRepo;

	public GameUser registerOrGet(String firstName, String lastName) {
		Optional<GameUser> existing = GameUserRepo.findByFirstNameAndLastName(firstName, lastName);
		if (existing.isPresent()) {
			return existing.get();
		}
		GameUser user = new GameUser();
		user.setFirstName(firstName);
		user.setLastName(lastName);
		return GameUserRepo.save(user);

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

		// Stats that we will store in a MAP
		Map<String, Integer> stats = new LinkedHashMap<>();
		stats.put("DEV", usr.getStatDev());
		stats.put("DESIGN", usr.getStatDesign());
		stats.put("BRAIN", usr.getStatBrain());
		stats.put("SOCIAL", usr.getStatSocial());
		stats.put("GRIND", usr.getStatGrind());
		res.setStats(stats);

		// Traits → List
		if (usr.getTraits() != null && !usr.getTraits().isBlank()) {
			res.setTraits(Arrays.asList(usr.getTraits().split(",")));
		} else {
			res.setTraits(List.of());
		}

		return res;
	}

	public PlayerCardResponse getCard(UUID id) {
		GameUser usr = GameUserRepo.findById(id)
				.orElseThrow(() -> new RuntimeException("BuddyBoy your user is not registered"));
		return toResponse(usr);
	}

	public PlayerCardResponse updateCard(UUID id, PlayerCardRequest req) {
		GameUser usr = GameUserRepo.findById(id)
				.orElseThrow(() -> new RuntimeException("BuddyBoy your user is not registered"));
		if (req.getClassRole() != null)
			usr.setClassRole(req.getClassRole());
		if (req.getBio() != null)
			usr.setBio(req.getBio());
		if (req.getMotto() != null)
			usr.setMotto(req.getMotto());
		if (req.getPhotoUrl() != null)
			usr.setPhotoUrl(req.getPhotoUrl());
		if (req.getCardStyle() != null)
			usr.setCardStyle(req.getCardStyle());
		if (req.getWantedLevel() != null)
			usr.setWantedLevel(req.getWantedLevel());
		if (req.getWantedText() != null)
			usr.setWantedText(req.getWantedText());
		if (req.getLevel() != null)
			usr.setLevel(req.getLevel());
		if (req.getXpPercent() != null)
			usr.setXpPercent(req.getXpPercent());
		if (req.getStatDev() != null)
			usr.setStatDev(req.getStatDev());
		if (req.getStatDesign() != null)
			usr.setStatDesign(req.getStatDesign());
		if (req.getStatBrain() != null)
			usr.setStatBrain(req.getStatBrain());
		if (req.getStatSocial() != null)
			usr.setStatSocial(req.getStatSocial());
		if (req.getStatGrind() != null)
			usr.setStatGrind(req.getStatGrind());
		if (req.getTraits() != null)
			usr.setTraits(req.getTraits());

		GameUserRepo.save(usr);
		return toResponse(usr);

	}

}
