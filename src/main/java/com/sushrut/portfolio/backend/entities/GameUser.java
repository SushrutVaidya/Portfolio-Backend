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
	@Column(name = "class_role", length = 50)
	private String classRole;

	@Column(length = 280)
	private String bio;

	@Column(length = 120)
	private String motto;

	@Column(name = "photo_url", length = 500)
	private String photoUrl;

	@Column(name = "card_style", length = 20)
	private String cardStyle = "minimal";

	@Column(name = "wanted_level")
	private Integer wantedLevel = 1;

	@Column(name = "wanted_text", length = 100)
	private String wantedText;

	@Column
	private Integer level = 1;

	@Column(name = "xp_percent")
	private Integer xpPercent = 0;

	@Column(name = "stat_dev")
	private Integer statDev = 50;

	@Column(name = "stat_design")
	private Integer statDesign = 50;

	@Column(name = "stat_brain")
	private Integer statBrain = 50;

	@Column(name = "stat_social")
	private Integer statSocial = 50;

	@Column(name = "stat_grind")
	private Integer statGrind = 50;

	@Column(length = 200)
	private String traits;

	@Column(name = "profile_complete")
	private Boolean profileComplete = false;

	@Column(name = "updated_at")
	private LocalDateTime updatedAt;
}
