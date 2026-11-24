package com.sakshi.demo;

import com.sakshi.ratelimiter.core.RateLimiter;
import com.sakshi.ratelimiter.core.TimeSource;
import com.sakshi.ratelimiter.redis.RedisLeakyBucketRateLimiter;
import com.sakshi.ratelimiter.redis.RedisSlidingWindowCounterRateLimiter;
import com.sakshi.ratelimiter.redis.RedisSlidingWindowLogRateLimiter;
import com.sakshi.ratelimiter.redis.RedisTokenBucketRateLimiter;
import redis.clients.jedis.JedisPool;

/**
 * Turns a {@link RateLimitRule} from the config into a real RateLimiter.
 *
 * This is the one place that knows the algorithm names ("token-bucket", ...)
 * and which settings each one needs. Everything else (the filter, the
 * registry) only ever sees the RateLimiter interface.
 *
 * Bad config fails at start-up with a message saying exactly which rule and
 * which setting is wrong, rather than surfacing later as a confusing error
 * on the first request.
 */
public final class RateLimiterFactory {

    private RateLimiterFactory() {
    }

    public static RateLimiter create(RateLimitRule rule, JedisPool pool, TimeSource clock) {
        validateBasics(rule);

        // Each rule gets its own key prefix, so one client's counters for
        // /api/hello never mix with its counters for /api/search.
        String prefix = "rl:" + rule.getName() + ":";

        switch (algorithmOf(rule)) {
            case "token-bucket":
                return new RedisTokenBucketRateLimiter(pool,
                        require(rule, "capacity", rule.getCapacity()),
                        require(rule, "refill-per-second", rule.getRefillPerSecond()),
                        clock, prefix);
            case "leaky-bucket":
                return new RedisLeakyBucketRateLimiter(pool,
                        require(rule, "capacity", rule.getCapacity()),
                        require(rule, "leak-per-second", rule.getLeakPerSecond()),
                        clock, prefix);
            case "sliding-window-counter":
                return new RedisSlidingWindowCounterRateLimiter(pool,
                        require(rule, "limit", rule.getLimit()),
                        require(rule, "window-seconds", rule.getWindowSeconds()) * 1000L,
                        clock, prefix);
            case "sliding-window-log":
                return new RedisSlidingWindowLogRateLimiter(pool,
                        require(rule, "limit", rule.getLimit()),
                        require(rule, "window-seconds", rule.getWindowSeconds()) * 1000L,
                        clock, prefix);
            default:
                throw new IllegalArgumentException("Rule '" + rule.getName() + "': unknown algorithm '"
                        + rule.getAlgorithm() + "'. Use one of: token-bucket, leaky-bucket, "
                        + "sliding-window-counter, sliding-window-log");
        }
    }

    /**
     * Seconds a rejected client should wait before retrying (for the Retry-After header).
     * Bucket algorithms: about one slot's worth of time. Sliding windows: the whole
     * window, which is the safe upper bound (the oldest request leaves within one window).
     */
    public static long retryAfterSeconds(RateLimitRule rule) {
        validateBasics(rule);
        switch (algorithmOf(rule)) {
            case "token-bucket":
                return Math.max(1L, (long) Math.ceil(1.0 / require(rule, "refill-per-second", rule.getRefillPerSecond())));
            case "leaky-bucket":
                return Math.max(1L, (long) Math.ceil(1.0 / require(rule, "leak-per-second", rule.getLeakPerSecond())));
            case "sliding-window-counter":
            case "sliding-window-log":
                return Math.max(1L, require(rule, "window-seconds", rule.getWindowSeconds()));
            default:
                throw new IllegalArgumentException("Rule '" + rule.getName() + "': unknown algorithm '"
                        + rule.getAlgorithm() + "'");
        }
    }

    private static void validateBasics(RateLimitRule rule) {
        if (rule.getName() == null || rule.getName().isBlank()) {
            throw new IllegalArgumentException("Every rate-limit rule needs a 'name'");
        }
        if (rule.getPath() == null || rule.getPath().isBlank()) {
            throw new IllegalArgumentException("Rule '" + rule.getName() + "': 'path' is required");
        }
        if (rule.getAlgorithm() == null || rule.getAlgorithm().isBlank()) {
            throw new IllegalArgumentException("Rule '" + rule.getName() + "': 'algorithm' is required");
        }
    }

    private static String algorithmOf(RateLimitRule rule) {
        return rule.getAlgorithm().trim().toLowerCase();
    }

    private static <T> T require(RateLimitRule rule, String setting, T value) {
        if (value == null) {
            throw new IllegalArgumentException("Rule '" + rule.getName() + "': '" + setting
                    + "' is required for algorithm '" + rule.getAlgorithm() + "'");
        }
        return value;
    }
}
