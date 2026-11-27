/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.web.core.idempotency;

import java.time.Duration;
import java.util.Optional;

/**
 * HTTP 幂等状态机。acquire 成功后必须调用 complete 或 release。
 */
public interface IdempotencyGuard {

    /**
     * \u5c1d\u8bd5\u83b7\u53d6\u5e42\u7b49\u952e\u3002
     * @param key \u5e42\u7b49\u952e
     * @param ttl \u6709\u6548\u671f
     * @return \u83b7\u53d6\u6210\u529f\u65f6\u4e3a true
     */
    boolean acquire(String key, Duration ttl);

    /**
     * \u83b7\u53d6\u5e26\u6240\u6709\u8005\u6807\u8bc6\u7684\u5e42\u7b49\u79df\u7ea6\u3002
     *
     *  <p>\u9ed8\u8ba4\u5b9e\u73b0\u4fdd\u7559\u65e7 Guard \u7684\u517c\u5bb9\u8bed\u4e49\uff1b\u751f\u4ea7 Guard \u5e94\u8986\u76d6\u6b64\u65b9\u6cd5\u5e76\u63d0\u4f9b\u552f\u4e00 owner token\u3002
     *
     * @param key \u5e42\u7b49\u952e
     * @param ttl \u6709\u6548\u671f
     * @return \u53ef\u7528\u7684\u5e42\u7b49\u79df\u7ea6
     */
    default Optional<IdempotencyLease> acquireLease(String key, Duration ttl) {
        return acquire(key, ttl) ? Optional.of(new IdempotencyLease(key, null, ttl)) : Optional.empty();
    }

    /**
     * \u6807\u8bb0\u6307\u5b9a\u5e42\u7b49\u952e\u7684\u4e1a\u52a1\u5904\u7406\u5df2\u5b8c\u6210\u3002
     * @param key \u5e42\u7b49\u952e
     */
    void complete(String key);

    /**
     * \u5b8c\u6210\u6307\u5b9a\u79df\u7ea6\u3002\u9ed8\u8ba4\u5b9e\u73b0\u517c\u5bb9\u65e7\u7684\u6309 key \u5b8c\u6210\u8bed\u4e49\u3002
     *
     * @param lease \u5e42\u7b49\u79df\u7ea6
     */
    default void complete(IdempotencyLease lease) {
        complete(lease.key());
    }

    /**
     * \u91ca\u653e\u6307\u5b9a\u5e42\u7b49\u952e\uff0c\u5141\u8bb8\u91cd\u8bd5\u3002
     * @param key \u5e42\u7b49\u952e
     */
    void release(String key);

    /**
     * \u91ca\u653e\u6307\u5b9a\u79df\u7ea6\u3002\u9ed8\u8ba4\u5b9e\u73b0\u517c\u5bb9\u65e7\u7684\u6309 key \u91ca\u653e\u8bed\u4e49\u3002
     *
     * @param lease \u5e42\u7b49\u79df\u7ea6
     */
    default void release(IdempotencyLease lease) {
        release(lease.key());
    }
}
