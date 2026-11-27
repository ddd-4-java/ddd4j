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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Duration;
import java.util.Objects;

/**
 * 一次幂等请求获取到的租约。
 *
 * <p>ownerToken 保证过期请求不能完成或释放后续请求重新获取的同一个幂等键。
 * 调用方只应将实例交回创建它的 {@link IdempotencyGuard}。
 */
public final class IdempotencyLease {

    /** \u6807\u8bc6\u5f53\u524d\u5e42\u7b49\u8bb0\u5f55\u7684\u4e3b\u952e\u3002 */
    private final String key;
    /** \u533a\u5206\u5f53\u524d\u5e42\u7b49\u79df\u7ea6\u4e0e\u5176\u4ed6\u8bf7\u6c42\u6301\u6709\u6743\u7684\u4ee4\u724c\u3002 */
    private final String ownerToken;
    /** \u5e42\u7b49\u8bb0\u5f55\u4fdd\u7559\u7684\u65f6\u957f\u3002 */
    private final Duration ttl;

    /**
     * 创建幂等租约。
     *
     * @param key 存储键
     * @param ownerToken 此次获取的唯一所有者标识
     * @param ttl 租约有效期
     */
    @JsonCreator
    public IdempotencyLease(@JsonProperty("key") String key,
                            @JsonProperty("ownerToken") String ownerToken,
                            @JsonProperty("ttl") Duration ttl) {
        this.key = Objects.requireNonNull(key, "key must not be null");
        this.ownerToken = ownerToken;
        this.ttl = Objects.requireNonNull(ttl, "ttl must not be null");
    }

    /**
     * 返回存储键。
     *
     * @return 存储键
     */
    @JsonProperty("key")
    public String key() {
        return key;
    }

    /**
     * 返回租约所有者令牌。
     *
     * @return 所有者令牌
     */
    @JsonProperty("ownerToken")
    public String ownerToken() {
        return ownerToken;
    }

    /**
     * 返回租约有效期。
     *
     * @return 有效期
     */
    @JsonProperty("ttl")
    public Duration ttl() {
        return ttl;
    }

    /**
     * 返回存储键。
     *
     * @return 存储键
     */
    public String getKey() {
        return key;
    }

    /**
     * 返回租约所有者令牌。
     *
     * @return 所有者令牌
     */
    public String getOwnerToken() {
        return ownerToken;
    }

    /**
     * 返回租约有效期。
     *
     * @return 有效期
     */
    public Duration getTtl() {
        return ttl;
    }

    /**
     * 根据<code>key</code>、<code>ownerToken</code>、<code>ttl</code>比较对象的值是否相等。
     *
     * <p>只比较运行时类型相同的对象。</p>
     *
     * @param obj 用来比较或转换的输入对象
     * @return 对象满足此实现的相等条件时为真
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (Objects.isNull(obj) || getClass() != obj.getClass()) {
            return false;
        }
        IdempotencyLease other = (IdempotencyLease) obj;
        return Objects.equals(key, other.key)
                && Objects.equals(ownerToken, other.ownerToken)
                && Objects.equals(ttl, other.ttl);
    }

    /**
     * 计算与对象相等条件一致的哈希值。
     *
     * @return 由参与相等比较的状态计算出的哈希值
     */
    @Override
    public int hashCode() {
        int result = Objects.hashCode(key);
        result = 31 * result + Objects.hashCode(ownerToken);
        result = 31 * result + Objects.hashCode(ttl);
        return result;
    }

    /**
     * 生成用于调试和日志的对象描述，包含<code>key</code>、<code>ownerToken</code>、<code>ttl</code>。
     *
     * @return 表示当前对象状态的字符串
     */
    @Override
    public String toString() {
        return "IdempotencyLease[key=" + key + ", ownerToken=" + ownerToken + ", ttl=" + ttl + "]";
    }
}
