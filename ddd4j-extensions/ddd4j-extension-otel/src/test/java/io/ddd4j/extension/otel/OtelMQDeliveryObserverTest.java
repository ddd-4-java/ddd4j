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
import java.util.Collections;

class OtelMQDeliveryObserverTest {

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
    void shouldDelegateAllDeliveryOutcomesWithoutMessageAttributes() {
        OtelMQDeliveryObserver observer = new OtelMQDeliveryObserver("kafka");
        MQOutboxRecord record = MQOutboxRecord.pending("message-1", "orders.created", "{}", Collections.emptyMap(),
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
