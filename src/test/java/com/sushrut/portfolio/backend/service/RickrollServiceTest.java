package com.sushrut.portfolio.backend.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Rickroll graceful-degradation tests — the property under test is that a
 * Redis outage NEVER surfaces as a 500. The service falls back to an
 * in-memory counter and continues serving.
 */
@ExtendWith(MockitoExtension.class)
class RickrollServiceTest {

    @Mock private RedisTemplate<String, String> redis;
    @Mock private ValueOperations<String, String> ops;
    @InjectMocks private RickrollService service;

    @Test
    void increment_usesRedisWhenAvailable() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.increment(anyString())).thenReturn(42L);

        assertThat(service.incrementAndGet()).isEqualTo(42);
    }

    @Test
    void increment_fallsBackWhenRedisThrows() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.increment(anyString())).thenThrow(new RuntimeException("upstash gone"));

        // Should NOT throw, should return in-memory count (starts at 0, then 1)
        int first  = service.incrementAndGet();
        int second = service.incrementAndGet();

        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(2);
    }

    @Test
    void getCount_returnsZeroForMissingKey() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenReturn(null);
        assertThat(service.getCount()).isEqualTo(0);
    }

    @Test
    void getCount_handlesNonIntegerCorruption() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenReturn("💩");
        // Must not throw NumberFormatException up to controller
        assertThat(service.getCount()).isEqualTo(0);
    }

    @Test
    void getCount_fallsBackWhenRedisThrows() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenThrow(new RuntimeException("timeout"));
        // Fresh service → in-memory counter is 0
        assertThat(service.getCount()).isEqualTo(0);
    }
}
