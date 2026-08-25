package com.sushrut.portfolio.backend.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis client wiring.
 *
 * Two hard requirements:
 * 1. Every Redis operation must have a bounded timeout — a hung Redis
 *    (Upstash outage, network partition) should NOT block Tomcat worker
 *    threads indefinitely. The 2s command timeout below caps it.
 * 2. On failure the app must degrade gracefully — every service using
 *    Redis wraps calls in try/catch and falls back to an in-memory
 *    counter. See {@link RickrollService} for the pattern.
 *
 * SSL peer verification is disabled because Upstash uses a hosted CA
 * that trips Java's default trust store. For a portfolio-tier app the
 * cost of a MITM here is acceptable (only a rickroll counter goes over
 * this connection). Do NOT copy this to any auth-carrying Redis.
 */
@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.data.redis.ssl.enabled:false}")
    private boolean redisSslEnabled;

    @Value("${spring.data.redis.timeout:2s}")
    private Duration commandTimeout;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(redisHost, redisPort);

        if (redisPassword != null && !redisPassword.isEmpty()) {
            config.setPassword(redisPassword);
        }

        LettuceClientConfiguration.LettuceClientConfigurationBuilder clientConfig =
            LettuceClientConfiguration.builder()
                // Applies to every SYNC / REACTIVE command. Async commands still
                // need explicit timeouts, but we don't use those here.
                .commandTimeout(commandTimeout)
                // Connect handshake cap — separate from command timeout above.
                .shutdownTimeout(Duration.ofSeconds(1));

        if (redisSslEnabled) {
            clientConfig.useSsl().disablePeerVerification();
        }

        return new LettuceConnectionFactory(config, clientConfig.build());
    }

    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        template.setEnableTransactionSupport(false);
        return template;
    }
}

