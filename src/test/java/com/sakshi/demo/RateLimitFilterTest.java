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

    @Test
    void allowedRequestContinuesDownTheChain() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(key -> true, 1);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/hello");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest(), "controller should be reached");
        assertEquals(200, response.getStatus());
    }

    @Test
    void rejectedRequestGets429AndNeverReachesTheController() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(key -> false, 3);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/hello");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNull(chain.getRequest(), "controller must NOT be reached");
        assertEquals(429, response.getStatus());
        assertEquals("3", response.getHeader("Retry-After"));
        assertTrue(response.getContentAsString().contains("rate_limited"));
    }

    @Test
    void nonApiPathsAreNotRateLimited() throws Exception {
        // A limiter that rejects everything: /health must still get through.
        RateLimitFilter filter = new RateLimitFilter(key -> false, 1);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
        assertEquals(200, response.getStatus());
    }

    @Test
    void clientsAreIdentifiedByApiKeyWhenPresent() throws Exception {
        List<String> seenKeys = new ArrayList<>();
        RateLimiter recording = key -> {
            seenKeys.add(key);
            return true;
        };
        RateLimitFilter filter = new RateLimitFilter(recording, 1);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/hello");
        request.addHeader("X-API-Key", "abc123");
        request.setRemoteAddr("10.0.0.9");
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertEquals(List.of("key:abc123"), seenKeys);
    }

    @Test
    void clientsFallBackToIpWhenNoApiKey() throws Exception {
        List<String> seenKeys = new ArrayList<>();
        RateLimiter recording = key -> {
            seenKeys.add(key);
            return true;
        };
        RateLimitFilter filter = new RateLimitFilter(recording, 1);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/hello");
        request.setRemoteAddr("10.0.0.9");
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertEquals(List.of("ip:10.0.0.9"), seenKeys);
    }

    @Test
    void spoofedForwardedForHeaderDoesNotChangeTheClientKey() throws Exception {
        List<String> seenKeys = new ArrayList<>();
        RateLimiter recording = key -> {
            seenKeys.add(key);
            return true;
        };
        RateLimitFilter filter = new RateLimitFilter(recording, 1);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/hello");
        request.setRemoteAddr("10.0.0.9");
        request.addHeader("X-Forwarded-For", "1.2.3.4"); // attacker-controlled
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertEquals(List.of("ip:10.0.0.9"), seenKeys);
    }
}
