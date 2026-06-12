package com.sushrut.portfolio.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

	@Value("${portfolio.uploads.dir:./uploads}")
	private String uploadsDir;

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/**")
				.allowedOrigins(
					"http://localhost:8080", "http://127.0.0.1:8080",
					"https://sushrutvaidya.in", "https://www.sushrutvaidya.in"
				)
				.allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
				.allowedHeaders("*");
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		String location = "file:" + Paths.get(uploadsDir).toAbsolutePath().toString() + "/";
		registry.addResourceHandler("/uploads/**")
				.addResourceLocations(location);
	}
}
