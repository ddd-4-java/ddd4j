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
package io.ddd4j.core.context;

import io.ddd4j.kit.lang.StrKit;
import org.slf4j.MDC;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * traceId 写入 TTL {@link ThreadContext} 的桥接点（纯 Java，零 Spring 依赖）。
 *
 * <p>与既有 correlationId/causationId 因果链在同一 TTL 透传上下文中并列呈现
 * （对应家族规格 Requirement: Causality correlation），跨线程池透传由 TTL 机制天然完成。
 *
 * <h3>开关语义</h3>
 * <p>系统属性 {@link #TRACING_ENABLED_PROPERTY} 控制，<b>默认关闭</b>；关闭时本桥接零写入，
 * 既有对外行为与开启前一致（Requirement: Trace context propagation 默认关闭零影响场景）。
 *
 * <h3>双写</h3>
 * <p>trace 标识同时写入 TTL {@link ThreadContext}（跨线程池透传）与 slf4j {@link MDC}
 * （日志渲染），两处值始终一致。完整的导出栈（micrometer-tracing/OTel）由 ddd4j-boot
 * 观测性模块承接，本桥接只负责标识贯通。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public final class TraceContextBridge {

    /**
     * 追踪开关系统属性（对齐家族冻结配置键 {@code ddd4j.observability.tracing.enabled}，默认 false）。
     */
    public static final String TRACING_ENABLED_PROPERTY = "ddd4j.observability.tracing.enabled";

    /**
     * TTL ThreadContext 中的 trace 标识键（kebab-case，对齐既有上下文键风格）。
     */
    public static final String TRACE_ID_CONTEXT_KEY = "trace-id";

    /**
     * TTL ThreadContext 中的因果链关联 ID 键（与 trace 标识并列）。
     */
    public static final String CORRELATION_ID_CONTEXT_KEY = "correlation-id";

    /**
     * TTL ThreadContext 中的因果链因果 ID 键（与 trace 标识并列）。
     */
    public static final String CAUSATION_ID_CONTEXT_KEY = "causation-id";

    /**
     * MDC 中的 trace 标识键（camelCase，对齐家族冻结的日志结构化字段名 {@code traceId}）。
     */
    public static final String TRACE_ID_MDC_KEY = "traceId";

    private TraceContextBridge() {
    }

    /**
     * 判断追踪桥接是否开启。
     *
     * @return 系统属性为 {@code true} 时 {@code true}，其余（含未配置）一律 {@code false}
     */
    public static boolean enabled() {
        return Boolean.parseBoolean(System.getProperty(TRACING_ENABLED_PROPERTY, "false"));
    }

    /**
     * 开启追踪作用域并新生成 trace 标识。
     *
     * @return 追踪作用域，关闭时恢复进入前上下文
     */
    public static Scope begin() {
        return begin(null);
    }

    /**
     * 开启追踪作用域：入站追踪头合法时继承其 trace 标识，否则新生成。
     *
     * <p>对应家族规格 Trace context propagation 的「入站继承/无头新建」语义；
     * 桥接默认关闭时返回零操作作用域。
     *
     * @param inboundTraceId 入站追踪标识（如 W3C traceparent 中的 trace-id）；空表示新建
     * @return 追踪作用域
     */
    public static Scope begin(String inboundTraceId) {
        if (!enabled()) {
            return Scope.NOOP;
        }
        String traceId = StrKit.isBlank(inboundTraceId) ? newTraceId() : inboundTraceId.trim();
        Object previousContext = ThreadContext.get(TRACE_ID_CONTEXT_KEY);
        String previousMdc = MDC.get(TRACE_ID_MDC_KEY);
        ThreadContext.set(TRACE_ID_CONTEXT_KEY, traceId);
        MDC.put(TRACE_ID_MDC_KEY, traceId);
        return new Scope(Objects.nonNull(previousContext) ? previousContext.toString() : null, previousMdc);
    }

    /**
     * 读取当前线程的 trace 标识。
     *
     * @return 当前 trace 标识；未开启或未进入作用域时返回 {@code null}
     */
    public static String currentTraceId() {
        Object value = ThreadContext.get(TRACE_ID_CONTEXT_KEY);
        return Objects.nonNull(value) ? value.toString() : null;
    }

    /**
     * 因果链并列快照：trace 标识与 correlationId/causationId 同上下文并列可取。
     *
     * <p>MUST NOT 另建与因果链互不相通的第二套标识体系（Requirement: Causality correlation）。
     *
     * @return 不可变快照，仅含当前线程中存在的键
     */
    public static Map<String, String> correlationSnapshot() {
        Map<String, String> snapshot = new LinkedHashMap<String, String>();
        putIfPresent(snapshot, TRACE_ID_CONTEXT_KEY);
        putIfPresent(snapshot, CORRELATION_ID_CONTEXT_KEY);
        putIfPresent(snapshot, CAUSATION_ID_CONTEXT_KEY);
        return snapshot;
    }

    /**
     * 生成新 trace 标识。
     *
     * @return UUID 文本
     */
    private static String newTraceId() {
        return UUID.randomUUID().toString();
    }

    /**
     * 将上下文键值放入快照（值不存在时跳过）。
     *
     * @param snapshot 快照容器
     * @param key      上下文键
     */
    private static void putIfPresent(Map<String, String> snapshot, String key) {
        Object value = ThreadContext.get(key);
        if (Objects.nonNull(value)) {
            snapshot.put(key, value.toString());
        }
    }

    /**
     * 追踪作用域：关闭时恢复进入前的 TTL 上下文与 MDC 状态。
     */
    public static final class Scope implements AutoCloseable {

        /**
         * 桥接关闭时的零操作作用域。
         */
        static final Scope NOOP = new Scope(null, null, true);

        /**
         * 零操作标记（默认关闭时 close 不得触碰上下文）。
         */
        private final boolean noop;
        /**
         * 进入作用域前的 TTL trace 标识（嵌套作用域恢复用）；无则 {@code null}。
         */
        private final String previousContextTraceId;
        /**
         * 进入作用域前的 MDC trace 标识（嵌套作用域恢复用）；无则 {@code null}。
         */
        private final String previousMdcTraceId;
        /**
         * 是否已关闭。
         */
        private boolean closed;

        private Scope(String previousContextTraceId, String previousMdcTraceId) {
            this(previousContextTraceId, previousMdcTraceId, false);
        }

        private Scope(String previousContextTraceId, String previousMdcTraceId, boolean noop) {
            this.previousContextTraceId = previousContextTraceId;
            this.previousMdcTraceId = previousMdcTraceId;
            this.noop = noop;
        }

        @Override
        public void close() {
            if (closed || noop) {
                return;
            }
            if (Objects.isNull(previousContextTraceId)) {
                ThreadContext.remove(TRACE_ID_CONTEXT_KEY);
            } else {
                ThreadContext.set(TRACE_ID_CONTEXT_KEY, previousContextTraceId);
            }
            if (Objects.isNull(previousMdcTraceId)) {
                MDC.remove(TRACE_ID_MDC_KEY);
            } else {
                MDC.put(TRACE_ID_MDC_KEY, previousMdcTraceId);
            }
            closed = true;
        }
    }
}
