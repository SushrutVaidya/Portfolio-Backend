package com.sushrut.portfolio.backend.service.impl;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.sushrut.portfolio.backend.entities.GameUser;

@Repository
public interface GameUserRepository extends JpaRepository<GameUser, UUID> {
	Optional<GameUser> findByFirstNameAndLastName(String firstName, String lastName);

}
