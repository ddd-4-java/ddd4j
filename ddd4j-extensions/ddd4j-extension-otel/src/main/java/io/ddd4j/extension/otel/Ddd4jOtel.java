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
package io.ddd4j.extension.otel;

import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.util.SubjectKit;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * DDD4J OpenTelemetry 集成入口点。
 *
 * <p>提供：
 * <ul>
 *   <li>统一的 {@link Tracer} 访问（fail-fast 检查 OpenTelemetry 是否就绪）</li>
 *   <li>Span 属性的标准化（tenant.id / user.id / login.id）</li>
 *   <li>从 {@link ThreadContext} 桥接业务上下文到 OTel Span</li>
 *   <li>无 OTel 可用时的 noop fallback（零依赖兼容）</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 4.0.0
 */
public final class Ddd4jOtel {

    /**
     * OTel Tracer 名称（统一前缀，便于在 Jaeger/Tempo 中过滤）。
     */
    public static final String TRACER_NAME = "io.ddd4j";

    /**
     * OTel Meter 名称。与 tracer 保持一致，便于按组件名统一筛选。
     */
    public static final String METER_NAME = TRACER_NAME;

    /**
     * 标准属性键。
     */
    public static final AttributeKey<String> ATTR_TENANT_ID = AttributeKey.stringKey("ddd4j.tenant.id");
    /** 用户标识属性键，记录当前操作用户 ID。 */
    public static final AttributeKey<String> ATTR_USER_ID = AttributeKey.stringKey("ddd4j.user.id");
    /** 登录标识属性键，记录当前会话的登录 ID。 */
    public static final AttributeKey<String> ATTR_LOGIN_ID = AttributeKey.stringKey("ddd4j.login.id");
    /** 用户编码属性键，记录当前操作用户的业务编码。 */
    public static final AttributeKey<String> ATTR_USER_CODE = AttributeKey.stringKey("ddd4j.user.code");
    /** 请求标识属性键，记录一次请求链路的唯一 ID。 */
    public static final AttributeKey<String> ATTR_REQUEST_ID = AttributeKey.stringKey("ddd4j.request.id");
    /** 领域事件类型属性键，记录事件的类型名称。 */
    public static final AttributeKey<String> ATTR_DOMAIN_EVENT_TYPE = AttributeKey.stringKey("ddd4j.event.type");
    /** 聚合类型属性键，记录操作涉及的聚合根类型。 */
    public static final AttributeKey<String> ATTR_AGGREGATE_TYPE = AttributeKey.stringKey("ddd4j.aggregate.type");
    /** 聚合标识属性键，记录操作涉及的聚合根 ID。 */
    public static final AttributeKey<String> ATTR_AGGREGATE_ID = AttributeKey.stringKey("ddd4j.aggregate.id");
    /** 查询类型属性键，记录查询操作的类型名称。 */
    public static final AttributeKey<String> ATTR_QUERY_TYPE = AttributeKey.stringKey("ddd4j.query.type");
    /** 投影名称属性键，记录投影处理的名称。 */
    public static final AttributeKey<String> ATTR_PROJECTION_NAME = AttributeKey.stringKey("ddd4j.projection.name");
    /** 缓存业务标识属性键，记录缓存所属的业务域。 */
    public static final AttributeKey<String> ATTR_CACHE_BIZ = AttributeKey.stringKey("ddd4j.cache.biz");
    /** 缓存后端标识属性键，记录所使用的缓存实现类型。 */
    public static final AttributeKey<String> ATTR_CACHE_BACKEND = AttributeKey.stringKey("ddd4j.cache.backend");
    /** 缓存命中标志属性键，标识本次缓存访问是否命中。 */
    public static final AttributeKey<Boolean> ATTR_CACHE_HIT = AttributeKey.booleanKey("ddd4j.cache.hit");
    /** 消息系统属性键，记录消息中间件系统名称。 */
    public static final AttributeKey<String> ATTR_MESSAGING_SYSTEM = AttributeKey.stringKey("messaging.system");
    /** 消息目标属性键，记录消息发送或消费的目的地（主题/队列）。 */
    public static final AttributeKey<String> ATTR_MESSAGING_DESTINATION = AttributeKey.stringKey("messaging.destination");
    /** 消息操作属性键，记录消息操作类型（send/receive 等）。 */
    public static final AttributeKey<String> ATTR_MESSAGING_OPERATION = AttributeKey.stringKey("messaging.operation");
    /** 数据库系统属性键，记录数据库系统名称。 */
    public static final AttributeKey<String> ATTR_DB_SYSTEM = AttributeKey.stringKey("db.system");
    /** 数据库语句属性键，记录执行的语句摘要。 */
    public static final AttributeKey<String> ATTR_DB_STATEMENT = AttributeKey.stringKey("db.statement");

