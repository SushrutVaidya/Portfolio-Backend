package com.sushrut.portfolio.backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class RickrollService {
    private static final String RICKROLL_KEY = "rickroll:count";
    private static final Logger logger = LoggerFactory.getLogger(RickrollService.class);

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    // Fallback to in-memory storage if Redis is unavailable
    private final AtomicInteger fallbackCounter = new AtomicInteger(0);
    private boolean redisAvailable = true;

    public int incrementAndGet() {
        if (redisAvailable) {
            try {
                Long count = redisTemplate.opsForValue().increment(RICKROLL_KEY);
                logger.info("Rickroll count incremented to: {} (using Redis)", count);
                return count != null ? count.intValue() : 1;
            } catch (Exception e) {
                logger.warn("Redis unavailable, falling back to in-memory counter: {}", e.getMessage());
                redisAvailable = false;
            }
        }

        // Fallback to in-memory counter
        int count = fallbackCounter.incrementAndGet();
        logger.info("Rickroll count incremented to: {} (using in-memory)", count);
        return count;
    }

    public int getCount() {
        if (redisAvailable) {
            try {
                String value = redisTemplate.opsForValue().get(RICKROLL_KEY);
                int count = value != null ? Integer.parseInt(value) : 0;
                logger.info("Rickroll count retrieved: {} (using Redis)", count);
                return count;
            } catch (Exception e) {
                logger.warn("Redis unavailable, falling back to in-memory counter: {}", e.getMessage());
                redisAvailable = false;
            }
        }

        // Fallback to in-memory counter
        int count = fallbackCounter.get();
        logger.info("Rickroll count retrieved: {} (using in-memory)", count);
        return count;
    }
}
