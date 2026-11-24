package com.sakshi.demo;

import com.sakshi.ratelimiter.core.TimeSource;
import com.sakshi.ratelimiter.redis.RedisLeakyBucketRateLimiter;
import com.sakshi.ratelimiter.redis.RedisSlidingWindowCounterRateLimiter;
import com.sakshi.ratelimiter.redis.RedisSlidingWindowLogRateLimiter;
import com.sakshi.ratelimiter.redis.RedisTokenBucketRateLimiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.JedisPool;

import static org.junit.jupiter.api.Assertions.*;

/**
 * No Redis needed: creating a limiter doesn't connect to Redis (the pool
 * connects lazily), so we can check which class each algorithm name builds.
 */
class RateLimiterFactoryTest {

    private static final TimeSource CLOCK = () -> 0L;

    private JedisPool pool;

    @BeforeEach
    void setUp() {
        pool = new JedisPool("localhost", 6379);
    }

    @AfterEach
    void tearDown() {
        pool.close();
    }

    private static RateLimitRule rule(String algorithm) {
        RateLimitRule rule = new RateLimitRule();
        rule.setName("test");
        rule.setPath("/api/test");
        rule.setAlgorithm(algorithm);
        return rule;
    }

    @Test
    void buildsATokenBucket() {
        RateLimitRule rule = rule("token-bucket");
        rule.setCapacity(5L);
        rule.setRefillPerSecond(1.0);

        assertInstanceOf(RedisTokenBucketRateLimiter.class, RateLimiterFactory.create(rule, pool, CLOCK));
    }

    @Test
    void buildsALeakyBucket() {
        RateLimitRule rule = rule("leaky-bucket");
        rule.setCapacity(3L);
        rule.setLeakPerSecond(1.0);

        assertInstanceOf(RedisLeakyBucketRateLimiter.class, RateLimiterFactory.create(rule, pool, CLOCK));
    }

    @Test
    void buildsASlidingWindowCounter() {
        RateLimitRule rule = rule("sliding-window-counter");
        rule.setLimit(5);
        rule.setWindowSeconds(10L);

        assertInstanceOf(RedisSlidingWindowCounterRateLimiter.class, RateLimiterFactory.create(rule, pool, CLOCK));
    }

    @Test
    void buildsASlidingWindowLog() {
        RateLimitRule rule = rule("sliding-window-log");
        rule.setLimit(2);
        rule.setWindowSeconds(15L);

        assertInstanceOf(RedisSlidingWindowLogRateLimiter.class, RateLimiterFactory.create(rule, pool, CLOCK));
    }

    @Test
    void algorithmNameIsNotCaseSensitive() {
        RateLimitRule rule = rule("  Token-Bucket ");
        rule.setCapacity(5L);
        rule.setRefillPerSecond(1.0);

        assertInstanceOf(RedisTokenBucketRateLimiter.class, RateLimiterFactory.create(rule, pool, CLOCK));
    }

    @Test
    void unknownAlgorithmFailsWithAClearMessage() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> RateLimiterFactory.create(rule("fixed-window"), pool, CLOCK));

        assertTrue(e.getMessage().contains("unknown algorithm 'fixed-window'"));
        assertTrue(e.getMessage().contains("token-bucket"), "message should list the valid choices");
    }

    @Test
    void missingSettingNamesTheRuleAndTheSetting() {
        RateLimitRule rule = rule("token-bucket");
        rule.setCapacity(5L); // refill-per-second is missing

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> RateLimiterFactory.create(rule, pool, CLOCK));

        assertTrue(e.getMessage().contains("Rule 'test'"));
        assertTrue(e.getMessage().contains("refill-per-second"));
    }

    @Test
    void eachAlgorithmRejectsConfigMissingItsOwnSettings() {
        assertThrows(IllegalArgumentException.class, () -> RateLimiterFactory.create(rule("token-bucket"), pool, CLOCK));
        assertThrows(IllegalArgumentException.class, () -> RateLimiterFactory.create(rule("leaky-bucket"), pool, CLOCK));
        assertThrows(IllegalArgumentException.class, () -> RateLimiterFactory.create(rule("sliding-window-counter"), pool, CLOCK));
        assertThrows(IllegalArgumentException.class, () -> RateLimiterFactory.create(rule("sliding-window-log"), pool, CLOCK));
    }

    @Test
    void nameAndPathAreRequired() {
        RateLimitRule noName = rule("token-bucket");
        noName.setName(" ");
        assertThrows(IllegalArgumentException.class, () -> RateLimiterFactory.create(noName, pool, CLOCK));

        RateLimitRule noPath = rule("token-bucket");
        noPath.setPath(null);
        assertThrows(IllegalArgumentException.class, () -> RateLimiterFactory.create(noPath, pool, CLOCK));
    }

    @Test
    void retryAfterIsAboutOneSlotForBucketsAndTheWholeWindowForSlidingWindows() {
        RateLimitRule slowRefill = rule("token-bucket");
        slowRefill.setCapacity(5L);
        slowRefill.setRefillPerSecond(0.5); // a token every 2 seconds
        assertEquals(2, RateLimiterFactory.retryAfterSeconds(slowRefill));

        RateLimitRule fastRefill = rule("token-bucket");
        fastRefill.setCapacity(5L);
        fastRefill.setRefillPerSecond(5.0); // sub-second: never advertise less than 1
        assertEquals(1, RateLimiterFactory.retryAfterSeconds(fastRefill));

        RateLimitRule leaky = rule("leaky-bucket");
        leaky.setCapacity(3L);
        leaky.setLeakPerSecond(1.0);
        assertEquals(1, RateLimiterFactory.retryAfterSeconds(leaky));

        RateLimitRule window = rule("sliding-window-log");
        window.setLimit(2);
        window.setWindowSeconds(15L);
        assertEquals(15, RateLimiterFactory.retryAfterSeconds(window));
    }
}