    private static final AtomicReference<Tracer> TRACER_CACHE = new AtomicReference<>();
    private static final AtomicReference<Meter> METER_CACHE = new AtomicReference<>();
    private static volatile boolean available = false;

    private Ddd4jOtel() {
    }

    /**
     * 获取或懒加载 Tracer。
     *
     * @return OpenTelemetry Tracer（若 OTel 未就绪则返回 noop Tracer）
     */
    public static Tracer tracer() {
        Tracer cached = TRACER_CACHE.get();
        if (Objects.nonNull(cached)) {
            return cached;
        }
        try {
            OpenTelemetry otel = GlobalOpenTelemetry.get();
            if (Objects.isNull(otel) || otel == io.opentelemetry.api.OpenTelemetry.noop()) {
                available = false;
                return io.opentelemetry.api.OpenTelemetry.noop().getTracer(TRACER_NAME);
            }
            Tracer tracer = otel.getTracer(TRACER_NAME);
            TRACER_CACHE.set(tracer);
            available = true;
            return tracer;
        } catch (Throwable t) {
            available = false;
            return io.opentelemetry.api.OpenTelemetry.noop().getTracer(TRACER_NAME);
        }
    }

    /**
     * 获取或懒加载 Meter。
     *
     * <p>扩展只声明指标语义和记录点；导出器、Collector 及部署配置由应用负责提供。
     *
     * @return OpenTelemetry Meter（未配置 OTel 时为 noop Meter）
     */
    public static Meter meter() {
        Meter cached = METER_CACHE.get();
        if (Objects.nonNull(cached)) {
            return cached;
        }
        try {
            Meter meter = GlobalOpenTelemetry.get().getMeter(METER_NAME);
            METER_CACHE.compareAndSet(null, meter);
            return METER_CACHE.get();
        } catch (Throwable t) {
            Meter meter = OpenTelemetry.noop().getMeter(METER_NAME);
            METER_CACHE.compareAndSet(null, meter);
            return METER_CACHE.get();
        }
    }

    /**
     * 检查 OpenTelemetry 是否就绪（未就绪时所有集成方法为 noop）。
     *
     * @return true 如果 OTel 已配置
     */
    public static boolean isAvailable() {
        if (Objects.nonNull(TRACER_CACHE.get())) {
            return available;
        }
        tracer();
        return available;
    }

    /**
     * 从当前 Span 提取业务上下文属性（tenant.id / user.id / login.id）。
     *
     * <p>调用此方法前应已开启 Span（{@code Span.current()}）。无 OTel 时 noop。
     *
     * @param span 目标 Span
     */
    public static void enrichWithBusinessContext(Span span) {
        if (Objects.isNull(span) || !span.getSpanContext().isValid()) {
            return;
        }
        AttributesBuilderHelper builder = new AttributesBuilderHelper();
        builder.readFromThreadContext();
        builder.applyTo(span);
    }

    /**
     * 直接构造 Attributes（用于 spanBuilder 的 setAllAttributes）。
     *
     * @return 包含业务上下文的 Attributes
     */
    public static Attributes businessContextAttributes() {
        AttributesBuilderHelper builder = new AttributesBuilderHelper();
        builder.readFromThreadContext();
        return builder.build();
    }

    /**
     * ThreadContext 属性桥接辅助类。
     */
    static final class AttributesBuilderHelper {

        private final AttributesBuilder delegate = Attributes.builder();

        void readFromThreadContext() {
            String tenantId = ThreadContext.get(ContextConstants.TENANT_ID);
            if (Objects.nonNull(tenantId)) {
                delegate.put(ATTR_TENANT_ID, tenantId);
            }
            try {
                AuthPrincipal principal = SubjectKit.getPrincipal();
                if (Objects.nonNull(principal)) {
                    if (Objects.nonNull(principal.getUserId())) {
                        delegate.put(ATTR_USER_ID, String.valueOf(principal.getUserId()));
                    }
                    if (Objects.nonNull(principal.getLoginId())) {
                        delegate.put(ATTR_LOGIN_ID, String.valueOf(principal.getLoginId()));
                    }
                    if (Objects.nonNull(principal.getUserCode())) {
                        delegate.put(ATTR_USER_CODE, String.valueOf(principal.getUserCode()));
                    }
                }
            } catch (Exception ignored) {
                // SubjectKit 可能未注册，忽略
            }
        }

        Attributes build() {
            return delegate.build();
        }

        void applyTo(Span span) {
            Attributes attrs = build();
            attrs.forEach((key, value) -> span.setAttribute((AttributeKey) key, value));
        }
    }
}
