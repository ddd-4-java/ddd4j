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

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

/**
 * {@link io.ddd4j.data.datascope.DataScopeProvider} 数据作用域评估的 OTel Span 包装。
 *
 * <p>为多租户过滤/数据权限评估提供 span 包装，
 * 无 OTel 时为 noop。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 4.0.0
 */
public final class DataScopeSpan {

    public static final AttributeKey<String> ATTR_DATA_SCOPE_TYPE = AttributeKey.stringKey("ddd4j.datascope.type");
    public static final AttributeKey<String> ATTR_DATA_SCOPE_DECISION = AttributeKey.stringKey("ddd4j.datascope.decision");

    private DataScopeSpan() {
    }

    /**
     * 包装 DataScopeProvider.hasPermissions 调用。
     *
     * @param dataType 数据类型
     * @param data     数据对象
     * @param provider DataScopeProvider 实例
     * @return 是否允许访问
     */
    public static boolean evaluate(String dataType, Object data, io.ddd4j.data.datascope.DataScopeProvider provider) {
        if (!Ddd4jOtel.isAvailable()) {
            return provider.hasPermissions(dataType, data);
        }
        Tracer tracer = Ddd4jOtel.tracer();
        Span span = tracer.spanBuilder("ddd4j.datascope.evaluate")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(ATTR_DATA_SCOPE_TYPE, dataType)
                .startSpan();
        try (Scope scope = span.makeCurrent()) {
            Ddd4jOtel.enrichWithBusinessContext(span);
            boolean allowed = provider.hasPermissions(dataType, data);
            span.setAttribute(ATTR_DATA_SCOPE_DECISION, allowed ? "allow" : "deny");
            return allowed;
        } catch (Throwable t) {
            span.recordException(t);
            span.setStatus(StatusCode.ERROR, t.getClass().getSimpleName());
            throw t;
        } finally {
            span.end();
        }
    }
}