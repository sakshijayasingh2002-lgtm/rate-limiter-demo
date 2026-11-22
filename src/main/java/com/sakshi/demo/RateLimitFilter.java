package com.sakshi.demo;

import com.sakshi.ratelimiter.core.RateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs before every /api/** request. Asks the rate limiter whether this
 * client may proceed; if not, answers 429 Too Many Requests right here and
 * the controller is never reached.
 *
 * Note this class only knows the RateLimiter interface. It has no idea
 * whether Token Bucket, Sliding Window or Leaky Bucket is behind it, or
 * whether state lives in memory or Redis.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";

    private final RateLimiter rateLimiter;
    private final long retryAfterSeconds;

    public RateLimitFilter(RateLimiter rateLimiter, long retryAfterSeconds) {
        this.rateLimiter = rateLimiter;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /** Only /api/** is rate-limited. Things like /health stay open. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String clientKey = resolveClientKey(request);

        if (rateLimiter.allowRequest(clientKey)) {
            chain.doFilter(request, response);
            return;
        }

        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
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
