package com.sakshi.demo;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitRegistryTest {

    private static RateLimitRegistry.Entry entry(String name, String path) {
        RateLimitRule rule = new RateLimitRule();
        rule.setName(name);
        rule.setPath(path);
        return new RateLimitRegistry.Entry(rule, key -> true, 1);
    }

    @Test
    void exactPathMatches() {
        RateLimitRegistry registry = new RateLimitRegistry(List.of(entry("hello", "/api/hello")));

        assertEquals("hello", registry.find("/api/hello").orElseThrow().rule().getName());
        assertTrue(registry.find("/api/other").isEmpty());
    }

    @Test
    void wildcardMatchesEverythingUnderThePrefix() {
        RateLimitRegistry registry = new RateLimitRegistry(List.of(entry("all-api", "/api/**")));

        assertTrue(registry.find("/api/hello").isPresent());
        assertTrue(registry.find("/api/orders/42").isPresent());
        assertTrue(registry.find("/health").isEmpty());
    }

    @Test
    void firstMatchWins_soSpecificRulesMustComeBeforeGeneralOnes() {
        RateLimitRegistry specificFirst = new RateLimitRegistry(List.of(
                entry("search", "/api/search"),
                entry("default", "/api/**")));
        assertEquals("search", specificFirst.find("/api/search").orElseThrow().rule().getName());
        assertEquals("default", specificFirst.find("/api/hello").orElseThrow().rule().getName());

        // Same rules, wrong order: the catch-all swallows everything, search is never reached.
        RateLimitRegistry generalFirst = new RateLimitRegistry(List.of(
                entry("default", "/api/**"),
                entry("search", "/api/search")));
        assertEquals("default", generalFirst.find("/api/search").orElseThrow().rule().getName());
    }

    @Test
    void noRulesMeansNothingIsLimited() {
        RateLimitRegistry registry = new RateLimitRegistry(List.of());

        assertTrue(registry.find("/api/hello").isEmpty());
    }
}
