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
    /** Span \u5c5e\u6027\u952e {@code ddd4j.user.id}\u3002 */
    public static final AttributeKey<String> ATTR_USER_ID = AttributeKey.stringKey("ddd4j.user.id");
    /** Span \u5c5e\u6027\u952e {@code ddd4j.login.id}\u3002 */
    public static final AttributeKey<String> ATTR_LOGIN_ID = AttributeKey.stringKey("ddd4j.login.id");
    /** Span \u5c5e\u6027\u952e {@code ddd4j.user.code}\u3002 */
    public static final AttributeKey<String> ATTR_USER_CODE = AttributeKey.stringKey("ddd4j.user.code");
    /** Span \u5c5e\u6027\u952e {@code ddd4j.request.id}\u3002 */
    public static final AttributeKey<String> ATTR_REQUEST_ID = AttributeKey.stringKey("ddd4j.request.id");
    /** Span \u5c5e\u6027\u952e {@code ddd4j.event.type}\u3002 */
    public static final AttributeKey<String> ATTR_DOMAIN_EVENT_TYPE = AttributeKey.stringKey("ddd4j.event.type");
    /** Span \u5c5e\u6027\u952e {@code ddd4j.aggregate.type}\u3002 */
    public static final AttributeKey<String> ATTR_AGGREGATE_TYPE = AttributeKey.stringKey("ddd4j.aggregate.type");
    /** Span \u5c5e\u6027\u952e {@code ddd4j.aggregate.id}\u3002 */
    public static final AttributeKey<String> ATTR_AGGREGATE_ID = AttributeKey.stringKey("ddd4j.aggregate.id");
    /** Span \u5c5e\u6027\u952e {@code ddd4j.query.type}\u3002 */
    public static final AttributeKey<String> ATTR_QUERY_TYPE = AttributeKey.stringKey("ddd4j.query.type");
    /** Span \u5c5e\u6027\u952e {@code ddd4j.projection.name}\u3002 */
    public static final AttributeKey<String> ATTR_PROJECTION_NAME = AttributeKey.stringKey("ddd4j.projection.name");
    /** Span \u5c5e\u6027\u952e {@code ddd4j.cache.biz}\u3002 */
    public static final AttributeKey<String> ATTR_CACHE_BIZ = AttributeKey.stringKey("ddd4j.cache.biz");
    /** Span \u5c5e\u6027\u952e {@code ddd4j.cache.backend}\u3002 */
    public static final AttributeKey<String> ATTR_CACHE_BACKEND = AttributeKey.stringKey("ddd4j.cache.backend");
    /** Span \u5c5e\u6027\u952e {@code ddd4j.cache.hit}\u3002 */
    public static final AttributeKey<Boolean> ATTR_CACHE_HIT = AttributeKey.booleanKey("ddd4j.cache.hit");
    /** Span \u5c5e\u6027\u952e {@code messaging.system}\u3002 */
    public static final AttributeKey<String> ATTR_MESSAGING_SYSTEM = AttributeKey.stringKey("messaging.system");
    /** Span \u5c5e\u6027\u952e {@code messaging.destination}\u3002 */
    public static final AttributeKey<String> ATTR_MESSAGING_DESTINATION = AttributeKey.stringKey("messaging.destination");
    /** Span \u5c5e\u6027\u952e {@code messaging.operation}\u3002 */
    public static final AttributeKey<String> ATTR_MESSAGING_OPERATION = AttributeKey.stringKey("messaging.operation");
    /** Span \u5c5e\u6027\u952e {@code db.system}\u3002 */
    public static final AttributeKey<String> ATTR_DB_SYSTEM = AttributeKey.stringKey("db.system");
    /** Span \u5c5e\u6027\u952e {@code db.statement}\u3002 */
    public static final AttributeKey<String> ATTR_DB_STATEMENT = AttributeKey.stringKey("db.statement");

    /** \u6309\u89c2\u6d4b\u4f5c\u7528\u57df\u7f13\u5b58 OpenTelemetry Tracer\u3002 */
    private static final AtomicReference<Tracer> TRACER_CACHE = new AtomicReference<>();
    /** \u6309\u89c2\u6d4b\u4f5c\u7528\u57df\u7f13\u5b58 OpenTelemetry Meter\u3002 */
    private static final AtomicReference<Meter> METER_CACHE = new AtomicReference<>();
    /** \u5f53\u524d\u8fdb\u7a0b\u662f\u5426\u80fd\u4f7f\u7528 OpenTelemetry API\u3002 */
    private static volatile boolean available = false;

    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>Ddd4jOtel</code> \u5b9e\u4f8b\u3002 */
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

        /** \u7d2f\u79ef OpenTelemetry \u952e\u503c\u5c5e\u6027\u7684\u539f\u751f\u6784\u5efa\u5668\u3002 */
        private final AttributesBuilder delegate = Attributes.builder();

        /** \u8bfb\u53d6 DDD4J \u5f53\u524d\u7ebf\u7a0b\u4e2d\u7684\u8ddf\u8e2a\u76f8\u5173\u5c5e\u6027\u3002 */
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

        /**
         * \u6784\u9020\u5df2\u6536\u96c6\u7684\u4e0d\u53ef\u53d8 OpenTelemetry \u5c5e\u6027\u96c6\u5408\u3002
         * @return \u53ef\u7528\u4e8e Span \u6216\u6307\u6807\u7684\u5c5e\u6027\u96c6\u5408
         */
        Attributes build() {
            return delegate.build();
        }

        /**
         * \u628a\u5df2\u6536\u96c6\u7684\u8ddf\u8e2a\u5c5e\u6027\u5e94\u7528\u5230\u6307\u5b9a Span\u3002
         * @param span \u63a5\u6536\u5f53\u524d\u8ddf\u8e2a\u5c5e\u6027\u7684 Span
         */
        void applyTo(Span span) {
            Attributes attrs = build();
            attrs.forEach((key, value) -> span.setAttribute((AttributeKey) key, value));
        }
    }
}
