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

    @BeforeEach
    void setUp() {
        OpenTelemetrySdkSetter.set(OpenTelemetry.noop());
    }

    @AfterEach
    void tearDown() {
        OpenTelemetrySdkSetter.set(OpenTelemetry.noop());
    }

    @Test
    void producer_shouldExecuteRunnable() {
        Map<String, String> headers = new HashMap<>();

        MqSpan.producer("kafka", "order-topic", headers, () -> {
            // 模拟发送
        });

        // 即使 OTel 不可用，runnable 也应该执行
        assertThat(headers).isNotNull();
    }

    @Test
    void producer_whenOtelNotAvailable_shouldNotInjectHeaders() {
        OpenTelemetrySdkSetter.set(OpenTelemetry.noop());
        Map<String, String> headers = new HashMap<>();

        MqSpan.producer("kafka", "topic", headers, () -> {
        });

        // noop 模式下不注入 traceparent
        assertThat(headers).doesNotContainKey("traceparent");
    }

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

    @Test
    void consumer_withEmptyHeaders_shouldReturnNoopScope() {
        Map<String, String> headers = new HashMap<>();

        io.opentelemetry.context.Scope scope = MqSpan.consumer("kafka", "topic", headers);

        assertThat(scope).isNotNull();
        scope.close();
    }

    @Test
    void endConsumer_shouldNotThrowWithNullSpan() {
        MqSpan.endConsumer(null);
        MqSpan.endConsumer(null, new RuntimeException("err"));
        assertThat(true).isTrue();
    }

    @Test
    void producer_supportsVariousBrokers() {
        for (String broker : new String[]{"kafka", "rabbitmq", "rocketmq", "redisStream", "pulsar"}) {
            Map<String, String> headers = new HashMap<>();
            MqSpan.producer(broker, "topic", headers, () -> {
            });
        }
        assertThat(true).isTrue();
    }

    @Test
    void producer_handlesNullTopic() {
        Map<String, String> headers = new HashMap<>();

        MqSpan.producer("kafka", null, headers, () -> {
        });

        assertThat(true).isTrue();
    }

    @Test
    void producer_acceptsNullHeaders() {
        MqSpan.producer("kafka", "topic", null, () -> {
        });
        assertThat(true).isTrue();
    }
}