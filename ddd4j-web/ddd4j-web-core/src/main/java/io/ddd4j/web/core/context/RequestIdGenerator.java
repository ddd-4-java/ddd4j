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
package io.ddd4j.web.core.context;

import java.util.UUID;

/**
 * 为缺失请求标识的请求生成服务端标识。
 */
@FunctionalInterface
public interface RequestIdGenerator {

    String generate();

    static RequestIdGenerator uuid() {
        // 预热 SecureRandom 熵源播种：JVM 内首次 UUID.randomUUID() 会触发 SeederHolder
        // 类初始化，Windows 下需枚举网卡收集熵，实测（JDK 8）可达 8 秒以上；若落在
        // 首个请求的上下文创建上，将击穿网关/测试客户端的响应超时（WebTestClient 默认 5s）。
        // 在生成器创建期（Bean 装配/测试上下文启动）完成播种，此后 generate() 零额外开销。
        UUID.randomUUID();
        return () -> UUID.randomUUID().toString();
    }
}
