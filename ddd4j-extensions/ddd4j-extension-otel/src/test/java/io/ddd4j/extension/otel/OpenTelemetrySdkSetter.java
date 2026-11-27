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

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;

/**
 * 测试辅助：设置 OpenTelemetry 全局实例。
 *
 * <p>新版本 API（1.40+）使用 {@link GlobalOpenTelemetry#set(OpenTelemetry)}，
 * 旧版本使用反射设置 INSTANCE 字段。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
final class OpenTelemetrySdkSetter {

    private OpenTelemetrySdkSetter() {
    }

    static void set(OpenTelemetry instance) {
        try {
            GlobalOpenTelemetry.resetForTest();
            GlobalOpenTelemetry.set(instance);
        } catch (Throwable t) {
            // 旧版本 API 失败时不需要做其他事情（noop 模式生效）
        }
    }
}
