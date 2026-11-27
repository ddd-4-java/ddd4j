package io.ddd4j.extension.otel;

import io.ddd4j.mq.delivery.MQOutboxRecord;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongCounterBuilder;
import io.opentelemetry.api.metrics.Meter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** \u9a8c\u8bc1 MQ \u6295\u9012\u4e8b\u4ef6\u7684\u4f4e\u57fa\u6570\u6307\u6807\u5951\u7ea6\u3002 */
class OtelMQDeliveryObserverTest {

    /** \u63d0\u4f9b\u6d4b\u8bd5\u6307\u6807\u6ce8\u518c\u80fd\u529b\u7684 OpenTelemetry Meter\u3002 */
    private Meter meter;
    /** \u5f53\u524d\u6d4b\u8bd5\u4ece\u6837\u4f8b\u914d\u7f6e\u521b\u5efa\u88ab\u6d4b\u7ec4\u4ef6\u7684\u6784\u5efa\u5668\u3002 */
    private LongCounterBuilder builder;
    /** \u7528\u6765\u9a8c\u8bc1\u6307\u6807\u6216\u7d2f\u8ba1\u72b6\u6001\u7684\u8ba1\u6570\u5668\u3002 */
    private LongCounter counter;

    /** \u6d4b\u8bd5\u6267\u884c\u524d\u6267\u884c <code>mock()</code>\u3001<code>meter.counterBuilder()</code>\u3001<code>any()</code>\u3001<code>builder.setUnit()</code> \u5b8c\u6210\u8d44\u6e90\u51c6\u5907\u3002 */
    @BeforeEach
    void setUp() {
        meter = mock(Meter.class);
        builder = mock(LongCounterBuilder.class);
        counter = mock(LongCounter.class);
        when(meter.counterBuilder(any())).thenReturn(builder);
        when(builder.setUnit(any())).thenReturn(builder);
        when(builder.setDescription(any())).thenReturn(builder);
        when(builder.build()).thenReturn(counter);
        setMeterCache(meter);
    }

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>setMeterCache()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void tearDown() {
        setMeterCache(null);
    }

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>MQOutboxRecord.pending()</code>\u3001<code>Collections.emptyMap()</code>\u3001<code>observer.onOutboxPublished()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void shouldDelegateAllDeliveryOutcomesWithoutMessageAttributes() {
        OtelMQDeliveryObserver observer = new OtelMQDeliveryObserver("kafka");
        MQOutboxRecord record = MQOutboxRecord.pending("message-1", "orders.created", "{}", Map.of(),
                Instant.EPOCH);

        observer.onOutboxPublished(record);
        observer.onOutboxRetry(record);
        observer.onOutboxDead(record);
        observer.onOutboxFailed(record);
        observer.onInboxProcessed("order-projection", "message-1");
        observer.onInboxDuplicate("order-projection", "message-1");
        observer.onInboxFailed("order-projection", "message-1");

        ArgumentCaptor<Attributes> attributesCaptor = ArgumentCaptor.forClass(Attributes.class);
        verify(meter).counterBuilder(MqDeliveryMetrics.OUTBOX_DELIVERY_METRIC);
        verify(meter).counterBuilder(MqDeliveryMetrics.INBOX_DELIVERY_METRIC);
        verify(counter, times(7)).add(eq(1L), attributesCaptor.capture());
        assertThat(attributesCaptor.getAllValues())
                .allSatisfy(attributes -> assertThat(attributes.asMap().keySet())
                        .extracting(attributeKey -> attributeKey.getKey())
                        .containsExactlyInAnyOrder("messaging.system", "ddd4j.delivery.outcome"));
        assertThat(attributesCaptor.getAllValues())
                .extracting(attributes -> attributes.get(Ddd4jOtel.ATTR_MESSAGING_SYSTEM))
                .containsOnly("kafka");
        assertThat(attributesCaptor.getAllValues())
                .extracting(attributes -> attributes.get(MqDeliveryMetrics.ATTR_DELIVERY_OUTCOME))
                .containsExactlyInAnyOrder("published", "retry", "dead", "failed", "processed", "duplicate", "failed");
    }

    /**
     * \u8c03\u7528 <code>Ddd4jOtel.class.getDeclaredField()</code>\u3001<code>field.setAccessible()</code>\u3001<code>field.get()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param meter \u4f20\u7ed9 <code>((AtomicReference&lt;Meter&gt;)field.get(null)).set(meter)</code> \u7684 <code>Meter</code> \u8f93\u5165
     */
    @SuppressWarnings("unchecked")
    private static void setMeterCache(Meter meter) {
        try {
            Field field = Ddd4jOtel.class.getDeclaredField("METER_CACHE");
            field.setAccessible(true);
            ((AtomicReference<Meter>) field.get(null)).set(meter);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to configure OpenTelemetry meter test cache", exception);
        }
    }
}
