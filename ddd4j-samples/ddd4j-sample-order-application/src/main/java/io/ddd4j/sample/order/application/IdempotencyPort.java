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

/** 订单操作的幂等记录端口。 */
public interface IdempotencyPort {
    /**
     * @param key 幂等键
     * @param ttl 占用有效期
     * @return 是否成功占用该键
     */
    boolean acquire(String key, Duration ttl);

    /**
     * @param key 幂等键
     * @param result 操作结果
     */
    void complete(String key, Object result);

    /** @param key 待释放的幂等键 */
    void release(String key);
}
