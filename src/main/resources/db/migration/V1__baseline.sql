-- V1__baseline.sql
--
-- Baseline schema for DevQuest — matches what Hibernate `ddl-auto=update`
-- has been generating from JPA entities up through 2026-07-04. On a fresh
-- OCI database, Flyway runs this and reaches the same state Hibernate would.
--
-- Existing prod DBs (EC2) get `baseline-on-migrate=true` applied on first
-- boot — Flyway stamps the schema as already-at-V1 without re-running this
-- file, so no data is touched.
--
-- Column widths & defaults MUST stay in sync with the @Column annotations
-- on GameUser.java and PrintRequest.java. If you change an entity, ADD a
-- V{N}__descriptive-name.sql file — do NOT edit this one.

-- ── game_user (typing challenge players + saved cards) ─────────────────
CREATE TABLE IF NOT EXISTS game_user (
    id                UUID          PRIMARY KEY,
    first_name        VARCHAR(24)   NOT NULL,
    last_name         VARCHAR(24)   NOT NULL,
    created_at        TIMESTAMP     WITHOUT TIME ZONE,
    class_role        VARCHAR(50),
    bio               VARCHAR(280),
    motto             VARCHAR(120),
    photo_url         VARCHAR(500),
    card_style        VARCHAR(20)   DEFAULT 'minimal',
    wanted_level      INTEGER       DEFAULT 1,
    wanted_text       VARCHAR(100),
    level             INTEGER       DEFAULT 1,
    xp_percent        INTEGER       DEFAULT 0,
    stat_dev          INTEGER       DEFAULT 50,
    stat_design       INTEGER       DEFAULT 50,
    stat_brain        INTEGER       DEFAULT 50,
    stat_social       INTEGER       DEFAULT 50,
    stat_grind        INTEGER       DEFAULT 50,
    traits            VARCHAR(200),
    profile_complete  BOOLEAN       DEFAULT FALSE,
    updated_at        TIMESTAMP     WITHOUT TIME ZONE,
    best_wpm          INTEGER       DEFAULT 0,
    best_accuracy     INTEGER       DEFAULT 0
);

-- Leaderboard: findAllByOrderByCreatedAtAsc is the hot query
CREATE INDEX IF NOT EXISTS idx_gameuser_created_at
    ON game_user (created_at);

-- registerOrGet uniqueness — enforced at DB so concurrent registers
-- can't race a duplicate row past the JPA layer
CREATE UNIQUE INDEX IF NOT EXISTS uk_gameuser_name
    ON game_user (first_name, last_name);

-- ── print_requests (PVC card postal orders) ────────────────────────────
CREATE TABLE IF NOT EXISTS print_requests (
    id             UUID          PRIMARY KEY,
    full_name      VARCHAR(255)  NOT NULL,
    address_line1  VARCHAR(255)  NOT NULL,
    address_line2  VARCHAR(255),
    city           VARCHAR(255)  NOT NULL,
    pincode        VARCHAR(6)    NOT NULL,
    phone          VARCHAR(15)   NOT NULL,
    card_id        UUID,
    created_at     TIMESTAMP     WITHOUT TIME ZONE NOT NULL
);
