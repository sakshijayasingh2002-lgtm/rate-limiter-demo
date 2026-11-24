package com.sakshi.demo;

import com.sakshi.ratelimiter.core.RateLimiter;
import org.springframework.util.AntPathMatcher;

import java.util.List;
import java.util.Optional;

/**
 * The ordered list of rules, each paired with its ready-to-use limiter.
 * Given a request path, finds the rule that applies.
 *
 * FIRST MATCH WINS, so put specific paths before general ones in the yml
 * (/api/search before /api/**). A path matching no rule is not limited.
 */
public class RateLimitRegistry {

    /** A rule, its limiter, and the Retry-After value to send when it rejects. */
    public record Entry(RateLimitRule rule, RateLimiter limiter, long retryAfterSeconds) {
    }

    private final List<Entry> entries;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public RateLimitRegistry(List<Entry> entries) {
        this.entries = List.copyOf(entries);
    }

    public Optional<Entry> find(String requestPath) {
        for (Entry entry : entries) {
            if (pathMatcher.match(entry.rule().getPath(), requestPath)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }
}
