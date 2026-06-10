package com.sushrut.portfolio.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/**")
				.allowedOrigins(
					"http://localhost:5500", "http://127.0.0.1:5500",
					"http://localhost:3000", "http://127.0.0.1:3000",
					"http://localhost:8080", "http://127.0.0.1:8080",
					"http://localhost:8081", "http://127.0.0.1:8081",
					"https://sushrutvaidya.in", "https://www.sushrutvaidya.in"
				)
				.allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
				.allowedHeaders("*");
	}

}
