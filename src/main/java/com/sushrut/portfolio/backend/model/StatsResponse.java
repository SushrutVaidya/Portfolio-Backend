package com.sushrut.portfolio.backend.model;

//import jakarta.persistence.Entity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

//@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatsResponse {
	private String location;
	private String game;
	private String currentTime;
	private long timeStamp;
	private String songName;
	private String songURL;

}
