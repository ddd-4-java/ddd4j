package io.ddd4j.extension.otel;

import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.context.ThreadContext;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link Ddd4jOtel} 主入口测试。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class Ddd4jOtelTest {

    @BeforeEach
    void setUp() {
        ThreadContext.clear();
        resetTracerCache();
    }

    @AfterEach
    void tearDown() {
        ThreadContext.clear();
        OpenTelemetrySdkSetter.set(OpenTelemetry.noop());
        resetTracerCache();
    }

    private static void resetTracerCache() {
        try {
            Field field = Ddd4jOtel.class.getDeclaredField("TRACER_CACHE");
            field.setAccessible(true);
            ((java.util.concurrent.atomic.AtomicReference<?>) field.get(null)).set(null);
        } catch (Exception ignored) {
            // 反射失败不影响测试
        }
    }

    @Test
    void tracer_shouldReturnNonNullTracer() {
        Tracer tracer = Ddd4jOtel.tracer();
        assertThat(tracer).isNotNull();
    }

    @Test
    void tracer_shouldReturnSameInstanceOnRepeatedCalls() {
        Tracer t1 = Ddd4jOtel.tracer();
        Tracer t2 = Ddd4jOtel.tracer();
        assertThat(t1).isSameAs(t2);
    }

    @Test
    void isAvailable_shouldReturnBoolean() {
        // 仅验证返回 boolean 类型（不关心具体值，因为依赖 OTel 状态）
        boolean available = Ddd4jOtel.isAvailable();
        // 重新调用应返回相同结果
        assertThat(Ddd4jOtel.isAvailable()).isEqualTo(available);
    }

    @Test
    void businessContextAttributes_shouldIncludeTenantWhenSet() {
        ThreadContext.set(ContextConstants.TENANT_ID, "tenant-XYZ");

        Attributes attrs = Ddd4jOtel.businessContextAttributes();
        assertThat(attrs.get(Ddd4jOtel.ATTR_TENANT_ID)).isEqualTo("tenant-XYZ");
    }

    @Test
    void businessContextAttributes_shouldBeEmptyWhenNoContext() {
        Attributes attrs = Ddd4jOtel.businessContextAttributes();
        assertThat(attrs.get(Ddd4jOtel.ATTR_TENANT_ID)).isNull();
    }

    @Test
    void standardAttributeKeys_shouldHaveCorrectNames() {
        assertThat(Ddd4jOtel.ATTR_TENANT_ID.getKey()).isEqualTo("ddd4j.tenant.id");
        assertThat(Ddd4jOtel.ATTR_USER_ID.getKey()).isEqualTo("ddd4j.user.id");
        assertThat(Ddd4jOtel.ATTR_AGGREGATE_TYPE.getKey()).isEqualTo("ddd4j.aggregate.type");
        assertThat(Ddd4jOtel.ATTR_AGGREGATE_ID.getKey()).isEqualTo("ddd4j.aggregate.id");
        assertThat(Ddd4jOtel.ATTR_QUERY_TYPE.getKey()).isEqualTo("ddd4j.query.type");
        assertThat(Ddd4jOtel.ATTR_PROJECTION_NAME.getKey()).isEqualTo("ddd4j.projection.name");
        assertThat(Ddd4jOtel.ATTR_CACHE_BIZ.getKey()).isEqualTo("ddd4j.cache.biz");
        assertThat(Ddd4jOtel.ATTR_CACHE_BACKEND.getKey()).isEqualTo("ddd4j.cache.backend");
        assertThat(Ddd4jOtel.ATTR_CACHE_HIT.getKey()).isEqualTo("ddd4j.cache.hit");
        assertThat(Ddd4jOtel.ATTR_MESSAGING_SYSTEM.getKey()).isEqualTo("messaging.system");
        assertThat(Ddd4jOtel.ATTR_MESSAGING_DESTINATION.getKey()).isEqualTo("messaging.destination");
        assertThat(Ddd4jOtel.ATTR_DB_SYSTEM.getKey()).isEqualTo("db.system");
        assertThat(Ddd4jOtel.ATTR_DOMAIN_EVENT_TYPE.getKey()).isEqualTo("ddd4j.event.type");
    }

    @Test
    void enrichWithBusinessContext_withNullSpan_shouldNotThrow() {
        ThreadContext.set(ContextConstants.TENANT_ID, "tenant-001");
        // null span 应该安全忽略
        Ddd4jOtel.enrichWithBusinessContext(null);
        assertThat(true).isTrue();
    }

    @Test
    void tracerName_shouldBeDdd4j() {
        assertThat(Ddd4jOtel.TRACER_NAME).isEqualTo("io.ddd4j");
    }

    @Test
    void businessContextAttributes_shouldBeAttributeKey() {
        assertThat(Ddd4jOtel.ATTR_TENANT_ID).isInstanceOf(AttributeKey.class);
        assertThat(Ddd4jOtel.ATTR_USER_ID).isInstanceOf(AttributeKey.class);
    }
}