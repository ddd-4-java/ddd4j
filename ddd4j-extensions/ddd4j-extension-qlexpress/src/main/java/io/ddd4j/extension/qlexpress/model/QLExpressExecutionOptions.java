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
package io.ddd4j.extension.qlexpress.model;

import com.alibaba.qlexpress4.QLOptions;
import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * QLExpress 单次执行选项。
 *
 * <p>默认开启编译缓存，并设置 3 秒超时和 10000 的数组长度上限。
 */
@Getter
@Builder(toBuilder = true)
public final class QLExpressExecutionOptions {

    /**
     * 默认执行超时时间（毫秒）。
     */
    public static final long DEFAULT_TIMEOUT_MILLIS = 3000L;
    /**
     * 默认数组长度上限。
     */
    public static final int DEFAULT_MAX_ARRAY_LENGTH = 10000;

    /**
     * 是否启用高精度数值运算。
     */
    @Builder.Default
    private final boolean precise = false;
    /**
     * 是否把表达式产生的变量修改写回用户上下文。
     */
    @Builder.Default
    private final boolean polluteUserContext = false;
    /**
     * 单次表达式执行的超时时间，单位为毫秒。
     */
    @Builder.Default
    private final long timeoutMillis = DEFAULT_TIMEOUT_MILLIS;
    /**
     * 随当前执行下发到引擎上下文的额外对象。
     */
    @Builder.Default
    private final Map<String, Object> attachments = Collections.emptyMap();
    /**
     * 是否复用已解析的表达式结果。
     */
    @Builder.Default
    private final boolean cache = true;
    /**
     * 是否启用表达式执行器的空引用保护。
     */
    @Builder.Default
    private final boolean avoidNullPointer = false;
    /**
     * 表达式可创建或处理的数组长度限制。
     */
    @Builder.Default
    private final int maxArrayLength = DEFAULT_MAX_ARRAY_LENGTH;
    /**
     * 当前表达式执行是否收集跟踪信息。
     */
    @Builder.Default
    private final boolean traceExpression = false;
    /**
     * 是否关闭布尔运算的短路求值。
     */
    @Builder.Default
    private final boolean shortCircuitDisabled = false;

    /**
     * 创建默认执行选项。
     *
     * @return 默认执行选项
     */
    public static QLExpressExecutionOptions defaults() {
        return QLExpressExecutionOptions.builder().build();
    }

    /**
     * 转换为 QLExpress 原生执行选项。
     *
     * @return 原生执行选项
     */
    public QLOptions toNativeOptions() {
        Map<String, Object> safeAttachments = Objects.isNull(attachments) ? Collections.emptyMap() : Collections.unmodifiableMap(new HashMap<>(attachments));
        return QLOptions.builder()
                .precise(precise)
                .polluteUserContext(polluteUserContext)
                .timeoutMillis(timeoutMillis)
                .attachments(safeAttachments)
                .cache(cache)
                .avoidNullPointer(avoidNullPointer)
                .maxArrLength(maxArrayLength)
                .traceExpression(traceExpression)
                .shortCircuitDisable(shortCircuitDisabled)
                .build();
    }
}
