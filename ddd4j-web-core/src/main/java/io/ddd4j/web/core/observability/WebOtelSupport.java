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
package io.ddd4j.web.core.observability;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * ddd4j-web 内部辅助类：通过纯反射安全调用 ddd4j-extension-otel，
 * 避免对 OTel API 的任何编译时依赖。
 *
 * <p>8 个 web 框架的拦截器/过滤器通过此类集成 WebOtelIntegration。
 * 若 ddd4j-extension-otel 不在 classpath 上，所有方法为 noop（反射失败 → 安全降级）。
 *
 * <h3>GraalVM native-image 注意事项</h3>
 * <p>本类在运行时通过 {@code Class.forName} + {@code getMethod} 反射调用
 * {@code io.ddd4j.extension.otel.WebOtelIntegration} 的 6 个静态方法
 * （{@code startServerSpan} / {@code activate} / {@code recordError} /
 * {@code endServerSpan} / {@code injectResponseContext} / {@code isAvailable}）。
 * 本模块是框架无关纯 Java 模块，刻意不引入 Quarkus 注解与 OTel 编译依赖，
 * 因此 native 反射注册由业务方声明。Quarkus 应用构建 native image 时请在
 * {@code application.properties} 加：
 * <pre>{@code
 * # WebOtelSupport 反射调用 WebOtelIntegration 所需；
 * # 不配置时 JVM 模式正常，native 模式下静默降级为 noop（无 tracing）
 * quarkus.native.reflection.include-patterns=io.ddd4j.extension.otel.WebOtelIntegration
 * }</pre>
 * <p>JVM 模式无需任何配置。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public final class WebOtelSupport {

    private static final Class<?> OTEL_INTEGRATION_CLASS;
    private static final Method START_SERVER_SPAN;
    private static final Method ACTIVATE;
    private static final Method RECORD_ERROR;
    private static final Method END_SERVER_SPAN;
    private static final Method INJECT_RESPONSE_CONTEXT;
    private static final Method IS_AVAILABLE;

    static {
        Class<?> cls = null;
        Method startSpan = null;
        Method act = null;
        Method recErr = null;
        Method endSpan = null;
        Method inject = null;
        Method isAvail = null;
        try {
            cls = Class.forName("io.ddd4j.extension.otel.WebOtelIntegration");
            startSpan = cls.getMethod("startServerSpan", String.class, String.class, Map.class);
            act = cls.getMethod("activate", Object.class);
            recErr = cls.getMethod("recordError", Object.class, Throwable.class);
            endSpan = cls.getMethod("endServerSpan", Object.class, int.class);
            inject = cls.getMethod("injectResponseContext", Map.class);
            isAvail = cls.getMethod("isAvailable");
        } catch (Throwable ignored) {
            // OTel 集成不在 classpath 上 → 所有方法降级为 noop
        }
        OTEL_INTEGRATION_CLASS = cls;
        START_SERVER_SPAN = startSpan;
        ACTIVATE = act;
        RECORD_ERROR = recErr;
        END_SERVER_SPAN = endSpan;
        INJECT_RESPONSE_CONTEXT = inject;
        IS_AVAILABLE = isAvail;
    }

    private WebOtelSupport() {
    }

    /**
     * 启动 SERVER span，返回 span 对象（OTel 未就绪时返回 null）。
     */
    public static Object startServerSpan(String method, String path, Map<String, String> headers) {
        if (Objects.isNull(START_SERVER_SPAN)) {
            return null;
        }
        try {
            return START_SERVER_SPAN.invoke(null, method, path, Objects.isNull(headers) ? new HashMap<>() : headers);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 激活 span 为当前 Context，返回 Scope（try-with-resources 可关闭）。
     */
    public static AutoCloseable activate(Object span) {
        if (Objects.isNull(ACTIVATE) || Objects.isNull(span)) {
            return () -> {
            };
        }
        try {
            Object scope = ACTIVATE.invoke(null, span);
            if (scope instanceof AutoCloseable) {
                return (AutoCloseable) scope;
            }
            return () -> {
            };
        } catch (Throwable t) {
            return () -> {
            };
        }
    }

    /**
     * 记录异常到 span。
     */
    public static void recordError(Object span, Throwable error) {
        if (Objects.isNull(RECORD_ERROR) || Objects.isNull(span) || Objects.isNull(error)) {
            return;
        }
        try {
            RECORD_ERROR.invoke(null, span, error);
        } catch (Throwable ignored) {
            // 静默失败
        }
    }

    /**
     * 结束 span 并记录 HTTP 状态码。
     */
    public static void endServerSpan(Object span, int status) {
        if (Objects.isNull(END_SERVER_SPAN) || Objects.isNull(span)) {
            return;
        }
        try {
            END_SERVER_SPAN.invoke(null, span, status);
        } catch (Throwable ignored) {
            // 静默失败
        }
    }

    /**
     * 注入 traceparent 到响应头。
     */
    public static void injectResponseContext(Map<String, String> responseHeaders) {
        if (Objects.isNull(INJECT_RESPONSE_CONTEXT) || Objects.isNull(responseHeaders)) {
            return;
        }
        try {
            INJECT_RESPONSE_CONTEXT.invoke(null, responseHeaders);
        } catch (Throwable ignored) {
            // 静默失败
        }
    }

    /**
     * 检查 OTel 集成是否可用。
     */
    public static boolean isAvailable() {
        if (Objects.isNull(IS_AVAILABLE)) {
            return false;
        }
        try {
            Object result = IS_AVAILABLE.invoke(null);
            return Boolean.TRUE.equals(result);
        } catch (Throwable t) {
            return false;
        }
    }
}
