-- V2__constraints_and_indexes.sql
--
-- Post-baseline hardening. This runs on TOP of V1 on both fresh installs
-- and existing DBs (which V1 baselined without running). Everything here
-- is idempotent (`IF NOT EXISTS`) and non-destructive, so re-running
-- against a partially-updated schema is safe.

-- Backfill: any pre-existing row with NULL created_at gets stamped so we
-- can enforce NOT NULL below.
UPDATE game_user
   SET created_at = COALESCE(created_at, CURRENT_TIMESTAMP);

-- Enforce created_at NOT NULL going forward. New rows populate it via
-- @Column default in GameUser.java, but the DB should not trust that.
ALTER TABLE game_user
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;

-- Bounded score columns: level/xp/stats are constrained in the API layer
-- (@Min/@Max on PlayerCardRequest) but DB should enforce too. Prevents
-- someone bypassing the API from wedging garbage.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.check_constraints
                   WHERE constraint_name = 'chk_gameuser_stats') THEN
        ALTER TABLE game_user ADD CONSTRAINT chk_gameuser_stats CHECK (
            (level         IS NULL OR level         BETWEEN 0   AND 1000) AND
            (xp_percent    IS NULL OR xp_percent    BETWEEN 0   AND 100)  AND
            (wanted_level  IS NULL OR wanted_level  BETWEEN 1   AND 5)    AND
            (stat_dev      IS NULL OR stat_dev      BETWEEN 0   AND 100)  AND
            (stat_design   IS NULL OR stat_design   BETWEEN 0   AND 100)  AND
            (stat_brain    IS NULL OR stat_brain    BETWEEN 0   AND 100)  AND
            (stat_social   IS NULL OR stat_social   BETWEEN 0   AND 100)  AND
            (stat_grind    IS NULL OR stat_grind    BETWEEN 0   AND 100)  AND
            (best_wpm      IS NULL OR best_wpm      BETWEEN 0   AND 300)  AND
            (best_accuracy IS NULL OR best_accuracy BETWEEN 0   AND 100)
        );
    END IF;
END$$;

-- Non-blank names (whitespace-only is not a valid identity).
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.check_constraints
                   WHERE constraint_name = 'chk_gameuser_names_nonblank') THEN
        ALTER TABLE game_user ADD CONSTRAINT chk_gameuser_names_nonblank CHECK (
            length(trim(first_name)) >= 2 AND
            length(trim(last_name))  >= 2
        );
    END IF;
END$$;

-- print_requests — enforce shape at DB layer. All new rows already pass
-- the checks in PrintRequestService, but this locks it down.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.check_constraints
                   WHERE constraint_name = 'chk_print_pincode_digits') THEN
        ALTER TABLE print_requests ADD CONSTRAINT chk_print_pincode_digits CHECK (
            pincode ~ '^\d{6}$'
        );
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.check_constraints
                   WHERE constraint_name = 'chk_print_phone_digits') THEN
        ALTER TABLE print_requests ADD CONSTRAINT chk_print_phone_digits CHECK (
            phone ~ '^\+?\d{10,15}$'
        );
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.check_constraints
                   WHERE constraint_name = 'chk_print_city_allowed') THEN
        ALTER TABLE print_requests ADD CONSTRAINT chk_print_city_allowed CHECK (
            upper(city) IN ('HYDERABAD', 'NAGPUR')
        );
    END IF;
END$$;

-- Hot path indexes. game_user.created_at was covered in V1; add one on
-- print_requests to keep the /print-request/count fast even at scale.
CREATE INDEX IF NOT EXISTS idx_printreq_created_at ON print_requests (created_at);
CREATE INDEX IF NOT EXISTS idx_printreq_card_id    ON print_requests (card_id)
    WHERE card_id IS NOT NULL;
