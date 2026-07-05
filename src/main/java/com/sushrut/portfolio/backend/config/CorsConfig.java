package com.sushrut.portfolio.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * CORS + static-resource wiring.
 *
 * Allowed origins are the actual production domain + local dev servers.
 * `allowCredentials(false)` is explicit — we don't use cookie auth (the
 * HMAC X-DQ-Token header is not a credential in the CORS sense), and
 * this keeps the browser from imposing the "no wildcard with credentials"
 * rule.
 *
 * `allowedHeaders` is a fixed list — including X-DQ-Token so the
 * preflight succeeds. Wildcard would work but explicit is auditable.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

	@Value("${portfolio.uploads.dir:./uploads}")
	private String uploadsDir;

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/**")
				.allowedOrigins(
					"http://localhost:8080", "http://127.0.0.1:8080",
					"http://localhost:8888", "http://127.0.0.1:8888",   // local e2e nginx-proxy
					"https://sushrutvaidya.in", "https://www.sushrutvaidya.in"
				)
				.allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
				.allowedHeaders("Content-Type", "Accept", "Origin", "X-Requested-With", "X-DQ-Token", "X-Request-Id")
				.exposedHeaders("X-Request-Id")
				.allowCredentials(false)
				.maxAge(3600);
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		String location = "file:" + Paths.get(uploadsDir).toAbsolutePath().toString() + "/";
		registry.addResourceHandler("/uploads/**")
				.addResourceLocations(location);
	}
}

