package io.ddd4j.web.core;

import java.time.Duration;

/**
 * HTTP 幂等状态机。acquire 成功后必须调用 complete 或 release。
 */
public interface IdempotencyGuard {

    boolean acquire(String key, Duration ttl);

    void complete(String key);

    void release(String key);
}
