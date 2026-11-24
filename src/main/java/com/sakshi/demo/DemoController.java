package com.sakshi.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
public class DemoController {

    private final String instanceName;

    /**
     * Names this instance in responses ("servedBy"). Docker Compose sets INSTANCE_NAME
     * for each container; running locally it falls back to "instance-<port>".
     */
    public DemoController(@Value("${instance.name:instance-${server.port}}") String instanceName) {
        this.instanceName = instanceName;
    }

    /** Token Bucket rule: allows a small burst, then a steady trickle. */
    @GetMapping("/api/hello")
    public Map<String, Object> hello() {
        return Map.of(
                "message", "Hello!",
                "servedBy", instanceName,
                "time", Instant.now().toString());
    }

    /** Sliding Window Counter rule: N requests per rolling window. */
    @GetMapping("/api/search")
    public Map<String, Object> search(@RequestParam(defaultValue = "") String q) {
        return Map.of(
                "query", q,
                "results", List.of(),
                "servedBy", instanceName);
    }

    /** Leaky Bucket rule: smooths traffic to a steady rate. */
    @GetMapping("/api/orders")
    public Map<String, Object> orders() {
        return Map.of(
                "orders", List.of(),
                "servedBy", instanceName);
    }

    /** Sliding Window Log rule: strict, exact count. Good for expensive operations. */
    @GetMapping("/api/report")
    public Map<String, Object> report() {
        return Map.of(
                "report", "expensive report generated",
                "servedBy", instanceName,
                "time", Instant.now().toString());
    }

    /** Matches no rule: never rate-limited. */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
