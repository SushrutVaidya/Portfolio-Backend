package com.sushrut.portfolio.backend.it;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-stack integration tests for the top-risk endpoints.
 *
 * Testcontainers spins up a real Postgres 16 for the duration of the test
 * class, Flyway runs V1+V2 against it, and Spring Boot boots on a random
 * port. Then we hit HTTP endpoints for real to prove the whole request
 * path works: filter chain → controller → service → JPA → Postgres.
 *
 * Redis is left off. `management.health.redis.enabled=false` in the
 * base config keeps the health endpoint green, and RickrollService
 * falls back to in-memory on any Redis error — so tests don't need a
 * Redis container.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DevQuestApiIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.4-alpine")
        .withDatabaseName("portfolio")
        .withUsername("portfolio_app")
        .withPassword("test_password");

    @DynamicPropertySource
    static void jdbcProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("DB_PASSWORD",                POSTGRES::getPassword);       // property placeholder in app.properties
        // Force Flyway to run migrations on the empty container.
        r.add("spring.flyway.baseline-on-migrate", () -> "false");
        r.add("spring.jpa.hibernate.ddl-auto",     () -> "validate");
        // Redis off — RickrollService falls back to in-memory.
        r.add("spring.data.redis.host",     () -> "127.0.0.1");
        r.add("spring.data.redis.port",     () -> "1");     // guaranteed refused
        // A strong, non-default HMAC secret so AuthTokenService starts.
        r.add("auth.hmac-secret",           () -> "integration-test-hmac-secret-xyz");
    }

    @LocalServerPort private int port;
    private RestTemplate http;

    @BeforeEach
    void setUp() {
        // Realistic timeouts — a hung backend under test should fail the
        // test with a clear message rather than hanging the CI job.
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(2).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(10).toMillis());
        http = new RestTemplate(factory);
    }

    private String url(String path) { return "http://localhost:" + port + path; }

    // ─── register + token lifecycle ────────────────────────────────

    @Test
    void register_returnsIdAndToken() {
        Map<String, Object> body = post("/api/user",
            Map.of("firstName", "Alice", "lastName", "Wonderland"),
            null, HttpStatus.OK);

        assertThat(body).containsKeys("id", "firstName", "lastName", "token");
        assertThat((String) body.get("firstName")).isEqualTo("Alice");
        assertThat((String) body.get("token")).isNotBlank();
    }

    @Test
    void register_deterministic_sameNameSameUserSameToken() {
        Map<String, Object> first  = post("/api/user", Map.of("firstName", "Bob", "lastName", "Builder"), null, HttpStatus.OK);
        Map<String, Object> second = post("/api/user", Map.of("firstName", "bob", "lastName", "BUILDER"), null, HttpStatus.OK);
        assertThat(first.get("id")).isEqualTo(second.get("id"));
        assertThat(first.get("token")).isEqualTo(second.get("token"));
    }

    @Test
    void register_rejectsShortName() {
        post("/api/user", Map.of("firstName", "X", "lastName", "Xx"), null, HttpStatus.BAD_REQUEST);
    }

    // ─── auth-guarded endpoints ────────────────────────────────────

    @Test
    void updateCard_401WithoutToken() {
        Map<String, Object> reg = post("/api/user", Map.of("firstName", "Carol", "lastName", "Danvers"), null, HttpStatus.OK);
        Object id = reg.get("id");
        HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON);
        try {
            http.exchange(url("/api/user/" + id + "/card"), HttpMethod.PUT,
                new HttpEntity<>(Map.of("bio", "hi"), h), Map.class);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            return;
        }
        throw new AssertionError("expected 401");
    }

    @Test
    void updateCard_worksWithToken() {
        Map<String, Object> reg = post("/api/user", Map.of("firstName", "Dave", "lastName", "Grohl"), null, HttpStatus.OK);
        String token = (String) reg.get("token");
        Object id    = reg.get("id");
        Map<String, Object> body = Map.of("bio", "Sound City forever", "statDev", 88);
        Map<String, Object> updated = put("/api/user/" + id + "/card", body, token, HttpStatus.OK);
        assertThat(updated.get("bio")).isEqualTo("Sound City forever");
        assertThat(((Map<?,?>) updated.get("stats")).get("DEV")).isEqualTo(88);
    }

    @Test
    void score_401WithoutToken() {
        Map<String, Object> reg = post("/api/user", Map.of("firstName", "Eva", "lastName", "Green"), null, HttpStatus.OK);
        Object id = reg.get("id");
        Map<String, Object> score = Map.of("userId", id.toString(), "wpm", 100, "accuracy", 95);
        post("/api/score", score, null, HttpStatus.UNAUTHORIZED);
    }

    // ─── leaderboard is bounded ────────────────────────────────────

    @Test
    void leaderboard_returnsListShape() {
        post("/api/user", Map.of("firstName", "Frank", "lastName", "Ocean"), null, HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) http
            .getForObject(url("/api/leaderboard"), List.class);
        assertThat(rows).isNotEmpty();
        assertThat(rows.get(0)).containsKeys("rank", "id", "firstName", "lastName", "level");
    }

    // ─── print request validation ──────────────────────────────────

    @Test
    void printRequest_rejectsBadPincode() {
        Map<String, String> body = new HashMap<>();
        body.put("fullName", "Test Person");
        body.put("addressLine1", "Somewhere");
        body.put("city", "Hyderabad");
        body.put("pincode", "5A0001");   // non-digits
        body.put("phone", "9876543210");
        post("/api/print-request", body, null, HttpStatus.BAD_REQUEST);
    }

    @Test
    void printRequest_rejectsUnsupportedCity() {
        Map<String, String> body = new HashMap<>();
        body.put("fullName", "Test Person");
        body.put("addressLine1", "Anywhere Rd");
        body.put("city", "Mumbai");
        body.put("pincode", "400001");
        body.put("phone", "9876543210");
        post("/api/print-request", body, null, HttpStatus.BAD_REQUEST);
    }

    @Test
    void printRequestCount_isReachable() {
        @SuppressWarnings("unchecked")
        Map<String, Object> resp = http.getForObject(url("/api/print-request/count"), Map.class);
        assertThat(resp).containsKeys("claimed", "remaining", "full");
    }

    // ─── health surface locked down ────────────────────────────────

    @Test
    void actuatorHealth_returnsUpWithoutDetails() {
        @SuppressWarnings("unchecked")
        Map<String, Object> health = http.getForObject(url("/actuator/health"), Map.class);
        assertThat(health.get("status")).isEqualTo("UP");
        // With show-details=when-authorized and no auth, details must be absent
        assertThat(health).doesNotContainKeys("components");
    }

    // ─── request id echo ───────────────────────────────────────────

    @Test
    void anyResponse_stampsXRequestIdHeader() {
        ResponseEntity<Map> resp = http.getForEntity(url("/api/leaderboard"), Map.class);
        assertThat(resp.getHeaders().getFirst("X-Request-Id")).matches("[A-Za-z0-9._-]{1,64}");
    }

    // ─── helpers ───────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String path, Object body, String token, HttpStatus expected) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) h.set("X-DQ-Token", token);
        try {
            ResponseEntity<Map> resp = http.exchange(url(path), HttpMethod.POST,
                new HttpEntity<>(body, h), Map.class);
            assertThat(resp.getStatusCode()).isEqualTo(expected);
            return resp.getBody();
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            assertThat(e.getStatusCode()).isEqualTo(expected);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> put(String path, Object body, String token, HttpStatus expected) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) h.set("X-DQ-Token", token);
        ResponseEntity<Map> resp = http.exchange(url(path), HttpMethod.PUT,
            new HttpEntity<>(body, h), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(expected);
        return resp.getBody();
    }
}
