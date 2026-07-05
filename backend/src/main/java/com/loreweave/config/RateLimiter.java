package com.loreweave.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * A tiny per-key token-bucket rate limiter (no external dependency). Each turn spends scarce Gemini
 * free-tier budget (see the quota notes), so we cap how fast one signed-in user can take turns —
 * protecting both the quota and the backend from a runaway client.
 */
@Component
public class RateLimiter {

    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    /** Sustained turns per minute per user; also the burst capacity. */
    @Value("${loreweave.ratelimit.turns-per-minute:20}")
    private int turnsPerMinute;

    @Value("${loreweave.ratelimit.enabled:true}")
    private boolean enabled;

    /** Try to spend one token for {@code key}; false means rate-limited. */
    public boolean allow(String key) {
        if (!enabled) return true;
        return bucket(key).tryConsume(System.currentTimeMillis());
    }

    /** Whole seconds until the next token is available (for the Retry-After header). */
    public long retryAfterSeconds(String key) {
        return bucket(key).retryAfterSeconds(System.currentTimeMillis());
    }

    private TokenBucket bucket(String key) {
        return buckets.computeIfAbsent(key,
            k -> new TokenBucket(turnsPerMinute, turnsPerMinute / 60_000.0, System.currentTimeMillis()));
    }

    /**
     * A classic token bucket. {@code capacity} tokens, refilled at {@code refillPerMs} tokens/ms; the
     * clock is passed in so it can be tested deterministically without sleeping.
     */
    static final class TokenBucket {
        private final double capacity;
        private final double refillPerMs;
        private double tokens;
        private long lastMs;

        TokenBucket(double capacity, double refillPerMs, long nowMs) {
            this.capacity = capacity;
            this.refillPerMs = refillPerMs;
            this.tokens = capacity;
            this.lastMs = nowMs;
        }

        synchronized boolean tryConsume(long nowMs) {
            refill(nowMs);
            if (tokens >= 1.0) { tokens -= 1.0; return true; }
            return false;
        }

        synchronized long retryAfterSeconds(long nowMs) {
            refill(nowMs);
            if (tokens >= 1.0) return 0;
            double needed = 1.0 - tokens;
            return (long) Math.ceil(needed / refillPerMs / 1000.0);
        }

        private void refill(long nowMs) {
            if (nowMs > lastMs) {
                tokens = Math.min(capacity, tokens + (nowMs - lastMs) * refillPerMs);
                lastMs = nowMs;
            }
        }
    }
}
