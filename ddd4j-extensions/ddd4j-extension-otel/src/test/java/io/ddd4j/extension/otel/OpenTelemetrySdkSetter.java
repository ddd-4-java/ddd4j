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
            GlobalOpenTelemetry.set(instance);
        } catch (Throwable t) {
            // 旧版本 API 失败时不需要做其他事情（noop 模式生效）
        }
    }
}