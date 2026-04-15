package com.sushrut.portfolio.backend.model;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlayerCardResponse {

	private UUID id;
	private String firstName;
	private String lastName;
	private String classRole;
	private String bio;
	private String motto;
	private String photoUrl;
	private String cardStyle;
	private Integer wantedLevel;
	private String wantedText;
	private Integer level;
	private Integer xpPercent;
	private Map<String, Integer> stats;
	private List<String> traits;
	private Boolean profileComplete;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
}
