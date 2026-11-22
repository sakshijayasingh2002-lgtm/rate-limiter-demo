package com.sakshi.demo;

import com.sakshi.ratelimiter.core.RateLimiter;
import com.sakshi.ratelimiter.core.TimeSource;
import com.sakshi.ratelimiter.redis.RedisTokenBucketRateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * Wires the rate-limiter library into Spring: a Redis connection pool, the
 * limiter itself, and the filter that applies it to incoming requests.
 *
 * Day 9 keeps this simple: one algorithm (Token Bucket), one set of limits,
 * both read from application.yml. Day 10 makes the algorithm and limits
 * configurable per route.
 */
@Configuration
public class RateLimiterConfig {

    @Bean(destroyMethod = "close")
    public JedisPool jedisPool(@Value("${ratelimiter.redis.host}") String host,
                               @Value("${ratelimiter.redis.port}") int port) {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(32);
        return new JedisPool(poolConfig, host, port);
    }

    @Bean
    public RateLimiter rateLimiter(JedisPool jedisPool,
                                   @Value("${ratelimiter.capacity}") long capacity,
                                   @Value("${ratelimiter.refill-per-second}") double refillPerSecond) {
        return new RedisTokenBucketRateLimiter(
                jedisPool, capacity, refillPerSecond, TimeSource.systemTimeSource(), "rl:tb:");
    }

    @Bean
    public RateLimitFilter rateLimitFilter(RateLimiter rateLimiter,
                                           @Value("${ratelimiter.refill-per-second}") double refillPerSecond) {
        // A rejected client will have a token again after about 1/refillRate seconds.
        long retryAfterSeconds = Math.max(1L, (long) Math.ceil(1.0 / refillPerSecond));
        return new RateLimitFilter(rateLimiter, retryAfterSeconds);
    }
}
