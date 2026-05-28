# Portfolio Backend — Interview Concepts & Learnings

> Auto-updated every 5 commits. Last updated: commit `40486cd`

---

## Table of Contents

1. [Spring Boot Architecture](#1-spring-boot-architecture)
2. [REST API Design](#2-rest-api-design)
3. [Redis & Caching](#3-redis--caching)
4. [Resilience & Fallback Patterns](#4-resilience--fallback-patterns)
5. [Configuration Management](#5-configuration-management)
6. [Lombok — Reducing Boilerplate](#6-lombok--reducing-boilerplate)
7. [CORS — Cross-Origin Resource Sharing](#7-cors--cross-origin-resource-sharing)
8. [Spring Boot Actuator](#8-spring-boot-actuator)
9. [Concurrency — AtomicInteger](#9-concurrency--atomicinteger)
10. [Deployment — Docker + EC2 + Upstash](#10-deployment--docker--ec2--upstash)
11. [JPA & Entity Design](#11-jpa--entity-design)
12. [DTO Pattern & Validation](#12-dto-pattern--validation)
13. [Partial Updates (PATCH-style PUT)](#13-partial-updates-patch-style-put)
14. [Quick Interview Cheat Sheet](#quick-interview-cheat-sheet)

---

## 1. Spring Boot Architecture

### Layered Architecture
```
Controller → Service → Repository/External (Redis)
```
Each layer has a single responsibility:
- **Controller** — HTTP request/response handling, routing
- **Service** — business logic
- **Repository/Config** — data access, external integrations

### `@SpringBootApplication`
```java
@SpringBootApplication // = @Configuration + @ComponentScan + @EnableAutoConfiguration
public class PortfolioBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(PortfolioBackendApplication.class, args);
    }
}
```
> **Interview angle:** `@EnableAutoConfiguration` is the magic — Spring scans the classpath and automatically configures beans. If `spring-data-redis` is on the classpath, Redis beans are auto-configured. You can override them with your own `@Bean`.

### Dependency Injection with `@Autowired`
```java
@RestController
public class Controller {
    @Autowired
    private StatsService ss;       // Spring injects the bean

    @Autowired
    private RickrollService rickrollService;
}
```
> **Interview angle:** Spring maintains an IoC (Inversion of Control) container. Instead of `new StatsService()`, you declare the dependency and Spring provides it. Benefits: testability (mock the dependency), loose coupling, single instance (singleton by default).

**Constructor injection vs field injection**
```java
// Field injection (what we use — convenient but harder to test)
@Autowired private StatsService ss;

// Constructor injection (preferred in production — dependencies explicit)
public Controller(StatsService ss, RickrollService rickrollService) {
    this.ss = ss;
    this.rickrollService = rickrollService;
}
```
> Constructor injection is preferred because dependencies are explicit, the class can't be instantiated without them, and it's easier to write unit tests without a Spring context.

---

## 2. REST API Design

### `@RestController` + `@RequestMapping`
```java
@RestController          // @Controller + @ResponseBody — every method returns JSON by default
@RequestMapping("/api")  // base path for all endpoints in this class
public class Controller {

    @GetMapping("/stats")           // GET /api/stats
    @GetMapping("/rickroll")        // GET /api/rickroll
    @GetMapping("/rickroll/count")  // GET /api/rickroll/count
    @GetMapping("/redis/test")      // GET /api/redis/test
}
```

### `ResponseEntity` — full control over HTTP response
```java
// Return 200 with body
return ResponseEntity.ok(Map.of("status", "success"));

// Return 500 with error body
return ResponseEntity.status(500).body(Map.of(
    "status", "error",
    "message", "Redis connection failed: " + e.getMessage()
));
```
> **Interview angle:** `ResponseEntity<T>` lets you control the HTTP status code, headers, and body independently. Without it, `@RestController` always returns 200. Use it whenever you need 4xx/5xx responses or custom headers.

### `Map.of()` for quick JSON responses
```java
return Map.of("count", count);
// serialises to: { "count": 42 }
```
> `Map.of()` creates an immutable map (Java 9+). Spring's Jackson serialiser converts it to JSON automatically.

### Idiomatic endpoint naming
```
GET /api/rickroll         → action (increment AND return)
GET /api/rickroll/count   → read-only (no side effect)
GET /api/stats            → resource
GET /api/redis/test       → utility/debug endpoint
```
> **Interview angle:** REST convention: GET should be idempotent (safe to call multiple times with same result). Our `/api/rickroll` breaks this — it's a GET that mutates state. In strict REST it should be `POST /api/rickroll`. Acceptable tradeoff for a simple portfolio project.

---

## 3. Redis & Caching

### Why Redis?
- **In-memory** — microsecond reads/writes vs milliseconds for SQL
- **Persistent** — data survives application restarts (unlike a Java variable)
- **Distributed** — shared across multiple app instances (horizontal scaling)
- **Atomic operations** — `INCR` is thread-safe without locks

### `RedisTemplate<K, V>` — Spring's Redis abstraction
```java
// Set a value
redisTemplate.opsForValue().set("test:key", "test:value");

// Get a value
String value = redisTemplate.opsForValue().get("test:key");

// Atomic increment (returns new value)
Long count = redisTemplate.opsForValue().increment("rickroll:count");
```
> **Interview angle:** `increment()` maps to Redis `INCR` command — it's atomic at the Redis server level. No race condition even if 1000 requests hit simultaneously. This is the key advantage over `count++` in Java (which would need synchronisation).

### Redis serialisers
```java
template.setKeySerializer(new StringRedisSerializer());
template.setValueSerializer(new StringRedisSerializer());
```
> By default, Spring uses Java serialisation (binary). `StringRedisSerializer` stores plain text — human-readable in Redis CLI, portable across languages.

### SSL for Upstash (cloud Redis)
```java
if (redisSslEnabled) {
    clientConfig.useSsl().disablePeerVerification();
}
```
> `disablePeerVerification()` skips SSL certificate hostname check — necessary for Upstash's dynamic IPs. In a high-security context you'd validate certificates.

### Lettuce vs Jedis
We use **Lettuce** (Spring's default Redis client):
- Non-blocking, async I/O
- Single connection shared across threads (Jedis uses a connection pool)
- Better for reactive Spring WebFlux

### Upstash — serverless Redis
```properties
spring.data.redis.host=${REDIS_HOST:localhost}
spring.data.redis.port=${REDIS_PORT:6379}
spring.data.redis.password=${REDIS_PASSWORD:}
spring.data.redis.ssl.enabled=${REDIS_SSL:false}
```
> The `:defaultValue` syntax in `${ENV:default}` means "use the env var if set, otherwise use this default". Allows local dev without env vars while production uses real credentials.

---

## 4. Resilience & Fallback Patterns

### Circuit breaker pattern (manual implementation)
```java
private boolean redisAvailable = true;

public int incrementAndGet() {
    if (redisAvailable) {
        try {
            Long count = redisTemplate.opsForValue().increment(RICKROLL_KEY);
            return count.intValue();
        } catch (Exception e) {
            logger.warn("Redis unavailable, falling back: {}", e.getMessage());
            redisAvailable = false; // stop trying Redis on every call
        }
    }
    return fallbackCounter.incrementAndGet(); // in-memory fallback
}
```
> **Interview angle:** This is a simplified circuit breaker — once Redis fails, the flag stays false and we skip Redis on every subsequent call. A proper circuit breaker (Resilience4j) also has a "half-open" state to probe recovery. For a portfolio, this is perfectly sufficient.

### Why `AtomicInteger` for fallback?
```java
private final AtomicInteger fallbackCounter = new AtomicInteger(0);
```
> `int count++` is NOT thread-safe — read-increment-write is three operations, another thread can interleave. `AtomicInteger.incrementAndGet()` is a single atomic CPU instruction (CAS — Compare And Swap). No synchronisation block needed.

### Graceful degradation principle
> **Interview answer:** "Design systems to degrade gracefully, not fail completely." Our counter keeps working (in-memory) even when Redis is down. Users see a counter (possibly reset), not an error. The application stays alive.

### Logging with SLF4J
```java
private static final Logger logger = LoggerFactory.getLogger(RickrollService.class);

logger.info("Rickroll count incremented to: {} (using Redis)", count);
logger.warn("Redis unavailable, falling back: {}", e.getMessage());
```
> `{}` is SLF4J's placeholder — lazy string interpolation. The string is only constructed if the log level is enabled. Faster than `"count: " + count` (which always allocates a String).

---

## 5. Configuration Management

### `@Value` for injecting properties
```java
@Value("${portfolio.location}")
private String location;

@Value("${spring.data.redis.host:localhost}")
private String redisHost; // with default
```

### Externalising config with `application.properties`
```properties
# Hardcoded values
portfolio.location=Hyderabad
portfolio.game=Counter Strike 2

# From environment variables (with fallbacks)
spring.data.redis.host=${REDIS_HOST:localhost}
spring.data.redis.password=${REDIS_PASSWORD:}
```
> **Interview angle:** The 12-Factor App methodology says "store config in the environment". Secrets (passwords, API keys) should NEVER be hardcoded — inject via env vars. Hardcoded values like `portfolio.location` are fine since they're not sensitive.

### Environment variable injection (Docker)
```bash
docker run -p 8081:8081 \
  -e REDIS_HOST=fancy-serval.upstash.io \
  -e REDIS_PORT=6379 \
  -e REDIS_PASSWORD=secret \
  -e REDIS_SSL=true \
  portfolio-backend
```

### Disabling Redis health check
```properties
management.health.redis.enabled=false
```
> Without this, Spring Actuator's `/actuator/health` returns `DOWN` when Redis is unreachable — even if the application itself is healthy. Setting to `false` means Redis failure doesn't affect the health endpoint, which is correct since we have a fallback.

---

## 6. Lombok — Reducing Boilerplate

### What Lombok generates at compile time
```java
@Data           // getters + setters + toString + equals + hashCode
@NoArgsConstructor  // default constructor
@AllArgsConstructor // constructor with all fields
public class StatsResponse {
    private String location;
    private String game;
    private String currentTime;
    private long timeStamp;
    private String songName;
    private String songURL;
    private String bookName;
}
```
> Without Lombok this would be ~80 lines. With Lombok: 15 lines. It's an annotation processor — generates code at compile time, zero runtime overhead.

> **Interview angle:** "What does `@Data` do?" — It combines `@Getter`, `@Setter`, `@ToString`, `@EqualsAndHashCode`, and `@RequiredArgsConstructor`. Know what each generates and when to use them individually (e.g., immutable objects should use `@Value` instead of `@Data`).

---

## 7. CORS — Cross-Origin Resource Sharing

### The problem
Browser security blocks JavaScript from calling APIs on a different domain/port:
```
Frontend: http://localhost:5500
Backend:  http://localhost:8081  ← different port = different origin = blocked
```

### The solution
```java
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
            .allowedOrigins(
                "http://localhost:5500",
                "http://127.0.0.1:5500",
                "http://localhost:3000",
                "http://localhost:8080"
            )
            .allowedMethods("GET", "POST", "PUT", "OPTIONS")
            .allowedHeaders("*");
    }
}
```
> **Interview angle:** CORS is enforced by the BROWSER, not the server. The server just needs to send the right `Access-Control-Allow-Origin` header. If you call the API from curl or Postman, CORS doesn't apply. **Important:** CORS config changes require a server restart — the config is loaded once at startup into Spring's handler chain, not re-read per request.

### The `OPTIONS` preflight
Browsers send an `OPTIONS` request first ("preflight") to ask: "Is my POST request allowed?". The server must handle OPTIONS and return the appropriate CORS headers — that's why `OPTIONS` is in `allowedMethods`.

---

## 8. Spring Boot Actuator

### What it does
Actuator adds production-ready monitoring endpoints automatically:
```
GET /actuator/health → { "status": "UP" }
GET /actuator/info   → app metadata
```

### Configuration
```properties
management.endpoints.web.exposure.include=health,info
management.endpoint.health.show-details=always
```
> `show-details=always` exposes disk space, Redis status, etc. In production, this should be `when-authorized` — don't expose internal system details publicly.

### Use in container orchestration
```yaml
# Docker Compose healthcheck
healthcheck:
  test: ["CMD", "curl", "-f", "http://localhost:8081/actuator/health"]
  interval: 30s
  timeout: 10s
  retries: 3
```
> **Interview angle:** "How do you know if your service is healthy in production?" Kubernetes and Docker Compose use health endpoints to decide if a container should receive traffic. Actuator provides this out of the box.

---

## 9. Concurrency — AtomicInteger

### The race condition problem
```java
// NOT thread-safe
private int count = 0;
public int increment() { return ++count; } // read → increment → write: 3 steps
```
Two threads could both read `5`, both increment to `6`, both write `6` — one increment is lost.

### AtomicInteger solution
```java
private final AtomicInteger fallbackCounter = new AtomicInteger(0);
public int increment() { return fallbackCounter.incrementAndGet(); } // single atomic op
```
> Uses CPU-level CAS (Compare-And-Swap) instruction — hardware guarantee of atomicity. No `synchronized` block, no locks, no blocking.

### When to use what
| Scenario | Solution |
|---|---|
| Simple counter, single JVM | `AtomicInteger` |
| Counter across multiple JVM instances | Redis `INCR` |
| Complex transaction (multiple ops) | `synchronized` / `ReentrantLock` |
| High-throughput counter (Java 8+) | `LongAdder` (better than AtomicInteger under contention) |

---

## 10. Deployment — Docker + EC2 + Upstash

### Dockerfile pattern for Spring Boot
```dockerfile
FROM eclipse-temurin:17-jre-alpine   # small base image
COPY target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app.jar"]
```
> **Interview angle:** Use JRE (not JDK) in production — smaller image. `alpine` variant is ~50MB vs ~200MB for full Linux. Multi-stage builds can reduce further.

### Docker Compose for local dev
```yaml
services:
  backend:
    build: ./Portfolio-Backend
    ports: ["8081:8081"]
    environment:
      REDIS_HOST: ${REDIS_HOST}
      REDIS_PASSWORD: ${REDIS_PASSWORD}
```

### Keep-alive cron for Upstash free tier
```bash
# Upstash pauses free databases after 7 days of inactivity
# Ping every 3 days to prevent this
0 0 */3 * * curl -s http://localhost:8081/api/rickroll/count > /dev/null
```
> **Interview angle:** Cloud free tiers often have "cold start" or "pause" behaviour. Always understand the constraints of free tier services you use in production.

### Time-based song rotation (no cron needed)
```java
private String getSongURL() {
    String[] songs = { "Funkadelic.mp3", "Lawrie.mp3", ... };
    int hour = LocalDateTime.now().getHour();
    int slot = hour / 4;  // 0-5 (6 slots of 4 hours)
    return "/audio/" + songs[slot];
}
```
> **Interview angle:** Instead of a scheduled job that updates a value every 4 hours, compute the result from the current time on every request. Simpler, stateless, no race conditions.

---

## 11. JPA & Entity Design

### `@Entity` — mapping class to database table
```java
@Entity
@Table(name = "game_users")
@Data @NoArgsConstructor @AllArgsConstructor
public class GameUser {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String firstName;
    private String lastName;
    private String classRole;
    private String bio;
    private String photoUrl;
    private String cardStyle;
    private Integer statDev;
    // ...
}
```
> **Interview angle:** `@Entity` tells JPA this class maps to a row in a database table. `@Id` marks the primary key. `@GeneratedValue(UUID)` lets the database generate unique IDs — no collision risk even across distributed systems.

### UUID vs auto-increment
| | UUID | Auto-increment |
|---|---|---|
| Collision risk | Effectively zero | Zero (sequential) |
| Distributed-safe | Yes — no coordination needed | No — needs a single source |
| URL guessable | No | Yes — `/user/1`, `/user/2` |
| Size | 16 bytes | 4-8 bytes |

> **Interview angle:** We use UUIDs because the ID is exposed in the API URL (`/api/user/{id}/card`). Auto-increment IDs are guessable and leak information about your user count.

### `@Column` for constraints
```java
@Column(length = 280)
private String bio;

@Column(length = 20)
private String cardStyle;
```
> Database-level length constraints. Even if validation passes in Java, the DB enforces the limit as a safety net.

---

## 12. DTO Pattern & Validation

### Why DTOs?
The entity (`GameUser`) has all database fields including internal ones (`createdAt`, database ID). You don't want the client to set those. DTOs separate what the client sends from what the database stores.

```
Client → PlayerCardRequest (DTO) → Service → GameUser (Entity) → Database
Database → GameUser (Entity) → Service → PlayerCardResponse (DTO) → Client
```

### Request DTO with Jakarta validation
```java
public class PlayerCardRequest {
    @Size(max = 50)
    private String classRole;

    @Size(max = 280)
    private String bio;

    @Min(0) @Max(100)
    private Integer statDev;

    @Min(1) @Max(5)
    private Integer wantedLevel;
}
```
> **Interview angle:** Jakarta validation annotations (`@Size`, `@Min`, `@Max`) are declarative — you define constraints and the framework enforces them. `@Valid` on the controller param triggers validation automatically. Returns 400 with details on failure.

### Response DTO — shaping the output
```java
public class PlayerCardResponse {
    private UUID id;
    private String firstName;
    private Map<String, Integer> stats;    // flattened from 5 entity fields
    private List<String> traits;           // parsed from comma-separated string
    private Boolean profileComplete;       // computed, not stored
}
```
> **Interview angle:** The response DTO transforms internal data structure into what the frontend needs. `stats` is a `LinkedHashMap` to maintain key order (DEV → DESIGN → BRAIN → SOCIAL → GRIND). `traits` splits `"Gamer,Cook"` into `["Gamer", "Cook"]`. `profileComplete` is derived — never trust the client to tell you if a profile is complete.

---

## 13. Partial Updates (PATCH-style PUT)

### The problem
User wants to update just their bio — they shouldn't have to send all 15 fields.

### The solution — null-safe field mapping
```java
public GameUser updateCard(UUID userId, PlayerCardRequest req) {
    GameUser user = repo.findById(userId).orElseThrow();

    if (req.getClassRole() != null) user.setClassRole(req.getClassRole());
    if (req.getBio() != null)       user.setBio(req.getBio());
    if (req.getStatDev() != null)   user.setStatDev(req.getStatDev());
    // ... repeat for each field

    return repo.save(user);
}
```
> **Interview angle:** `null` means "not sent" — only overwrite fields that are non-null in the request. This is PATCH semantics on a PUT endpoint. True PUT should replace the entire resource. We chose this pragmatic hybrid because our frontend always sends all fields, but partial updates still work for testing via Postman.

### Why not use PATCH?
- PUT is simpler — one endpoint, one DTO
- Our frontend always sends the full object anyway
- PATCH with JSON Merge Patch or JSON Patch adds complexity for no practical gain here

> **Interview answer:** "We use PUT with null-safe updates as a pragmatic choice. In a larger API, I'd use PATCH with a proper merge strategy and PUT for full replacement."

---

## Quick Interview Cheat Sheet

| Concept | One-liner |
|---|---|
| `@SpringBootApplication` | `@Configuration` + `@ComponentScan` + `@EnableAutoConfiguration` combined |
| Field vs Constructor injection | Constructor is preferred — explicit deps, easier to test |
| `ResponseEntity<T>` | Control HTTP status code + headers + body independently |
| Redis `INCR` atomicity | Single CPU instruction — no race condition, no locks needed |
| `AtomicInteger` vs `int++` | `AtomicInteger` is thread-safe via CAS; `int++` is 3 ops, not atomic |
| `${ENV:default}` syntax | Use env var if set, otherwise fall back to default value |
| CORS is browser-enforced | Server sends headers, browser decides. curl/Postman ignores CORS |
| CORS needs restart | Config loaded once at startup — no hot reload |
| OPTIONS preflight | Browser asks permission before cross-origin POST/PUT |
| Actuator `/health` | Used by Docker/K8s to decide if container should receive traffic |
| `disablePeerVerification()` | Skip SSL cert hostname check — needed for cloud Redis dynamic IPs |
| SLF4J `{}` placeholder | Lazy string format — only allocated if log level is active |
| `@Data` (Lombok) | Generates getters + setters + toString + equals + hashCode at compile time |
| Graceful degradation | System keeps working at reduced capability when a dependency fails |
| Lettuce vs Jedis | Lettuce = non-blocking, single connection; Jedis = blocking, pool |
| `management.health.redis.enabled=false` | Prevent Redis failure from marking app as DOWN in health check |
| JPA `@Entity` | Maps a Java class to a database table — each instance = one row |
| `@GeneratedValue(UUID)` | Database generates unique UUID primary keys — no collision risk |
| Request DTO vs Response DTO | Request = what client sends (validated), Response = what server returns (shaped) |
| Jakarta `@Valid` | Triggers bean validation on controller method params — returns 400 on failure |
| Null-safe partial update | Only overwrite fields that are non-null in the request — preserves existing data |
| `LinkedHashMap` for ordered JSON | Maintains insertion order — stats always render DEV → DESIGN → BRAIN → SOCIAL → GRIND |
| `profileComplete` flag | Computed field — derive from data state, don't trust client to set it |

---

*Updated at commit `40486cd` — next update at commit 5 from here*
