package com.sakshi.demo;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Runs before every request. Looks up which rule (if any) covers the request
 * path, asks that rule's limiter whether this client may proceed, and answers
 * 429 Too Many Requests right here if not, so the controller is never reached.
 *
 * It only knows the RateLimiter interface (through the registry), so it has
 * no idea which algorithm a rule uses. Paths with no matching rule, like
 * /health, pass straight through.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";

    private final RateLimitRegistry registry;

    public RateLimitFilter(RateLimitRegistry registry) {
        this.registry = registry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Optional<RateLimitRegistry.Entry> match = registry.find(request.getRequestURI());
        if (match.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }

        RateLimitRegistry.Entry entry = match.get();
        String clientKey = resolveClientKey(request);

        if (entry.limiter().allowRequest(clientKey)) {
            chain.doFilter(request, response);
            return;
        }

        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(entry.retryAfterSeconds()));
        response.setHeader("X-RateLimit-Rule", entry.rule().getName());
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"rate_limited\",\"message\":\"Too many requests, slow down.\"}");
    }

    /**
     * Who is this request from? Prefer an API key, fall back to the caller's IP.
     * (We deliberately do not read X-Forwarded-For here: any client can fake that
     * header, which would let them dodge the limit by changing it on every request.)
     */
    private String resolveClientKey(HttpServletRequest request) {
        String apiKey = request.getHeader(API_KEY_HEADER);
        if (apiKey != null && !apiKey.isBlank()) {
            return "key:" + apiKey;
        }
        return "ip:" + request.getRemoteAddr();
    }
}
