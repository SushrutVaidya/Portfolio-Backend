package com.sushrut.portfolio.backend.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Weak-signature auth: HMAC-SHA256(userId + server-secret) → token.
 *
 * Token is deterministic — same user always gets the same token unless the
 * server secret is rotated. Stateless, zero session storage, zero Redis
 * dependency. Fits OCI Always-Free footprint (JDK built-ins only).
 *
 * Threat model: blocks casual abuse where someone reads a UUID off the
 * leaderboard and tries to overwrite that user's card. Does NOT protect
 * against someone who inspects their own localStorage and hands their
 * token to a friend — that's fine for a portfolio project.
 */
@Service
public class AuthTokenService {

    private static final Logger log = LoggerFactory.getLogger(AuthTokenService.class);
    private static final String HEADER = "X-DQ-Token";
    private static final String ALGO   = "HmacSHA256";

    // MUST match the default in application.properties. If this string ever
    // slips through into a running prod container, everyone can forge tokens
    // because the secret is now in source. Startup check below rejects it
    // outside the `local` Spring profile.
    private static final String DEV_DEFAULT_SECRET =
        "local-dev-secret-do-not-use-in-prod-please";

    @Value("${auth.hmac-secret}")
    private String secret;

    private final Environment env;

    public AuthTokenService(Environment env) {
        this.env = env;
    }

    @PostConstruct
    void validateSecretAtStartup() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                "auth.hmac-secret is empty. Set AUTH_HMAC_SECRET env var " +
                "(openssl rand -base64 48) or run with --spring.profiles.active=local.");
        }
        boolean isLocal = false;
        for (String p : env.getActiveProfiles()) {
            if ("local".equalsIgnoreCase(p)) { isLocal = true; break; }
        }
        if (!isLocal && DEV_DEFAULT_SECRET.equals(secret)) {
            // Fail hard — do not start the app with the shipped dev secret in prod.
            throw new IllegalStateException(
                "Refusing to start: auth.hmac-secret is the built-in dev default. " +
                "Set AUTH_HMAC_SECRET in the environment (openssl rand -base64 48) " +
                "before deploying, or activate the 'local' profile for local testing.");
        }
        if (isLocal && DEV_DEFAULT_SECRET.equals(secret)) {
            log.warn("[auth] Running with dev-default HMAC secret — LOCAL PROFILE ONLY. " +
                     "Do not use this secret in production.");
        }
    }

    /** Mint a token for a user. Deterministic given the secret. */
    public String generateToken(UUID userId) {
        try {
            Mac mac = Mac.getInstance(ALGO);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGO));
            byte[] hmac = mac.doFinal(userId.toString().getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hmac);
        } catch (Exception e) {
            // Only fires if the JDK doesn't have HmacSHA256 (never in practice)
            throw new IllegalStateException("HMAC generation failed", e);
        }
    }

    /** Constant-time compare. Prevents timing side-channel on token check. */
    public boolean validate(UUID userId, String presented) {
        if (presented == null || presented.isBlank()) return false;
        String expected = generateToken(userId);
        return MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            presented.getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * Throws 401 unless the request's X-DQ-Token header matches HMAC(userId).
     * Call at the top of every mutating endpoint that takes a user's UUID.
     */
    public void assertOwns(UUID userId, HttpServletRequest req) {
        String presented = req.getHeader(HEADER);
        if (!validate(userId, presented)) {
            log.warn("Auth rejected for userId={} — token {}",
                userId, presented == null ? "absent" : "invalid");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "Missing or invalid X-DQ-Token header");
        }
    }
}
