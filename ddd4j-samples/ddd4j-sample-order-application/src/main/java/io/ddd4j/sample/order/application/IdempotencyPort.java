package io.ddd4j.sample.order.application;

import java.time.Duration;

public interface IdempotencyPort {
    boolean acquire(String key, Duration ttl);
    void complete(String key, Object result);
    void release(String key);
}
