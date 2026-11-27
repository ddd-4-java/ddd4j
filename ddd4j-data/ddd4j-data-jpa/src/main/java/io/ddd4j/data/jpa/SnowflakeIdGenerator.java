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

    /** \u4e3a JPA \u6807\u8bc6\u751f\u6210\u5668\u63d0\u4f9b Snowflake \u6807\u8bc6\u7684\u7b56\u7565\u3002 */
    private static final SnowflakeIdStrategy STRATEGY = new SnowflakeIdStrategy();

    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>SnowflakeIdGenerator</code> \u5b9e\u4f8b\u3002 */
    private SnowflakeIdGenerator() {
    }

    /**
     * \u751f\u6210\u4e0b\u4e00\u4e2a\u96ea\u82b1\u6807\u8bc6\u3002
     * @return \u96ea\u82b1\u6807\u8bc6
     */
    public static long nextId() {
        return STRATEGY.generate();
    }
}
