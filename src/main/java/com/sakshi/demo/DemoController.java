package com.sakshi.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class DemoController {

    private final int port;

    public DemoController(@Value("${server.port}") int port) {
        this.port = port;
    }

    /** Rate-limited. "servedBy" shows which instance answered (useful on Day 11 with several instances). */
    @GetMapping("/api/hello")
    public Map<String, Object> hello() {
        return Map.of(
                "message", "Hello!",
                "servedBy", port,
                "time", Instant.now().toString());
    }

    /** Not rate-limited (outside /api/). */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
