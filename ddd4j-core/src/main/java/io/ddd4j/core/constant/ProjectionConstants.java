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
 * 投影（CQRS 读侧）共享常量。
 *
 * <p>跨 Spring / Quarkus / Guice 三运行时统一投影位置表名与列名，
 * 避免魔法字面量散落在各运行时模块。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */
public final class ProjectionConstants {

    // ==================== 表名 ====================
    /**
     * 投影位置统一表名（Spring / Quarkus / Guice 共用）。
     */
    public static final String TABLE_NAME = "DDD4J_PROJECTION_POSITION";

    // ==================== 列名 ====================
    /**
     * 事件流 ID 列名。
     */
    public static final String COLUMN_STREAM_ID = "stream_id";
    /**
     * 下一条待处理事件号列名。
     */
    public static final String COLUMN_NEXT_EVENT_NUMBER = "next_event_number";

    // ==================== 指标名称（Micrometer）====================
    /**
     * 已处理事件总数指标名（Micrometer Counter）。
     */
    public static final String METRIC_EVENTS_TOTAL = "projection.events.total";
    /**
     * 运行耗时指标名（Micrometer Timer）。
     */
    public static final String METRIC_RUN_DURATION = "projection.run.duration";
    /**
     * 运行失败次数指标名（Micrometer Counter）。
     */
    public static final String METRIC_ERRORS_TOTAL = "projection.errors.total";
    /**
     * Micrometer 指标标签名：投影视图流 ID。
     */
    public static final String TAG_STREAM = "stream";

    // ==================== 指标名称（OpenTelemetry）====================
    /**
     * 运行次数指标名（OTel LongCounter）。
     */
    public static final String OTel_METRIC_RUN_COUNT = "ddd4j.projection.run.count";
    /**
     * 事件计数指标名（OTel LongCounter）。
     */
    public static final String OTel_METRIC_EVENT_COUNT = "ddd4j.projection.event.count";
    /**
     * 运行耗时指标名（OTel DoubleHistogram）。
     */
    public static final String OTel_METRIC_RUN_DURATION = "ddd4j.projection.run.duration";
    /**
     * 运行错误指标名（OTel LongCounter）。
     */
    public static final String OTel_METRIC_RUN_ERROR = "ddd4j.projection.run.error";
    /**
     * OTel 指标属性 key：投影视图流 ID。
     */
    public static final String OTel_ATTR_STREAM_ID = "streamId";

    private ProjectionConstants() {
    }
}
