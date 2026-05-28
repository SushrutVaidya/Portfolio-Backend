package com.sushrut.portfolio.backend.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PlayerCardRequest {

	@Size(max = 50)
	private String classRole;
	@Size(max = 280)
	private String bio;
	@Size(max = 120)
	private String motto;
	@Size(max = 500)
	private String photoUrl;
	@Size(max = 20)
	private String cardStyle;
	@Min(1)
	@Max(5)
	private Integer wantedLevel;
	@Size(max = 100)
	private String wantedText;
	private Integer level;
	@Min(0)
	@Max(100)
	private Integer xpPercent;
	@Min(0)
	@Max(100)
	private Integer statDev;
	@Min(0)
	@Max(100)
	private Integer statDesign;
	@Min(0)
	@Max(100)
	private Integer statBrain;
	@Min(0)
	@Max(100)
	private Integer statSocial;
	@Min(0)
	@Max(100)
	private Integer statGrind;
	@Size(max = 200)
	private String traits;

}
