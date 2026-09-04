package io.ddd4j.extension.otel;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongCounterBuilder;
import io.opentelemetry.api.metrics.Meter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MqDeliveryMetrics} 指标语义测试。
 *
 * <p>测试将 Meter 替换为 mock，只验证稳定的指标名、低基数标签和计数值；生产模块
 * 不内置 SDK、exporter 或 Collector。
 */
class MqDeliveryMetricsTest {

    private Meter meter;
    private LongCounterBuilder builder;
    private LongCounter counter;

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

    @AfterEach
    void tearDown() {
        setMeterCache(null);
    }

    @Test
    void outboxMetrics_shouldUseControlledOutcomesAndNeverAttachMessageData() {
        MqDeliveryMetrics.outboxPublished("kafka");
        MqDeliveryMetrics.outboxRetry("kafka");
        MqDeliveryMetrics.outboxDead("kafka");

        ArgumentCaptor<Attributes> attributesCaptor = ArgumentCaptor.forClass(Attributes.class);
        verify(meter).counterBuilder(MqDeliveryMetrics.OUTBOX_DELIVERY_METRIC);
        verify(counter, times(3)).add(eq(1L), attributesCaptor.capture());
        assertThat(attributesCaptor.getAllValues())
                .allSatisfy(attributes -> assertThat(attributes.asMap().keySet())
                        .extracting(attributeKey -> attributeKey.getKey())
                        .containsExactlyInAnyOrder("messaging.system", "ddd4j.delivery.outcome"));
        assertThat(attributesCaptor.getAllValues())
                .extracting(attributes -> attributes.get(Ddd4jOtel.ATTR_MESSAGING_SYSTEM))
                .containsOnly("kafka");
        assertThat(attributesCaptor.getAllValues())
                .extracting(attributes -> attributes.get(MqDeliveryMetrics.ATTR_DELIVERY_OUTCOME))
                .containsExactlyInAnyOrder("published", "retry", "dead");
    }

    @Test
    void inboxMetrics_shouldExposeOnlyExpectedOutcomeAndFallbackBroker() {
        MqDeliveryMetrics.inboxProcessed(null);
        MqDeliveryMetrics.inboxDuplicate("rabbitmq");
        MqDeliveryMetrics.inboxFailed("rabbitmq");

        ArgumentCaptor<Attributes> attributesCaptor = ArgumentCaptor.forClass(Attributes.class);
        verify(meter).counterBuilder(MqDeliveryMetrics.INBOX_DELIVERY_METRIC);
        verify(counter, times(3)).add(eq(1L), attributesCaptor.capture());
        assertThat(attributesCaptor.getAllValues())
                .contains(Attributes.of(
                        Ddd4jOtel.ATTR_MESSAGING_SYSTEM, "unknown",
                        MqDeliveryMetrics.ATTR_DELIVERY_OUTCOME, "processed"
                ));
        assertThat(attributesCaptor.getAllValues())
                .extracting(attributes -> attributes.get(MqDeliveryMetrics.ATTR_DELIVERY_OUTCOME))
                .containsExactlyInAnyOrder("processed", "duplicate", "failed");
    }

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
