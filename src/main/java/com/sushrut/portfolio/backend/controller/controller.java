package com.sushrut.portfolio.backend.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sushrut.portfolio.backend.model.StatsResponse;
import com.sushrut.portfolio.backend.service.StatsService;
import com.sushrut.portfolio.backend.service.RickrollService;

@RestController
@RequestMapping("/api")
public class controller {
	@Autowired
	private StatsService ss;

	@Autowired
	private RickrollService rickrollService;

	@GetMapping("/stats")
	public StatsResponse getStats() {
		return ss.getStats();
	}

	@GetMapping("/rickroll")
	public Map<String, Integer> incrementRickroll() {
		int count = rickrollService.incrementAndGet();
		return Map.of("count", count);
	}

	@GetMapping("/rickroll/count")
	public Map<String, Integer> getRickrollCount() {
		return Map.of("count", rickrollService.getCount());
	}

}
