package com.sushrut.portfolio.backend.model;

//import java.util.List;  

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SteamGameResponse {
	private String title;
	private int xp;
	private String played;
	private int appid;

}
