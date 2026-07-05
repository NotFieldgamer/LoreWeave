package com.loreweave.config;

import com.loreweave.config.RateLimiter.TokenBucket;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The token-bucket logic, tested deterministically with an injected clock (no sleeping, no Spring). */
class RateLimiterTest {

    // capacity 3 turns, refilled at 3 tokens / 60_000 ms (i.e. 3/min).
    private static final double CAP = 3, REFILL = 3 / 60_000.0;

    @Test
    void allowsABurstUpToCapacityThenBlocks() {
        TokenBucket b = new TokenBucket(CAP, REFILL, 0);
        assertThat(b.tryConsume(0)).isTrue();
        assertThat(b.tryConsume(0)).isTrue();
        assertThat(b.tryConsume(0)).isTrue();
        assertThat(b.tryConsume(0)).as("4th within the same instant is blocked").isFalse();
        assertThat(b.retryAfterSeconds(0)).as("1 token needs 20s at 3/min").isEqualTo(20);
    }

    @Test
    void refillsOverTime() {
        TokenBucket b = new TokenBucket(CAP, REFILL, 0);
        b.tryConsume(0); b.tryConsume(0); b.tryConsume(0);   // drain
        assertThat(b.tryConsume(19_000)).as("still short of one token at 19s").isFalse();
        assertThat(b.tryConsume(20_000)).as("one token back at 20s").isTrue();
        assertThat(b.tryConsume(20_000)).isFalse();
    }

    @Test
    void neverAccumulatesBeyondCapacity() {
        TokenBucket b = new TokenBucket(CAP, REFILL, 0);
        b.tryConsume(0); b.tryConsume(0); b.tryConsume(0);   // drain
        // idle far longer than it takes to refill — capacity is still the ceiling.
        assertThat(b.tryConsume(10_000_000)).isTrue();
        assertThat(b.tryConsume(10_000_000)).isTrue();
        assertThat(b.tryConsume(10_000_000)).isTrue();
        assertThat(b.tryConsume(10_000_000)).as("capped at capacity=3").isFalse();
    }
}
