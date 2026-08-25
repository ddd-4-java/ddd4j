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
package io.ddd4j.data.jpa;

/**
 * JPA 持久化对象使用的纯 Java 雪花 ID 门面。
 *
 * <p>保留原有类名以兼容调用方，但不再实现 Hibernate
 * {@code IdentifierGenerator}，从而保持通用 JPA 模块的 provider-neutral。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 4.0.0
 */
public final class SnowflakeIdGenerator {

    private static final SnowflakeIdStrategy STRATEGY = new SnowflakeIdStrategy();

    private SnowflakeIdGenerator() {
    }

    public static long nextId() {
        return STRATEGY.generate();
    }
}
