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
import java.util.Objects;

/**
 * 一次幂等请求获取到的租约。
 *
 * <p>ownerToken 用于保证过期请求不能完成或释放后续请求重新获取的同一个幂等键。
 * 调用方只应将实例交回创建它的 {@link IdempotencyGuard}。
 *
 * @param key        存储键
 * @param ownerToken 此次获取的唯一所有者标识
 * @param ttl        租约有效期
 */public final class IdempotencyLease {

    private final String key;
    private final String ownerToken;
    private final Duration ttl;

    public IdempotencyLease(String key, String ownerToken, Duration ttl) {
        this.key = Objects.requireNonNull(key, "key must not be null");
        this.ownerToken = ownerToken;
        this.ttl = Objects.requireNonNull(ttl, "ttl must not be null");
    }

    public String key() { return key; }
    public String ownerToken() { return ownerToken; }
    public Duration ttl() { return ttl; }

    public String getKey() {
        return key;
    }

    public String getOwnerToken() {
        return ownerToken;
    }

    public Duration getTtl() {
        return ttl;
    }
}