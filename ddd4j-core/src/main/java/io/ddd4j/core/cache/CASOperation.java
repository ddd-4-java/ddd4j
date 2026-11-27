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
package io.ddd4j.core.cache;

/**
 * CAS 乐观并发操作回调（借鉴 XMemcached {@code CASOperation<T>}）。
 *
 * <p>业务方实现此接口来声明"如何基于当前值计算新值"：
 * <pre>{@code
 * // 分布式计数器（所有后端通用）
 * cache.compareAndSet("counter", 60, current -> {
 *     Long v = current.value();
 *     return (v instanceof Long value ? value : 0L) + 1;
 * });
 * }</pre>
 *
 * <h2>跨后端兼容性</h2>
 * <p>回调只使用 {@link GetsResponse#value()}——不依赖 {@link GetsResponse#version()}。
 * 这让所有缓存后端都能实现 CAS：
 * <ul>
 *   <li><b>Caffeine/Guava</b>：用 {@code ConcurrentMap.compute(K, BiFunction)} 原子回调</li>
 *   <li><b>Jedis/Lettuce</b>：用 Lua 脚本 {@code if redis.call("get")==old then set end}</li>
 *   <li><b>Redisson</b>：用 {@code RBucket.compareAndSet(old, new)}</li>
 *   <li><b>Memcached</b>：用 {@code cas(key, exp, value, casVersion)} 原生版本号 CAS</li>
 * </ul>
 *
 * @param <R> 操作返回类型
 * @param <V> 缓存值类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
@FunctionalInterface
public interface CASOperation<R, V> {

    /**
     * 基于当前值计算新值。
     *
     * @param current 当前值（{@link GetsResponse#value()} 可能为 null 表示首次写入）
     * @return 计算后的新值；返回 null 表示放弃 CAS（引擎不会重试）
     * @throws Exception 业务方可抛出异常表示放弃 CAS
     */
    R apply(GetsResponse<V> current) throws Exception;

    /**
     * CAS \u6700\u5927\u91cd\u8bd5\u6b21\u6570\u2014\u2014\u501f\u9274 Memcached {@code CASOperation.getMaxTries()}\u3002
     *
     *  <p>\u9ed8\u8ba4\u8fd4\u56de -1\uff08\u4f7f\u7528\u5f15\u64ce\u9ed8\u8ba4 16 \u6b21\uff09\u3002
     *  \u4e1a\u52a1\u65b9\u53ef\u91cd\u5199\u6b64\u65b9\u6cd5\u63a7\u5236\u91cd\u8bd5\u7b56\u7565\uff1a
     *  <ul>
     *    <li>1 = \u53ea\u5c1d\u8bd5\u4e00\u6b21\uff0c\u4e0d\u91cd\u8bd5</li>
     *    <li>-1 = \u4f7f\u7528\u5f15\u64ce\u9ed8\u8ba4\uff0816 \u6b21\uff09</li>
     *    <li>N = \u6700\u591a\u91cd\u8bd5 N \u6b21</li>
     *  </ul>
     *
     * @return \u6700\u5927\u91cd\u8bd5\u6b21\u6570\uff0c-1 \u8868\u793a\u4f7f\u7528\u5f15\u64ce\u9ed8\u8ba4\u503c
     */
    default int maxRetries() {
        return -1;
    }
}
