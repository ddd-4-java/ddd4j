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

import com.alibaba.ttl.threadpool.TtlExecutors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * traceId 写入 TTL ThreadContext 桥接点行为测试。
 *
 * <p>对应 OpenSpec change {@code promote-causality-to-broker-headers}，
 * 派生自家族规格 Requirement: Trace context propagation（异步线程池透传、默认关闭零影响）
 * 与 Requirement: Causality correlation（trace 标识与因果链并列呈现）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class TraceContextBridgeMustTest {

    private static void enableTracing() {
        System.setProperty(TraceContextBridge.TRACING_ENABLED_PROPERTY, "true");
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(TraceContextBridge.TRACING_ENABLED_PROPERTY);
        ThreadContext.clear();
        MDC.clear();
    }

    // ========================= Scenario: 默认关闭零影响 =========================

    @Test
    void bridgeMustBeDisabledByDefaultAndWriteNothing() {
        assertFalse(TraceContextBridge.enabled(), "追踪桥接默认必须关闭");

        TraceContextBridge.Scope scope = TraceContextBridge.begin("inbound-trace");
        try {
            assertNull(TraceContextBridge.currentTraceId(), "默认关闭时不得写入 traceId");
            assertNull(MDC.get(TraceContextBridge.TRACE_ID_MDC_KEY), "默认关闭时不得写 MDC");
            assertNull(ThreadContext.get(TraceContextBridge.TRACE_ID_CONTEXT_KEY), "默认关闭时不得写 TTL 上下文");
        } finally {
            scope.close();
        }
        assertTrue(TraceContextBridge.correlationSnapshot().isEmpty(), "默认关闭时快照为空");
    }

    // ========================= Scenario: 异步线程池透传 =========================

    @Test
    void traceIdMustPropagateAcrossTtlThreadPoolWhenEnabled() throws Exception {
        enableTracing();
        ExecutorService rawPool = Executors.newSingleThreadExecutor();
        ExecutorService ttlPool = TtlExecutors.getTtlExecutorService(rawPool);
        AtomicReference<String> tracedInPool = new AtomicReference<>();

        try (TraceContextBridge.Scope scope = TraceContextBridge.begin("trace-parent-001")) {
            assertEquals("trace-parent-001", TraceContextBridge.currentTraceId(), "入站 traceId 合法时必须继承");
            assertEquals("trace-parent-001", MDC.get(TraceContextBridge.TRACE_ID_MDC_KEY),
                    "提交方线程 MDC 双写必须一致");

            ttlPool.submit(() -> tracedInPool.set(TraceContextBridge.currentTraceId()))
                    .get(5, TimeUnit.SECONDS);

            assertEquals("trace-parent-001", tracedInPool.get(),
                    "TTL 线程池内 trace 标识必须与提交方一致");
        } finally {
            ttlPool.shutdown();
            assertTrue(rawPool.awaitTermination(5, TimeUnit.SECONDS));
        }

        assertNull(TraceContextBridge.currentTraceId(), "作用域关闭后必须恢复原上下文");
        assertNull(MDC.get(TraceContextBridge.TRACE_ID_MDC_KEY), "作用域关闭后必须清除 MDC");
    }

    // ========================= Scenario: 无追踪头时新建 =========================

    @Test
    void bridgeMustGenerateTraceIdWhenInboundBlank() {
        enableTracing();

        try (TraceContextBridge.Scope ignored = TraceContextBridge.begin("  ")) {
            String traceId = TraceContextBridge.currentTraceId();
            assertNotNull(traceId, "入站为空时必须新生成 traceId");
            assertFalse(traceId.trim().isEmpty());
            assertEquals(traceId, MDC.get(TraceContextBridge.TRACE_ID_MDC_KEY),
                    "MDC 双写必须与 TTL 上下文一致");
        }
    }

    // ========================= Scenario: 因果链与 trace 并列 =========================

    @Test
    void snapshotMustExposeTraceIdSideBySideWithCausalityChain() {
        enableTracing();
        ThreadContext.set(TraceContextBridge.CORRELATION_ID_CONTEXT_KEY, "correlation-abc");
        ThreadContext.set(TraceContextBridge.CAUSATION_ID_CONTEXT_KEY, "causation-xyz");

        try (TraceContextBridge.Scope ignored = TraceContextBridge.begin("trace-side-by-side")) {
            Map<String, String> snapshot = TraceContextBridge.correlationSnapshot();
            assertEquals("trace-side-by-side", snapshot.get(TraceContextBridge.TRACE_ID_CONTEXT_KEY));
            assertEquals("correlation-abc", snapshot.get(TraceContextBridge.CORRELATION_ID_CONTEXT_KEY),
                    "trace 标识必须与 correlationId 并列可取");
            assertEquals("causation-xyz", snapshot.get(TraceContextBridge.CAUSATION_ID_CONTEXT_KEY),
                    "trace 标识必须与 causationId 并列可取");
        }
    }
}
