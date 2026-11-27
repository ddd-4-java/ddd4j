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
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 验证 MQ 投递事件的低基数指标契约。
 */
class OtelMQDeliveryObserverTest {

    /**
     * 提供测试指标注册能力的 OpenTelemetry Meter。
     */
    private Meter meter;
    /**
     * 当前测试从样例配置创建被测组件的构建器。
     */
    private LongCounterBuilder builder;
    /**
     * 用来验证指标或累计状态的计数器。
     */
    private LongCounter counter;

    /**
     * 调用 <code>Ddd4jOtel.class.getDeclaredField()</code>、<code>field.setAccessible()</code>、<code>field.get()</code> 执行当前夹具操作。
     *
     * @param meter 传给 <code>((AtomicReference&lt;Meter&gt;)field.get(null)).set(meter)</code> 的 <code>Meter</code> 输入
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

    /**
     * 测试执行前执行 <code>mock()</code>、<code>meter.counterBuilder()</code>、<code>any()</code>、<code>builder.setUnit()</code> 完成资源准备。
     */
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

    /**
     * 测试结束后执行 <code>setMeterCache()</code> 完成资源回收或状态恢复。
     */
    @AfterEach
    void tearDown() {
        setMeterCache(null);
    }

    /**
     * 在测试框架中执行 <code>MQOutboxRecord.pending()</code>、<code>Collections.emptyMap()</code>、<code>observer.onOutboxPublished()</code>，验证该场景。
     */
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
}
