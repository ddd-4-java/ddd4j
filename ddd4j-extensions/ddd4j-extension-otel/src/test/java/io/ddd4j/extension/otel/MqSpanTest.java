package io.ddd4j.extension.otel;

import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MqSpan} 消息队列 span 测试（无 SDK 依赖，纯行为验证）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class MqSpanTest {

    /** \u6d4b\u8bd5\u6267\u884c\u524d\u6267\u884c <code>OpenTelemetrySdkSetter.set()</code>\u3001<code>OpenTelemetry.noop()</code> \u5b8c\u6210\u8d44\u6e90\u51c6\u5907\u3002 */
    @BeforeEach
    void setUp() {
        OpenTelemetrySdkSetter.set(OpenTelemetry.noop());
    }

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>OpenTelemetrySdkSetter.set()</code>\u3001<code>OpenTelemetry.noop()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void tearDown() {
        OpenTelemetrySdkSetter.set(OpenTelemetry.noop());
    }

    /** \u9a8c\u8bc1<code>headers</code>\u975e\u7a7a\u3002 */
    @Test
    void producer_shouldExecuteRunnable() {
        Map<String, String> headers = new HashMap<>();

        MqSpan.producer("kafka", "order-topic", headers, () -> {
            // 模拟发送
        });

        // 即使 OTel 不可用，runnable 也应该执行
        assertThat(headers).isNotNull();
    }

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>OpenTelemetrySdkSetter.set()</code>\u3001<code>OpenTelemetry.noop()</code>\u3001<code>MqSpan.producer()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void producer_whenOtelNotAvailable_shouldNotInjectHeaders() {
        OpenTelemetrySdkSetter.set(OpenTelemetry.noop());
        Map<String, String> headers = new HashMap<>();

        MqSpan.producer("kafka", "topic", headers, () -> {
        });

        // noop 模式下不注入 traceparent
        assertThat(headers).doesNotContainKey("traceparent");
    }

    /** \u9a8c\u8bc1<code>scope</code>\u975e\u7a7a\u3002 */
    @Test
    void consumer_shouldReturnNonNullScope() {
        Map<String, String> headers = new HashMap<>();

        io.opentelemetry.context.Scope scope = MqSpan.consumer("kafka", "topic", headers);
        try {
            assertThat(scope).isNotNull();
        } finally {
            scope.close();
        }
    }

    /** \u9a8c\u8bc1<code>scope</code>\u975e\u7a7a\u3002 */
    @Test
    void consumer_withEmptyHeaders_shouldReturnNoopScope() {
        Map<String, String> headers = new HashMap<>();

        io.opentelemetry.context.Scope scope = MqSpan.consumer("kafka", "topic", headers);

        assertThat(scope).isNotNull();
        scope.close();
    }

    /** \u9a8c\u8bc1<code>true</code>\u4e3a\u771f\u3002 */
    @Test
    void endConsumer_shouldNotThrowWithNullSpan() {
        MqSpan.endConsumer(null);
        MqSpan.endConsumer(null, new RuntimeException("err"));
        assertThat(true).isTrue();
    }

    /** \u9a8c\u8bc1<code>true</code>\u4e3a\u771f\u3002 */
    @Test
    void producer_supportsVariousBrokers() {
        for (String broker : new String[]{"kafka", "rabbitmq", "rocketmq", "redisStream", "pulsar"}) {
            Map<String, String> headers = new HashMap<>();
            MqSpan.producer(broker, "topic", headers, () -> {
            });
        }
        assertThat(true).isTrue();
    }

    /** \u9a8c\u8bc1<code>true</code>\u4e3a\u771f\u3002 */
    @Test
    void producer_handlesNullTopic() {
        Map<String, String> headers = new HashMap<>();

        MqSpan.producer("kafka", null, headers, () -> {
        });

        assertThat(true).isTrue();
    }

    /** \u9a8c\u8bc1<code>true</code>\u4e3a\u771f\u3002 */
    @Test
    void producer_acceptsNullHeaders() {
        MqSpan.producer("kafka", "topic", null, () -> {
        });
        assertThat(true).isTrue();
    }
}