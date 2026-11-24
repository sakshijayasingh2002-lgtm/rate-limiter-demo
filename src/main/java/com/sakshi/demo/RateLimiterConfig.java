package com.sakshi.demo;

import com.sakshi.ratelimiter.core.TimeSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Wires the rate-limiter library into Spring from application.yml:
 * a Redis connection pool, one limiter per configured rule, and the
 * filter that applies them.
 */
@Configuration
@EnableConfigurationProperties(RateLimiterProperties.class)
public class RateLimiterConfig {

    @Bean(destroyMethod = "close")
    public JedisPool jedisPool(RateLimiterProperties properties) {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(32);
        return new JedisPool(poolConfig, properties.getRedis().getHost(), properties.getRedis().getPort());
    }

    @Bean
    public RateLimitRegistry rateLimitRegistry(RateLimiterProperties properties, JedisPool jedisPool) {
        List<RateLimitRegistry.Entry> entries = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();

        for (RateLimitRule rule : properties.getRules()) {
            if (rule.getName() != null && !seenNames.add(rule.getName())) {
                throw new IllegalArgumentException("Duplicate rate-limit rule name: '" + rule.getName() + "'");
            }
            entries.add(new RateLimitRegistry.Entry(
                    rule,
                    RateLimiterFactory.create(rule, jedisPool, TimeSource.systemTimeSource()),
                    RateLimiterFactory.retryAfterSeconds(rule)));
        }
        return new RateLimitRegistry(entries);
    }

    @Bean
    public RateLimitFilter rateLimitFilter(RateLimitRegistry registry) {
        return new RateLimitFilter(registry);
    }
}
