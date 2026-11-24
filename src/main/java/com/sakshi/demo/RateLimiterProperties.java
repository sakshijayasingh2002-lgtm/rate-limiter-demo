package com.sakshi.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/** Everything under "ratelimiter:" in application.yml. */
@ConfigurationProperties(prefix = "ratelimiter")
public class RateLimiterProperties {

    private Redis redis = new Redis();
    private List<RateLimitRule> rules = new ArrayList<>();

    public Redis getRedis() { return redis; }
    public void setRedis(Redis redis) { this.redis = redis; }

    public List<RateLimitRule> getRules() { return rules; }
    public void setRules(List<RateLimitRule> rules) { this.rules = rules; }

    public static class Redis {
        private String host = "localhost";
        private int port = 6379;

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }

        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
    }
}
