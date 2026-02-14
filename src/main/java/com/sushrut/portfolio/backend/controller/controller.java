package com.sushrut.portfolio.backend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sushrut.portfolio.backend.model.StatsResponse;
import com.sushrut.portfolio.backend.service.StatsService;

@RestController
@RequestMapping("/api")
public class controller {
	@Autowired
	private StatsService ss;

	@GetMapping("/stats")
	public StatsResponse getStats() {
		return ss.getStats();
	}

}
