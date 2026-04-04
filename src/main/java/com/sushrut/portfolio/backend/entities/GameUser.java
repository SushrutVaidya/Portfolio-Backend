package com.sushrut.portfolio.backend.entities;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
//import jakarta.annotation.Generated;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

@Data
@Entity
public class GameUser {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;
	@Column(name = "first_name", nullable = false, length = 24)
	private String firstName;
	@Column(name = "last_name", nullable = false, length = 24)
	private String lastName;
	@Column(name = "created_at")
	private LocalDateTime createdAt = LocalDateTime.now();

}
