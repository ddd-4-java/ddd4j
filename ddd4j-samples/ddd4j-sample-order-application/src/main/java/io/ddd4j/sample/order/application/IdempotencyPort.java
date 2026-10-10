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
package io.ddd4j.sample.order.application;

import java.time.Duration;

/**
 * 类型说明。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */
public interface IdempotencyPort {

    /**
     * 尝试获取指定幂等键的执行权（加锁）。
     *
     * @param key 幂等键
     * @param ttl 锁的存活时长
     * @return 获取成功返回 {@code true}，已被占用返回 {@code false}
     */
    boolean acquire(String key, Duration ttl);

    /**
     * 标记幂等键对应的处理已完成，并记录处理结果。
     *
     * @param key 幂等键
     * @param result 处理结果，可供后续幂等回放
     */
    void complete(String key, Object result);

    /**
     * 释放幂等键上的执行权（解锁），通常在处理失败时调用。
     *
     * @param key 幂等键
     */
    void release(String key);
}
