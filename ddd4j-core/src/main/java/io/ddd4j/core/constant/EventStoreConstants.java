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
package io.ddd4j.core.constant;

/**
 * EventStore 持久化层共享常量。
 *
 * <p>跨 JPA / R2DBC / ESDB 三实现统一表名、列名与 ESDB 系统流前缀，
 * 避免魔法字面量散落在各运行时模块。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */
public final class EventStoreConstants {

    // ==================== 表名 ====================
    /**
     * 事件存储统一表名（JPA / R2DBC 共用）。
     */
    public static final String TABLE_NAME = "DDD4J_EVENT_STORE";

    // ==================== 列名 ====================
    /**
     * 聚合根标识列名。
     */
    public static final String COLUMN_AGGREGATE_ID = "aggregate_id";
    /**
     * 版本号列名。
     */
    public static final String COLUMN_VERSION = "version";
    /**
     * 全局位置列名。
     */
    public static final String COLUMN_POSITION = "position";
    /**
     * 事件类型列名。
     */
    public static final String COLUMN_EVENT_TYPE = "event_type";
    /**
     * 事件 ID 列名。
     */
    public static final String COLUMN_EVENT_ID = "event_id";
    /**
     * 事件载荷列名。
     */
    public static final String COLUMN_PAYLOAD = "payload";
    /**
     * 时间戳列名。
     */
    public static final String COLUMN_TIMESTAMP = "timestamp";

    // ==================== ESDB ====================
    /**
     * EventStoreDB 系统流前缀（以 {@code $} 开头的流 ID 为系统流）。
     */
    public static final String ESDB_SYSTEM_STREAM_PREFIX = "$";
    /**
     * ESDB readStream 默认最大读取条数。
     */
    public static final long ESDB_DEFAULT_READ_LIMIT = 4096L;

    private EventStoreConstants() {
    }
}
