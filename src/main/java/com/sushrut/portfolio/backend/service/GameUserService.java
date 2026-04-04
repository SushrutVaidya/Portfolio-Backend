package com.sushrut.portfolio.backend.service;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.sushrut.portfolio.backend.entities.GameUser;
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
}