package com.sakshi.demo;

/**
 * One rate-limiting rule, as written in application.yml:
 * which URL pattern it covers, which algorithm to use, and the limits.
 *
 * Which limit fields apply depends on the algorithm:
 *   token-bucket            capacity, refill-per-second
 *   leaky-bucket            capacity, leak-per-second
 *   sliding-window-counter  limit, window-seconds
 *   sliding-window-log      limit, window-seconds
 *
 * Unused fields are simply left out of the yml. Missing ones that ARE needed
 * are caught at start-up by {@link RateLimiterFactory}, with a clear message.
 */
public class RateLimitRule {

    private String name;
    private String path;
    private String algorithm;

    private Long capacity;
    private Double refillPerSecond;
    private Double leakPerSecond;
    private Integer limit;
    private Long windowSeconds;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getAlgorithm() { return algorithm; }
    public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }

    public Long getCapacity() { return capacity; }
    public void setCapacity(Long capacity) { this.capacity = capacity; }

    public Double getRefillPerSecond() { return refillPerSecond; }
    public void setRefillPerSecond(Double refillPerSecond) { this.refillPerSecond = refillPerSecond; }

    public Double getLeakPerSecond() { return leakPerSecond; }
    public void setLeakPerSecond(Double leakPerSecond) { this.leakPerSecond = leakPerSecond; }

    public Integer getLimit() { return limit; }
    public void setLimit(Integer limit) { this.limit = limit; }

    public Long getWindowSeconds() { return windowSeconds; }
    public void setWindowSeconds(Long windowSeconds) { this.windowSeconds = windowSeconds; }
}
