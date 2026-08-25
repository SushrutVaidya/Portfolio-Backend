package com.sushrut.portfolio.backend.service.impl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.sushrut.portfolio.backend.entities.GameUser;

@Repository
public interface GameUserRepository extends JpaRepository<GameUser, UUID> {
	Optional<GameUser> findByFirstNameAndLastName(String firstName, String lastName);

	// Leaderboard — capped to prevent unbounded enumeration and bandwidth.
	// If the site grows past 100 registered players, add pagination here
	// (Pageable + offset/limit) rather than raising this number.
	List<GameUser> findTop100ByOrderByCreatedAtAsc();
}
