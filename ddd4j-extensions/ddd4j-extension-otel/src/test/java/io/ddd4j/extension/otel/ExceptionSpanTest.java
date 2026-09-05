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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ExceptionSpan} 异常 span 事件测试（无 SDK 依赖，纯行为验证）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class ExceptionSpanTest {

    @BeforeEach
    void setUp() {
        OpenTelemetrySdkSetter.set(OpenTelemetry.noop());
    }

    @AfterEach
    void tearDown() {
        OpenTelemetrySdkSetter.set(OpenTelemetry.noop());
    }

    @Test
    void record_withNull_shouldNotThrow() {
        ExceptionSpan.record(null);
        assertThat(true).isTrue();
    }

    @Test
    void record_whenOtelNotAvailable_shouldNotThrow() {
        OpenTelemetrySdkSetter.set(OpenTelemetry.noop());
        ExceptionSpan.record(new RuntimeException("ignored"));
        assertThat(true).isTrue();
    }

    @Test
    void record_withNullSpan_shouldNotThrow() {
        ExceptionSpan.record(null, new RuntimeException("test"));
        assertThat(true).isTrue();
    }

    @Test
    void record_withNullThrowableAndNullSpan_shouldNotThrow() {
        ExceptionSpan.record(null, null);
        assertThat(true).isTrue();
    }

    @Test
    void record_shouldAcceptAnyThrowable() {
        // 不抛异常即通过（noop OTel 下 record 是 noop）
        ExceptionSpan.record(new IllegalStateException("state error"));
        ExceptionSpan.record(new IllegalArgumentException("arg error"));
        ExceptionSpan.record(new RuntimeException("runtime error"));
        assertThat(true).isTrue();
    }
}