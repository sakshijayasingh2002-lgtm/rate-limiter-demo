package com.sakshi.demo;

import com.sakshi.ratelimiter.core.RateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the filter on its own: no Spring context, no Redis. A tiny fake
 * RateLimiter stands in, so we only check what the FILTER does with the
 * limiter's answer.
 */
class RateLimitFilterTest {

    /** A registry with one rule covering /api/**, backed by the given fake limiter. */
    private static RateLimitFilter filterWith(RateLimiter limiter, long retryAfterSeconds) {
        RateLimitRule rule = new RateLimitRule();
        rule.setName("test-rule");
        rule.setPath("/api/**");
        RateLimitRegistry registry = new RateLimitRegistry(
                List.of(new RateLimitRegistry.Entry(rule, limiter, retryAfterSeconds)));
        return new RateLimitFilter(registry);
    }

    @Test
    void allowedRequestContinuesDownTheChain() throws Exception {
        RateLimitFilter filter = filterWith(key -> true, 1);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/hello");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest(), "controller should be reached");
        assertEquals(200, response.getStatus());
    }

    @Test
    void rejectedRequestGets429AndNeverReachesTheController() throws Exception {
        RateLimitFilter filter = filterWith(key -> false, 3);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/hello");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNull(chain.getRequest(), "controller must NOT be reached");
        assertEquals(429, response.getStatus());
        assertEquals("3", response.getHeader("Retry-After"));
        assertEquals("test-rule", response.getHeader("X-RateLimit-Rule"));
        assertTrue(response.getContentAsString().contains("rate_limited"));
        assertEquals("UTF-8", response.getCharacterEncoding(), "JSON should be sent as UTF-8");
    }

    @Test
    void pathsWithNoMatchingRuleAreNotRateLimited() throws Exception {
        // A limiter that rejects everything: /health matches no rule, so it must still get through.
        RateLimitFilter filter = filterWith(key -> false, 1);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
        assertEquals(200, response.getStatus());
    }

    @Test
    void eachRouteIsJudgedByItsOwnRule() throws Exception {
        RateLimitRule strict = new RateLimitRule();
        strict.setName("strict");
        strict.setPath("/api/report");
        RateLimitRule relaxed = new RateLimitRule();
        relaxed.setName("relaxed");
        relaxed.setPath("/api/**");

        RateLimitFilter filter = new RateLimitFilter(new RateLimitRegistry(List.of(
                new RateLimitRegistry.Entry(strict, key -> false, 15),   // rejects everything
                new RateLimitRegistry.Entry(relaxed, key -> true, 1)))); // allows everything

        MockHttpServletResponse reportResponse = new MockHttpServletResponse();
        MockFilterChain reportChain = new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/report"), reportResponse, reportChain);
        assertEquals(429, reportResponse.getStatus());
        assertEquals("15", reportResponse.getHeader("Retry-After"));
        assertNull(reportChain.getRequest());

        MockHttpServletResponse helloResponse = new MockHttpServletResponse();
        MockFilterChain helloChain = new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/hello"), helloResponse, helloChain);
        assertEquals(200, helloResponse.getStatus());
        assertNotNull(helloChain.getRequest());
    }

    @Test
    void clientsAreIdentifiedByApiKeyWhenPresent() throws Exception {
        List<String> seenKeys = new ArrayList<>();
        RateLimitFilter filter = filterWith(key -> {
            seenKeys.add(key);
            return true;
        }, 1);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/hello");
        request.addHeader("X-API-Key", "abc123");
        request.setRemoteAddr("10.0.0.9");
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertEquals(List.of("key:abc123"), seenKeys);
    }

    @Test
    void clientsFallBackToIpWhenNoApiKey() throws Exception {
        List<String> seenKeys = new ArrayList<>();
        RateLimitFilter filter = filterWith(key -> {
            seenKeys.add(key);
            return true;
        }, 1);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/hello");
        request.setRemoteAddr("10.0.0.9");
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertEquals(List.of("ip:10.0.0.9"), seenKeys);
    }

    @Test
    void spoofedForwardedForHeaderDoesNotChangeTheClientKey() throws Exception {
        List<String> seenKeys = new ArrayList<>();
        RateLimitFilter filter = filterWith(key -> {
            seenKeys.add(key);
            return true;
        }, 1);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/hello");
        request.setRemoteAddr("10.0.0.9");
        request.addHeader("X-Forwarded-For", "1.2.3.4"); // attacker-controlled
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertEquals(List.of("ip:10.0.0.9"), seenKeys);
    }
}
