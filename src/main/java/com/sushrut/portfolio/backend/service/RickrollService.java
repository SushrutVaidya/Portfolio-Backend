package com.sushrut.portfolio.backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Rickroll counter — increments on every hit to /api/rickroll.
 *
 * Persistent state in Redis (Upstash) so restarts don't reset the counter.
 * Falls back to an in-memory AtomicInteger if Redis is unreachable. On
 * fallback the app keeps working — a rickroll counter that's briefly
 * inaccurate is fine, a 500 is not.
 *
 * We used to latch {@code redisAvailable=false} permanently on first
 * failure; a transient Upstash blip would silently strand us on the
 * fallback counter until process restart. Now we retry Redis every
 * {@link #RETRY_AFTER_MS} milliseconds so recovery is automatic.
 */
@Service
public class RickrollService {
    private static final String RICKROLL_KEY = "rickroll:count";
    private static final Logger logger = LoggerFactory.getLogger(RickrollService.class);

    /**
     * How long to stay on the in-memory fallback after a Redis failure
     * before probing Redis again. Long enough to avoid hammering a
     * struggling backend, short enough that a 30s outage self-heals.
     */
    private static final long RETRY_AFTER_MS = 30_000;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private final AtomicInteger fallbackCounter = new AtomicInteger(0);

    /** epoch-ms after which we can retry Redis. 0 = Redis assumed up. */
    private final AtomicLong retryAt = new AtomicLong(0);

    public int incrementAndGet() {
        if (canUseRedis()) {
            try {
                Long count = redisTemplate.opsForValue().increment(RICKROLL_KEY);
                if (count != null) return count.intValue();
            } catch (Exception e) {
                markRedisDown(e);
            }
        }
        return fallbackCounter.incrementAndGet();
    }

    public int getCount() {
        if (canUseRedis()) {
            try {
                String value = redisTemplate.opsForValue().get(RICKROLL_KEY);
                if (value == null) return 0;
                try {
                    return Integer.parseInt(value);
                } catch (NumberFormatException nfe) {
                    // Garbage in Redis — treat as zero and log. Do NOT throw:
                    // this endpoint is public, and a corrupt cache value
                    // should never surface as a 500.
                    logger.warn("Non-integer rickroll count in Redis: '{}'", value);
                    return 0;
                }
            } catch (Exception e) {
                markRedisDown(e);
            }
        }
        return fallbackCounter.get();
    }

    private boolean canUseRedis() {
        long retry = retryAt.get();
        return retry == 0 || System.currentTimeMillis() >= retry;
    }

    private void markRedisDown(Exception e) {
        long next = System.currentTimeMillis() + RETRY_AFTER_MS;
        retryAt.set(next);
        logger.warn("Redis unavailable — falling back to in-memory counter for {}s: {}",
            RETRY_AFTER_MS / 1000, e.getMessage());
    }
}

