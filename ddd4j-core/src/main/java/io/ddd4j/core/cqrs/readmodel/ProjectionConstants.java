package io.ddd4j.core.cqrs.readmodel;

/**
 * 投影模块常量（回填自 3.0.x 21cb5bf5，Java 8 基线适配）。
 *
 * <p>表名/列名供关系型投影位置存储使用；指标名供 Micrometer 适配器共用。
 */
public final class ProjectionConstants {
    // ==================== 表名 ====================
    /** 投影位置统一表名。 */
    public static final String TABLE_NAME = "DDD4J_PROJECTION_POSITION";
    // ==================== 列名 ====================
    /** 事件流 ID 列名。 */
    public static final String COLUMN_STREAM_ID = "stream_id";
    /** 下一条待处理事件号列名。 */
    public static final String COLUMN_NEXT_EVENT_NUMBER = "next_event_number";
    // ==================== 指标名称（Micrometer）====================
    /** 已处理事件总数指标名。 */
    public static final String METRIC_EVENTS_TOTAL = "projection.events.total";
    /** 运行耗时指标名。 */
    public static final String METRIC_RUN_DURATION = "projection.run.duration";
    /** 错误总数指标名。 */
    public static final String METRIC_ERRORS_TOTAL = "projection.errors.total";
    /** 流 ID 标签名。 */
    public static final String TAG_STREAM = "stream";
    private ProjectionConstants() {
    }
}
