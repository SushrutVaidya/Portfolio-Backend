# Portfolio App — Architecture & Page Flow Reference

> **Stack**: Spring Boot 4.0.2 (Java 17) · PostgreSQL 15 · Redis (Lettuce) · Vanilla JS · Nginx
> **Last updated**: April 21, 2026
> **Repo**: This doc lives in `Portfolio-Backend`. Frontend repo: `portfolio-frontend`

---

## Table of Contents

1. [System Architecture](#1-system-architecture)
2. [Database Schema](#2-database-schema)
3. [API Endpoint Reference](#3-api-endpoint-reference)
4. [Page Flow & User Journey](#4-page-flow--user-journey)
5. [API Data Flow — Sequence Diagrams](#5-api-data-flow--sequence-diagrams)
6. [Transition & Animation Design](#6-transition--animation-design)
7. [Graceful Degradation](#7-graceful-degradation)
8. [Developer Quick Reference](#8-developer-quick-reference)

---

## 1. System Architecture

### 1.1 High-Level Overview

```mermaid
flowchart LR
    subgraph CLIENT ["CLIENT LAYER"]
        direction TB
        Browser["🖥️ Browser<br/><i>Chrome · Safari · Firefox</i>"]
        HTML["HTML <small>(10 pages)</small>"]
        CSS["CSS <small>(10 files, 77 @keyframes)</small>"]
        JS["Vanilla JS <small>(12 files, no framework)</small>"]
        LS[("localStorage<br/><code>dq-user-id</code><br/><code>dq-player-first</code><br/><code>dq-player-last</code>")]
        Browser --- HTML & CSS & JS
        JS --- LS
    end

    subgraph SERVER ["SERVER BOUNDARY"]
        direction TB

        subgraph PROXY ["REVERSE PROXY"]
            Nginx["⚙️ Nginx"]
        end

        subgraph APP ["APPLICATION LAYER"]
            Boot["☕ Spring Boot<br/><i>Port 8081 · Java 17</i>"]
            Controller["REST Controller<br/><code>/api/*</code>"]
            Services["Service Layer<br/><i>StatsService<br/>GameUserService<br/>RickrollService</i>"]
            Boot --- Controller --- Services
        end

        subgraph DATA ["DATA LAYER"]
            PG[("🐘 PostgreSQL<br/><i>localhost:5432</i><br/><code>portfolio</code> DB")]
            Redis[("⚡ Redis<br/><i>localhost:6379</i><br/><i>Lettuce client</i>")]
        end

        Nginx -->|"HTTP :8081<br/><code>/api/*</code> only"| Boot
        Nginx -->|"Static files<br/>HTML · CSS · JS · audio"| Static["📁 Static Assets"]
        Services -->|"JDBC<br/>JPA/Hibernate"| PG
        Services -->|"Redis Protocol<br/>Lettuce"| Redis
    end

    Browser -->|"HTTPS :443<br/><i>(HTTP :8080 dev)</i>"| Nginx

    classDef client fill:#e8f4fd,stroke:#2196F3,stroke-width:2px,color:#1565C0
    classDef proxy fill:#fff3e0,stroke:#FF9800,stroke-width:2px,color:#E65100
    classDef app fill:#e8f5e9,stroke:#4CAF50,stroke-width:2px,color:#2E7D32
    classDef data fill:#fce4ec,stroke:#E91E63,stroke-width:2px,color:#880E4F
    classDef storage fill:#f3e5f5,stroke:#9C27B0,stroke-width:1px,color:#6A1B9A

    class Browser,HTML,CSS,JS client
    class Nginx proxy
    class Boot,Controller,Services app
    class PG,Redis data
    class LS,Static storage
```

### 1.2 Architecture Notes

| Layer | Technology | Port | Notes |
|-------|-----------|------|-------|
| Client | Vanilla HTML/CSS/JS | — | No build step, no bundler, no framework. `fetch()` for API calls |
| Reverse Proxy | Nginx | 443 (prod) / 8080 (dev) | Routes `/api/*` to Spring Boot, serves everything else as static |
| Application | Spring Boot 4.0.2 | 8081 | Java 17, Lombok, Jakarta Validation, Spring Data JPA + Redis |
| Relational DB | PostgreSQL | 5432 | Single table (`game_user`), Hibernate DDL auto-update |
| Cache | Redis | 6379 | Lettuce client, optional SSL, env var config for cloud deploy |

**CORS Configuration** — `CorsConfig.java`:
- Allowed origins: `localhost:5500`, `127.0.0.1:5500`, `localhost:3000`, `localhost:8080`
- Allowed methods: `GET`, `POST`, `PUT`, `OPTIONS`
- Allowed headers: `*`
- Mapping: `/**`

---

## 2. Database Schema

### 2.1 Entity Relationship Diagram

```mermaid
erDiagram
    GAME_USER {
        UUID id PK "Auto-generated, strategy UUID"
        VARCHAR_24 first_name "NOT NULL"
        VARCHAR_24 last_name "NOT NULL"
        TIMESTAMP created_at "Default: now()"
        VARCHAR_50 class_role "Nullable"
        VARCHAR_280 bio "Nullable"
        VARCHAR_120 motto "Nullable"
        VARCHAR_500 photo_url "Nullable"
        VARCHAR_20 card_style "Default: minimal"
        INTEGER wanted_level "Default: 1, Range: 1-5"
        VARCHAR_100 wanted_text "Nullable"
        INTEGER level "Default: 1"
        INTEGER xp_percent "Default: 0, Range: 0-100"
        INTEGER stat_dev "Default: 50, Range: 0-100"
        INTEGER stat_design "Default: 50, Range: 0-100"
        INTEGER stat_brain "Default: 50, Range: 0-100"
        INTEGER stat_social "Default: 50, Range: 0-100"
        INTEGER stat_grind "Default: 50, Range: 0-100"
        VARCHAR_200 traits "Nullable, comma-separated"
        BOOLEAN profile_complete "Default: false"
        TIMESTAMP updated_at "Nullable, not auto-set"
    }

    REDIS_KEYS {
        STRING rickroll_count "Key: rickroll:count, Value: int"
        STRING test_value "Key: test:key, Value: test:value"
    }

    GAME_USER ||--o{ REDIS_KEYS : "no direct relation — separate stores"
```

### 2.2 Schema Notes

- **Single table design** — `game_user` holds identity + card customization + stats in one entity
- **Traits storage** — Comma-separated string in DB, split to `List<String>` in response DTO
- **Stats** — 5 fixed integer columns (DEV, DESIGN, BRAIN, SOCIAL, GRIND), mapped to `LinkedHashMap<String, Integer>` in response
- **No foreign keys** — Redis keys are independent; no relational joins
- **User lookup** — `findByFirstNameAndLastName()` derived query (case-sensitive, no unique constraint)
- **`updated_at` not auto-set** — Service layer does not call `setUpdatedAt()` on save. Needs manual fix if timestamp tracking is desired
- **DDL** — `spring.jpa.hibernate.ddl-auto=update` auto-migrates schema changes

---

## 3. API Endpoint Reference

### 3.1 Endpoint Map

```mermaid
flowchart LR
    subgraph LIVE ["✅ LIVE ENDPOINTS"]
        direction TB
        E1["<b>GET</b> /api/stats"]
        E2["<b>POST</b> /api/user"]
        E3["<b>GET</b> /api/user/{id}/card"]
        E4["<b>POST</b> /api/user/{id}/card"]
        E5["<b>GET</b> /api/rickroll"]
        E6["<b>GET</b> /api/rickroll/count"]
        E7["<b>GET</b> /api/redis/test"]
    end

    subgraph PLANNED ["⚠️ PLANNED"]
        direction TB
        P1["<b>POST</b> /api/score"]
        P2["<b>GET</b> /api/leaderboard"]
    end

    E1 -->|"StatsService"| S1["Location, game, song,<br/>book, time, songURL"]
    E2 -->|"GameUserService"| S2["Register or return<br/>existing user"]
    E3 -->|"GameUserService"| S3["Player card data<br/>(stats as map, traits as list)"]
    E4 -->|"GameUserService"| S4["Null-safe partial<br/>card update"]
    E5 -->|"RickrollService"| S5["Increment + return<br/>Redis counter"]
    E6 -->|"RickrollService"| S6["Read Redis counter"]
    E7 -->|"inline"| S7["Redis connection test"]

    P1 -.->|"TBD"| S8["Save challenge scores<br/>(wpm, accuracy, difficulty)"]
    P2 -.->|"TBD"| S9["Ranked user list with<br/>performance metrics"]

    classDef live fill:#e8f5e9,stroke:#4CAF50,stroke-width:2px,color:#2E7D32
    classDef planned fill:#fff8e1,stroke:#FFC107,stroke-width:2px,color:#F57F17
    classDef detail fill:#f5f5f5,stroke:#9E9E9E,stroke-width:1px,color:#424242

    class E1,E2,E3,E4,E5,E6,E7 live
    class P1,P2 planned
    class S1,S2,S3,S4,S5,S6,S7,S8,S9 detail
```

### 3.2 Endpoint Details

#### `GET /api/stats` — Portfolio live data
```
Response: {
  "location":    "Hyderabad",                    // from application.properties
  "game":        "Counter Strike 2",             // from application.properties
  "currentTime": "2:30pm",                       // LocalTime.now() formatted
  "timeStamp":   1713700000000,                  // System.currentTimeMillis()
  "songName":    "a banger",                     // from application.properties
  "songURL":     "/audio/ManMazeUmalunGele.mp3", // rotates every 4 hours (hour/4)
  "bookName":    "Fundamentals of Microcontrollers and MicroProcessors"
}

Song rotation: slot = hour / 4
  00-03 → Funkadelic.mp3
  04-07 → Lawrie.mp3
  08-11 → Aladeeeeen.mp3
  12-15 → ManMazeUmalunGele.mp3
  16-19 → EverybodyWantsToRuleTheWorldJoshGad.mp3
  20-23 → miMorchaNelaNahi.mp3
```

#### `POST /api/user` — User registration (register-or-get)
```
Request:  { "firstName": "Sushrut", "lastName": "Vaidya" }
Response: Full GameUser entity JSON (includes UUID id)
Error:    400 { "error": "Length is too short babe" }  (if name < 2 chars)
Logic:    findByFirstNameAndLastName → exists? return it : create new
```

#### `GET /api/user/{id}/card` — Fetch player card
```
Path:     id = UUID
Response: PlayerCardResponse {
  id, firstName, lastName, classRole, bio, motto, photoUrl,
  cardStyle, wantedLevel, wantedText, level, xpPercent,
  stats: { DEV: 50, DESIGN: 50, BRAIN: 50, SOCIAL: 50, GRIND: 50 },
  traits: ["Hyderabad", "Gamer", "Cook"],
  profileComplete, createdAt, updatedAt
}
Error:    RuntimeException("BuddyBoy your user is not registered")
```

#### `POST /api/user/{id}/card` — Partial card update
```
Path:     id = UUID
Request:  PlayerCardRequest (all fields nullable — only non-null fields update)
          Validation: @Size, @Min(0), @Max(100) on stats, @Min(1) @Max(5) on wantedLevel
Response: PlayerCardResponse (same as GET)
Note:     Uses POST but semantically is PUT (CORS allows PUT)
```

#### `GET /api/rickroll` — Increment rickroll counter
```
Response: { "count": 42 }
Storage:  Redis key "rickroll:count" → INCR
Fallback: AtomicInteger in-memory if Redis fails (permanent switch, no retry)
```

---

## 4. Page Flow & User Journey

### 4.1 Complete Site Flow

```mermaid
flowchart TD
    START(("🌐 Visitor<br/>arrives"))

    START --> LANDING

    subgraph LANDING_PAGE ["landing.html — ENTRY POINT"]
        LANDING["<b>DevQuest Landing</b><br/><i>Hero + 3 buttons</i>"]
    end

    LANDING -->|"🎮 Enter DevQuest<br/><i>click → name modal</i>"| MODAL
    LANDING -->|"👤 Who am I?<br/><i>click → test/aboutme.html</i>"| ABOUTME
    LANDING -->|"💼 View Portfolio<br/><i>click → ../index.html</i>"| PORTFOLIO

    MODAL{"Name Input Modal<br/><code>[first] [last]</code><br/>POST /api/user"}
    MODAL -->|"Start Challenge →<br/><i>window.location</i>"| CAPTCHA

    subgraph CHALLENGES ["CHALLENGE FLOW — linear, gated"]
        direction LR
        CAPTCHA["<b>01 · Captcha</b><br/><i>Shape sorting</i><br/>6 shapes → 5 holes<br/>3 lives"]
        DEVTYPE["<b>02 · DevType</b><br/><i>Typing speed test</i><br/>Easy/Med/Hard<br/>60s or 45s timer"]
        INCIDENT["<b>03 · Incident</b><br/><i>Bug hunt</i><br/>5 rounds × 45s<br/>4 languages"]

        CAPTCHA -->|"[Next →]<br/><i>window.location</i>"| DEVTYPE
        DEVTYPE -->|"[Next Challenge →]<br/><i>window.location</i>"| INCIDENT
    end

    DEVTYPE -.->|"View Full Leaderboard →<br/><i>(optional link)</i>"| LEADERBOARD
    INCIDENT -->|"✅ Pass: meet the developer →<br/><i>a href</i>"| ABOUTME
    INCIDENT -.->|"❌ Fail: [retry]"| INCIDENT

    subgraph EXPLORE ["EXPLORE FLOW — chapter-based, contextual CTAs"]
        direction TB
        ABOUTME["<b>Chapter 1: The Reveal</b><br/><i>test/aboutme.html</i><br/>GTA V style · 7 sections<br/>Stats · Personas · Kitchen<br/>Gaming · Lore · Player Card"]
        CARDEDITOR["<b>Chapter 2: The Creation</b><br/><i>test/card-experiments.html</i><br/>9 card styles · live editor<br/>Save + Download as PNG"]
        LEADERBOARD["<b>Chapter 3: The Arena</b><br/><i>test/leaderboard.html</i><br/>Rankings · Quirks · Profile<br/>Registration bar for new users"]

        ABOUTME -->|"CREATE YOUR CARD<br/><i>a href · bottom of page</i>"| CARDEDITOR
        CARDEDITOR -->|"View Leaderboard →<br/><i>post-save fadeUp CTA</i>"| LEADERBOARD
    end

    LEADERBOARD -->|"Portfolio →<br/><i>CTA card</i>"| PORTFOLIO
    LEADERBOARD -.->|"Behind DevQuest<br/><i>CTA card</i>"| ABOUTME
    LEADERBOARD -.->|"Customise Your Card →<br/><i>card preview link</i>"| CARDEDITOR
    CARDEDITOR -.->|"Behind DevQuest<br/><i>post-save secondary</i>"| ABOUTME

    subgraph DESTINATION ["PORTFOLIO — tech/professional"]
        PORTFOLIO["<b>index.html</b><br/>Hero · About · Interests<br/>Tech Stack · Patent · Footer"]
    end

    PORTFOLIO -.->|"🎮 DevQuest<br/><i>footer gamepad icon</i>"| LANDING

    classDef entry fill:#E3F2FD,stroke:#1976D2,stroke-width:2px,color:#0D47A1
    classDef challenge fill:#FFF3E0,stroke:#F57C00,stroke-width:2px,color:#E65100
    classDef explore fill:#E8F5E9,stroke:#388E3C,stroke-width:2px,color:#1B5E20
    classDef destination fill:#F3E5F5,stroke:#7B1FA2,stroke-width:2px,color:#4A148C
    classDef decision fill:#FFF9C4,stroke:#F9A825,stroke-width:2px,color:#F57F17

    class LANDING entry
    class CAPTCHA,DEVTYPE,INCIDENT challenge
    class ABOUTME,CARDEDITOR,LEADERBOARD explore
    class PORTFOLIO destination
    class MODAL decision
```

### 4.2 Page Details

| Page | File | API Calls | localStorage | Nav Bar | Registration Bar |
|------|------|-----------|-------------|---------|-----------------|
| Landing | `devquest/landing.html` | `POST /api/user` | SET: name, first, last, id | No | No (has modal) |
| Captcha | `devquest/captcha.html` | None | None | Challenge progress bar | No |
| DevType | `devquest/devtype.html` | `POST /api/score`, `GET /api/leaderboard` | GET: user-id, player-first | Challenge progress bar | No |
| Incident | `devquest/incident.html` | None | None | Challenge progress bar | No |
| About v2 | `devquest/test/aboutme.html` | None | None | **No** (immersive) | **No** |
| Card Editor | `devquest/test/card-experiments.html` | `GET /api/user/{id}/card`, `PUT /api/user/{id}/card` | GET: user-id, player-first, player-last | **No** (focused) | **No** |
| Leaderboard | `devquest/test/leaderboard.html` | `POST /api/user` (reg bar) | GET/SET: all 4 keys | **Yes** (glassmorphic) | **Yes** |
| Portfolio | `index.html` | `GET /api/stats`, `GET /api/rickroll` | sessionStorage: rickrolled | No | No |

### 4.3 Two User Paths

```mermaid
flowchart LR
    V(("Visitor"))

    V --> R{"Recruiter<br/>or Explorer?"}

    R -->|"Recruiter<br/>(wants portfolio fast)"| F1["View Portfolio →"]
    F1 --> P["index.html<br/>Tech portfolio"]
    P -.->|"Footer gamepad icon<br/>(discover later)"| DQ["DevQuest Landing"]

    R -->|"Explorer<br/>(wants the experience)"| F2["Enter DevQuest →"]
    F2 --> C["3 Challenges"]
    C --> A["About Me v2<br/>(the reveal)"]
    A --> CE["Card Editor<br/>(the creation)"]
    CE --> LB["Leaderboard<br/>(the arena)"]
    LB --> P

    classDef fast fill:#F3E5F5,stroke:#7B1FA2,stroke-width:2px
    classDef full fill:#E8F5E9,stroke:#388E3C,stroke-width:2px

    class F1,P fast
    class F2,C,A,CE,LB full
```

---

## 5. API Data Flow — Sequence Diagrams

### 5.1 Portfolio Stats Fetch (`GET /api/stats`)

```mermaid
sequenceDiagram
    autonumber
    participant B as 🖥️ Browser<br/>(main.js)
    participant N as ⚙️ Nginx
    participant S as ☕ Spring Boot<br/>(StatsService)

    B->>N: GET /api/stats
    N->>S: proxy_pass → localhost:8081/api/stats

    Note over S: All data from application.properties<br/>+ system clock. No DB or Redis query.

    S->>S: location ← "Hyderabad"
    S->>S: game ← "Counter Strike 2"
    S->>S: songName ← "a banger"
    S->>S: bookName ← "Fundamentals of..."
    S->>S: currentTime ← LocalTime.now() → "2:30pm"
    S->>S: songURL ← getSongURL() → hour/4 slot

    S-->>N: 200 OK · StatsResponse JSON
    N-->>B: StatsResponse JSON

    Note over B: Update DOM:<br/>• Location text (hover → cityscape GIF)<br/>• Song name (hover → audio preview)<br/>• Game name (hover → game GIF)<br/>• Book name (hover → video overlay)<br/><br/>Latency: < 10ms (no DB, pure config)

    alt API unreachable
        B->>N: GET /api/stats
        N--xB: Connection refused / timeout
        Note over B: Use hardcoded defaults<br/>already in HTML.<br/>No loading spinner needed.
    end
```

### 5.2 User Registration (`POST /api/user`)

```mermaid
sequenceDiagram
    autonumber
    participant B as 🖥️ Browser<br/>(landing.js)
    participant N as ⚙️ Nginx
    participant S as ☕ Spring Boot<br/>(GameUserService)
    participant DB as 🐘 PostgreSQL

    B->>B: User enters first + last name in modal
    B->>B: Client validation: each name ≥ 2 chars

    B->>N: POST /api/user<br/>{"firstName":"Sushrut","lastName":"Vaidya"}
    N->>S: proxy_pass → localhost:8081

    S->>S: Server validation: trim + length check

    alt Name too short (< 2 chars)
        S-->>N: 400 {"error":"Length is too short babe"}
        N-->>B: 400 Bad Request
        Note over B: Show error, stay on modal
    end

    S->>DB: SELECT * FROM game_user<br/>WHERE first_name = ? AND last_name = ?

    alt User exists
        DB-->>S: GameUser row found
        S-->>N: 200 OK · existing GameUser JSON
    else New user
        DB-->>S: No rows
        S->>DB: INSERT INTO game_user<br/>(id, first_name, last_name, created_at,<br/>card_style='minimal', wanted_level=1,<br/>level=1, xp_percent=0, all stats=50)
        DB-->>S: New row with UUID
        S-->>N: 200 OK · new GameUser JSON
    end

    N-->>B: GameUser JSON (includes UUID)

    B->>B: localStorage.setItem('dq-user-id', data.id)
    B->>B: localStorage.setItem('dq-player-first', firstName)
    B->>B: localStorage.setItem('dq-player-last', lastName)
    B->>B: localStorage.setItem('dq-player-name', firstName)
    B->>B: window.location.href = 'captcha.html'

    Note over B: If backend offline:<br/>• Still stores names in localStorage<br/>• No UUID → scores won't save, card won't persist<br/>• Still navigates to captcha.html
```

### 5.3 Player Card Save (`POST /api/user/{id}/card`)

```mermaid
sequenceDiagram
    autonumber
    participant B as 🖥️ Browser<br/>(card-experiments.html)
    participant N as ⚙️ Nginx
    participant S as ☕ Spring Boot<br/>(GameUserService)
    participant DB as 🐘 PostgreSQL

    B->>B: User customizes card:<br/>style, class, stats, traits, wanted level
    B->>B: Click [Save Card]
    B->>B: Check localStorage('dq-user-id')

    alt No user ID
        B--xB: showSaveStatus('No user ID — register first', 'error')
        Note over B: Blocked. No API call made.
    end

    B->>N: POST /api/user/{uuid}/card<br/>PlayerCardRequest JSON<br/>(only non-null fields sent)
    N->>S: proxy_pass → localhost:8081

    S->>DB: SELECT * FROM game_user WHERE id = ?

    alt User not found
        DB-->>S: No rows
        S-->>N: RuntimeException<br/>"BuddyBoy your user is not registered"
        N-->>B: 500 Error
        Note over B: showSaveStatus(err.message, 'error')
    end

    DB-->>S: GameUser row

    Note over S: Null-safe partial update:<br/>for each field in request:<br/>  if (field != null) entity.setField(field)<br/><br/>Only non-null fields overwrite.<br/>Null fields left unchanged.

    S->>DB: UPDATE game_user SET ... WHERE id = ?
    DB-->>S: Updated row

    S->>S: toResponse(entity)<br/>• stats → LinkedHashMap {DEV,DESIGN,BRAIN,SOCIAL,GRIND}<br/>• traits → split(",") → List&lt;String&gt;

    S-->>N: 200 OK · PlayerCardResponse JSON
    N-->>B: PlayerCardResponse JSON

    B->>B: showSaveStatus('Saved', 'success')
    B->>B: document.getElementById('cardNext').style.display = ''
    Note over B: "What's Next" section fades in (fadeUp 400ms)<br/>• View Leaderboard → (primary CTA)<br/>• Behind DevQuest (secondary link)
```

### 5.4 Rickroll Counter (Redis with Fallback)

```mermaid
sequenceDiagram
    autonumber
    participant B as 🖥️ Browser<br/>(main.js)
    participant S as ☕ Spring Boot<br/>(RickrollService)
    participant R as ⚡ Redis

    B->>S: GET /api/rickroll<br/>(YouTube link clicked)

    alt Redis available
        S->>R: INCR rickroll:count
        R-->>S: 42 (new count)
        S-->>B: {"count": 42}
    else Redis down (first failure)
        S->>R: INCR rickroll:count
        R--xS: Connection refused
        Note over S: redisAvailable = false<br/>(permanent — no retry logic)
        S->>S: fallbackCounter.incrementAndGet()
        S-->>B: {"count": 1} (in-memory)
    else Redis down (subsequent calls)
        Note over S: redisAvailable already false<br/>Skip Redis entirely
        S->>S: fallbackCounter.incrementAndGet()
        S-->>B: {"count": N} (in-memory)
    end
```

### 5.5 Future: Live Data with Redis Caching

This is the **target architecture** for when `/api/stats` evolves to pull real-time data from external APIs (Spotify, Steam, Goodreads).

```mermaid
sequenceDiagram
    autonumber
    participant B as 🖥️ Browser
    participant S as ☕ Spring Boot
    participant R as ⚡ Redis
    participant DB as 🐘 PostgreSQL
    participant EXT as 🌐 External APIs<br/>(Spotify · Steam · Goodreads)

    B->>S: GET /api/stats (live data)

    S->>R: GET stats:live

    alt Cache HIT (TTL < 60s)
        R-->>S: Cached JSON
        Note over S: Latency: ~3ms
        S-->>B: 200 OK · StatsResponse
        Note over B: Skeleton → content<br/>crossfade (300ms ease)
    else Cache MISS (expired or first request)
        R-->>S: null

        par Parallel external fetches
            S->>EXT: Spotify API → last song
            S->>EXT: Steam API → last game
            S->>EXT: Goodreads API → current book
        end

        EXT-->>S: Song data (~200-500ms)
        EXT-->>S: Game data (~200-500ms)
        EXT-->>S: Book data (~200-500ms)

        Note over S: Aggregate responses<br/>Total latency: ~500ms (parallel)

        S->>R: SET stats:live {JSON} EX 60
        Note over R: TTL = 60 seconds
        R-->>S: OK

        S-->>B: 200 OK · StatsResponse
        Note over B: Skeleton → content<br/>crossfade (300ms ease)<br/><br/>User sees skeleton for ~500ms<br/>on first load of each 60s window
    end
```

### 5.6 Card Download (Client-Side Only)

```mermaid
sequenceDiagram
    autonumber
    participant U as 👤 User
    participant B as 🖥️ Browser<br/>(card-experiments.html)
    participant H as 📦 html2canvas<br/>(CDN v1.4.1)

    U->>B: Click [Download Card]
    B->>B: Button → disabled, text → "Exporting..."

    B->>B: Create hidden off-screen container<br/>position:fixed; left:-9999px

    B->>B: Render selected card style<br/>CARD_STYLES[index].build(true)

    B->>B: Set crossOrigin='anonymous'<br/>on all <img> elements

    B->>B: await Promise.all(images loaded)

    B->>H: html2canvas(cardEl, {<br/>  backgroundColor: null,<br/>  scale: 2,<br/>  useCORS: true<br/>})

    H-->>B: Canvas element (2x retina resolution)

    B->>B: canvas.toBlob(blob, 'image/png')
    B->>B: URL.createObjectURL(blob)
    B->>B: Trigger <a download> click
    B->>U: PNG file downloads

    B->>B: Cleanup: remove container,<br/>re-enable button
    B->>B: showSaveStatus('Downloaded!', 'success')
    B->>B: Reveal #cardNext section (fadeUp 400ms)

    Note over B: NO API CALLS<br/>Entirely client-side.<br/>Works offline.
```

---

## 6. Transition & Animation Design

### 6.1 Animation Inventory

```mermaid
pie title @keyframes Distribution (77 total)
    "About v2 (GTA)" : 34
    "Incident" : 8
    "About v1 (Terminal)" : 8
    "Captcha" : 6
    "Portfolio" : 5
    "DevType" : 5
    "Leaderboard" : 3
    "Card Editor" : 2
    "Other" : 6
```

### 6.2 Transition Map

```mermaid
flowchart TD
    subgraph TRANSITIONS ["PAGE TRANSITION TYPES"]
        direction TB
        T1["<b>Full page load</b><br/><code>window.location.href</code><br/><i>All page-to-page transitions</i><br/><i>No SPA, each page standalone</i>"]
        T2["<b>Scroll reveal</b><br/><code>IntersectionObserver</code><br/><i>threshold: 0.15</i><br/><i>opacity 0→1, translateY 30→0px</i><br/><i>600ms ease-out, fires once</i>"]
        T3["<b>Modal overlay</b><br/><code>.visible class toggle</code><br/><i>Backdrop fade 300ms ease</i><br/><i>Modal slide-up 300ms ease</i>"]
        T4["<b>State change</b><br/><code>CSS class add/remove</code><br/><i>Results reveal, quirk popup,</i><br/><i>card flip, game feedback</i><br/><i>300-600ms ease-in-out</i>"]
        T5["<b>Post-action CTA</b><br/><code>display:none → block</code><br/><i>fadeUp animation 400ms ease</i><br/><i>opacity 0→1, translateY 12→0px</i>"]
        T6["<b>Loading sequence</b><br/><code>GTA intro overlay</code><br/><i>Full-screen 2-3s, custom timing</i><br/><i>Only on test/aboutme.html</i>"]
    end

    classDef trans fill:#E3F2FD,stroke:#1976D2,stroke-width:1px,color:#0D47A1
    class T1,T2,T3,T4,T5,T6 trans
```

### 6.3 Per-Page Transition Spec

| Page | Entry | Duration | Easing | Key Animation |
|------|-------|----------|--------|---------------|
| `landing.html` | Direct URL load | Instant | — | Flowing orbs + grain overlay |
| Name Modal | Button click | 300ms | `ease` | Backdrop fade + modal slide-up |
| `captcha.html` | `window.location` | 600ms | `cubic-bezier(0.25,0.46,0.45,0.94)` | Shape drop-in + particle bursts (450-650ms) |
| `devtype.html` | `window.location` | 400ms | `ease-in-out` | Difficulty cards fade-in |
| DevType results | Timer expires | 500ms + 800ms | `ease-out`, `linear` | Results slide + graph draw + row stagger (50ms each) |
| `incident.html` | `window.location` | 400ms | `ease` | Code snippet fade-in + timer ring SVG |
| Incident results | Rounds complete | 400ms | `ease` | Results fade + verdict stamp |
| `test/aboutme.html` | `<a>` click | 2-3s | Custom | GTA loading overlay → section IntersectionObserver reveals |
| `test/card-experiments.html` | `<a>` click | 400ms | `ease` | Card grid fade-in |
| "What's Next" | Save/download success | 400ms | `ease` | `fadeUp` (opacity 0→1, Y 12→0) |
| `test/leaderboard.html` | `<a>` click | 50ms/row | `ease` | Stagger cascade + mesh gradient BG (20s loop) |
| `index.html` | `<a>` click | 400ms | `ease` | Section fade-in on scroll + hero gradient (15s loop) |

### 6.4 Design Principles

- **No SPA transitions** — Every page is a full HTML load. No client-side routing.
- **Entry always animated** — Each page has hero/intro animations on load.
- **Duration**: 300-600ms for interactions, up to 3s for cinematic sequences (GTA loading).
- **Easing**: `ease-out` for reveals, `ease-in-out` for state changes, `cubic-bezier` for physics.
- **Stagger**: Lists/grids use `animation-delay: calc(index * 50ms)` for cascade effect.
- **No skeletons yet** — Content is pre-rendered in HTML; API data overrides defaults.
- **Mobile**: Same transitions with `@media` breakpoints. No `hover` on touch devices.
- **Theme transitions**: CSS custom properties swap instantly. No animation on theme change.
- **Scroll**: `scroll-behavior: smooth` globally. IntersectionObserver for section reveals.

---

## 7. Graceful Degradation

### 7.1 Failure Scenarios

```mermaid
flowchart TD
    subgraph FAILURES ["FAILURE SCENARIOS"]
        direction TB

        F1["<b>Backend Offline</b><br/>(Spring Boot down)"]
        F2["<b>Redis Offline</b><br/>(cache unavailable)"]
        F3["<b>PostgreSQL Offline</b><br/>(DB unavailable)"]
        F4["<b>No User ID</b><br/>(skipped registration)"]
    end

    F1 --> R1["Landing: names stored locally,<br/>challenges proceed normally"]
    F1 --> R2["Portfolio: uses HTML defaults<br/>for stats (no loading state)"]
    F1 --> R3["Card Editor: download works,<br/>save shows error message"]

    F2 --> R4["Rickroll: AtomicInteger fallback<br/>(permanent, no retry)"]
    F2 --> R5["Stats: unaffected<br/>(no cache layer yet)"]

    F3 --> R6["Registration: fails silently,<br/>no UUID stored"]
    F3 --> R7["Card save: shows error message"]

    F4 --> R8["Challenges: work fully<br/>(no score tracking)"]
    F4 --> R9["Card editor: can edit + download,<br/>cannot save to backend"]
    F4 --> R10["Leaderboard: shows<br/>registration bar"]

    classDef failure fill:#FFEBEE,stroke:#C62828,stroke-width:2px,color:#B71C1C
    classDef result fill:#E8F5E9,stroke:#2E7D32,stroke-width:1px,color:#1B5E20

    class F1,F2,F3,F4 failure
    class R1,R2,R3,R4,R5,R6,R7,R8,R9,R10 result
```

### 7.2 Core Principle

> **Every page works without the backend. The API enhances — it never gates.**
>
> - `fetch()` calls are wrapped in try/catch with silent failure
> - No loading spinners block the UI (content is pre-rendered)
> - Card download is fully client-side (html2canvas, no API needed)
> - Challenges use `window.location` for navigation, no API dependency
> - Mock leaderboard data serves as permanent fallback

---

## 8. Developer Quick Reference

### 8.1 Dev Server Commands

```bash
# Frontend (serves from devquest/test/)
cd portfolio-frontend/devquest/test
python3 -m http.server 8080
# → http://localhost:8080/aboutme.html
# → http://localhost:8080/card-experiments.html
# → http://localhost:8080/leaderboard.html

# Backend
cd Portfolio-Backend
./mvnw spring-boot:run
# → http://localhost:8081/api/stats
# → http://localhost:8081/actuator/health

# Full stack: run both in separate terminals
```

### 8.2 Key File Map

```
Portfolio-Backend/
├── src/main/resources/
│   └── application.properties ········· DB, Redis, custom props
├── src/main/java/.../backend/
│   ├── controller/controller.java ····· All REST endpoints
│   ├── entities/GameUser.java ········· JPA entity (19 columns)
│   ├── model/
│   │   ├── PlayerCardRequest.java ····· Card update DTO + validation
│   │   ├── PlayerCardResponse.java ···· Card response DTO
│   │   └── StatsResponse.java ········· Stats response DTO
│   ├── service/
│   │   ├── GameUserService.java ······· User CRUD + card logic
│   │   ├── StatsService.java ·········· Config + song rotation
│   │   └── RickrollService.java ······· Redis counter + fallback
│   └── config/
│       ├── CorsConfig.java ············ CORS origins/methods
│       └── RedisConfig.java ··········· Lettuce connection factory

portfolio-frontend/
├── index.html ························· Portfolio (7 sections, 2 observers)
├── js/main.js ························· Stats fetch + hover overlays
├── devquest/
│   ├── landing.html ··················· Entry + name modal
│   ├── js/landing.js ·················· POST /api/user + localStorage
│   ├── captcha.html ··················· Challenge 01 (no API)
│   ├── devtype.html ··················· Challenge 02 (score + leaderboard)
│   ├── js/devtype.js ·················· Typing game engine
│   ├── incident.html ·················· Challenge 03 (no API)
│   ├── js/incident.js ················· Bug hunt engine
│   └── test/
│       ├── aboutme.html ··············· GTA-style about (v2)
│       ├── card-experiments.html ······ 9-style card editor
│       ├── leaderboard.html ··········· Rankings + registration bar
│       ├── js/aboutme-v2.js ··········· GTA page logic
│       └── css/aboutme-v2.css ········· 34+ @keyframes
```

### 8.3 localStorage Keys

| Key | Set By | Read By | Purpose |
|-----|--------|---------|---------|
| `dq-player-name` | landing.js | All challenges | Display name (short) |
| `dq-player-first` | landing.js, leaderboard | card-experiments, leaderboard | First name |
| `dq-player-last` | landing.js, leaderboard | card-experiments, leaderboard | Last name |
| `dq-user-id` | landing.js, leaderboard | devtype, card-experiments, leaderboard | Backend UUID |

### 8.4 Pending Backend Work

| Status | Endpoint | Called By | Fallback |
|--------|----------|-----------|----------|
| **TODO** | `POST /api/score` | devtype.js | Silent fail, game continues |
| **TODO** | `GET /api/leaderboard` | devtype.js, leaderboard.html | `MOCK_LEADERBOARD` array |
| **Live** | `POST /api/user` | landing.js, leaderboard.html | localStorage only, no UUID |
| **Live** | `GET /api/user/{id}/card` | card-experiments.html | Empty card defaults |
| **Live** | `POST /api/user/{id}/card` | card-experiments.html | Error message shown |
| **Live** | `GET /api/stats` | main.js | HTML defaults |
| **Live** | `GET /api/rickroll` | main.js | In-memory counter |

### 8.5 API Touchpoints by Page (Quick Lookup)

```
index.html ·········· GET /api/stats (on load) · GET /api/rickroll (on YouTube click)
landing.html ········ POST /api/user (on modal submit)
captcha.html ········ (none)
devtype.html ········ POST /api/score (on game end) · GET /api/leaderboard (on game end)
incident.html ······· (none)
test/aboutme.html ··· (none)
test/card-experiments  GET /api/user/{id}/card (on load) · POST /api/user/{id}/card (on save)
test/leaderboard ···· POST /api/user (registration bar)
```

---

*Generated from actual codebase — April 21, 2026. Not aspirational architecture.*
