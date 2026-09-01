package io.ddd4j.core.cqrs.eventstore;

/** 关系型事件存储统一 schema 常量（JPA / JDBI 适配器共用）。 */
public final class EventStoreConstants {

    /** 统一表名。 */
    public static final String TABLE_NAME = "DDD4J_EVENT_STORE";

    public static final String COLUMN_AGGREGATE_TYPE = "aggregate_type";
    public static final String COLUMN_AGGREGATE_ID = "aggregate_id";
    public static final String COLUMN_VERSION = "version";
    public static final String COLUMN_POSITION = "position";
    public static final String COLUMN_EVENT_TYPE = "event_type";
    public static final String COLUMN_EVENT_ID = "event_id";
    public static final String COLUMN_CORRELATION_ID = "correlation_id";
    public static final String COLUMN_CAUSATION_ID = "causation_id";
    /** 事件载荷（JSON 文本，跨方言 TEXT 类型，与 2.0.x/3.0.x 对齐）。 */
    public static final String COLUMN_PAYLOAD = "payload";
    public static final String COLUMN_TIMESTAMP = "timestamp";

    private EventStoreConstants() {
    }
}
