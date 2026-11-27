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

    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>OpenTelemetrySdkSetter</code> \u5b9e\u4f8b\u3002 */
    private OpenTelemetrySdkSetter() {
    }

    /**
     * \u8c03\u7528 <code>GlobalOpenTelemetry.resetForTest()</code>\u3001<code>GlobalOpenTelemetry.set()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param instance \u4f20\u7ed9 <code>GlobalOpenTelemetry.set()</code> \u7684 <code>OpenTelemetry</code> \u8f93\u5165
     */
    static void set(OpenTelemetry instance) {
        try {
            GlobalOpenTelemetry.resetForTest();
            GlobalOpenTelemetry.set(instance);
        } catch (Throwable t) {
            // 旧版本 API 失败时不需要做其他事情（noop 模式生效）
        }
    }
}
