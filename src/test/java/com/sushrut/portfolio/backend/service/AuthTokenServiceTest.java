package com.sushrut.portfolio.backend.service;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the HMAC weak-signature auth service.
 *
 * Covers the properties we actually depend on downstream:
 *   1. Token generation is deterministic given the same secret + userId
 *   2. Different userIds produce different tokens
 *   3. validate() is a constant-time compare and returns the expected boolean
 *   4. assertOwns() throws 401 for missing/invalid tokens
 *   5. Startup fails fast if the dev-default secret is present in a
 *      non-local profile — this is the "you forgot AUTH_HMAC_SECRET on OCI"
 *      guard, so it MUST have a test.
 */
@ExtendWith(MockitoExtension.class)
class AuthTokenServiceTest {

    private static final String REAL_SECRET =
        "s3cret-for-test-only-do-not-use-in-prod-please";
    private static final String DEV_DEFAULT =
        "local-dev-secret-do-not-use-in-prod-please";

    @Mock private Environment env;
    @InjectMocks private AuthTokenService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "secret", REAL_SECRET);
    }

    @Test
    void tokensAreDeterministic() {
        UUID u = UUID.randomUUID();
        assertThat(service.generateToken(u))
            .isEqualTo(service.generateToken(u))
            .isNotBlank();
    }

    @Test
    void differentUsersGetDifferentTokens() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertThat(service.generateToken(a)).isNotEqualTo(service.generateToken(b));
    }

    @Test
    void validateAcceptsMatchingToken() {
        UUID u = UUID.randomUUID();
        String token = service.generateToken(u);
        assertThat(service.validate(u, token)).isTrue();
    }

    @Test
    void validateRejectsWrongToken() {
        UUID u = UUID.randomUUID();
        assertThat(service.validate(u, "not-the-real-token")).isFalse();
    }

    @Test
    void validateRejectsNullAndBlank() {
        UUID u = UUID.randomUUID();
        assertThat(service.validate(u, null)).isFalse();
        assertThat(service.validate(u, "")).isFalse();
        assertThat(service.validate(u, "   ")).isFalse();
    }

    @Test
    void validateRejectsTokenForDifferentUser() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertThat(service.validate(b, service.generateToken(a))).isFalse();
    }

    @Test
    void assertOwnsThrows401WhenHeaderAbsent() {
        UUID u = UUID.randomUUID();
        HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
        Mockito.when(req.getHeader("X-DQ-Token")).thenReturn(null);
        assertThatThrownBy(() -> service.assertOwns(u, req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("401");
    }

    @Test
    void assertOwnsPassesWithMatchingHeader() {
        UUID u = UUID.randomUUID();
        HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
        Mockito.when(req.getHeader("X-DQ-Token")).thenReturn(service.generateToken(u));
        assertThatCode(() -> service.assertOwns(u, req)).doesNotThrowAnyException();
    }

    // ── startup fail-fast ──────────────────────────────────────────────

    @Test
    void startupFailsIfDevSecretInProdProfile() {
        AuthTokenService svc = new AuthTokenService(env);
        ReflectionTestUtils.setField(svc, "secret", DEV_DEFAULT);
        Mockito.when(env.getActiveProfiles()).thenReturn(new String[] { "prod" });
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(svc, "validateSecretAtStartup"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("dev default");
    }

    @Test
    void startupAcceptsDevSecretUnderLocalProfile() {
        AuthTokenService svc = new AuthTokenService(env);
        ReflectionTestUtils.setField(svc, "secret", DEV_DEFAULT);
        Mockito.when(env.getActiveProfiles()).thenReturn(new String[] { "local" });
        assertThatCode(() -> ReflectionTestUtils.invokeMethod(svc, "validateSecretAtStartup"))
            .doesNotThrowAnyException();
    }

    @Test
    void startupRejectsBlankSecret() {
        AuthTokenService svc = new AuthTokenService(env);
        ReflectionTestUtils.setField(svc, "secret", "");
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(svc, "validateSecretAtStartup"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("empty");
    }
}
