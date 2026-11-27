package io.ddd4j.core.cqrs.readmodel;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** \u9a8c\u8bc1 <code>ProjectionRunnerFailureContract</code> \u5728 <code>positionReadFailureReportsMetric</code>\u3001<code>handlerFailureReportsMetric</code> \u7b49\u573a\u666f\u4e2d\u7684\u884c\u4e3a\u3002 */
@ExtendWith(MockitoExtension.class)
class ProjectionRunnerFailureContractTest {

    /** \u7528\u4e8e\u9694\u79bb <code>ProjectionService</code> \u8c03\u7528\u5e76\u8bb0\u5f55\u4ea4\u4e92\u7684\u6d4b\u8bd5\u66ff\u8eab\u3002 */
    @Mock
    private ProjectionService positions;

    /** \u7528\u4e8e\u9694\u79bb <code>EventChunkReader&lt;String&gt;</code> \u8c03\u7528\u5e76\u8bb0\u5f55\u4ea4\u4e92\u7684\u6d4b\u8bd5\u66ff\u8eab\u3002 */
    @Mock
    private EventChunkReader<String> reader;

    /** \u7528\u4e8e\u9694\u79bb <code>ProjectionMetrics</code> \u8c03\u7528\u5e76\u8bb0\u5f55\u4ea4\u4e92\u7684\u6d4b\u8bd5\u66ff\u8eab\u3002 */
    @Mock
    private ProjectionMetrics metrics;

    /** \u9a8c\u8bc1\u672a\u8c03\u7528 <code>reader</code>\u3002 */
    @Test
    void positionReadFailureReportsMetricWithoutAdvancing() {
        IllegalStateException failure = new IllegalStateException("position unavailable");
        when(positions.readProjectionPosition("orders")).thenThrow(failure);
        ProjectionRunner<String> runner = new ProjectionRunner<>(positions, reader, metrics);

        assertThatThrownBy(() -> runner.runOnce(view(null))).isSameAs(failure);

        verify(metrics).onRunStarted("orders");
        verify(metrics).onRunFailed("orders", failure);
        verify(positions, never()).updateProjectionPosition(any(), anyLong());
        verifyNoInteractions(reader);
    }

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>positions.readProjectionPosition()</code>\u3001<code>reader.read()</code>\u3001<code>eq()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void handlerFailureReportsMetricWithoutAdvancing() {
        IllegalStateException failure = new IllegalStateException("handler failed");
        when(positions.readProjectionPosition("orders")).thenReturn(0L);
        when(reader.read(eq("orders"), eq(0L), eq(100), any()))
                .thenReturn(new EventChunk<>(Collections.singletonList("e1"), 1L));
        ProjectionRunner<String> runner = new ProjectionRunner<>(positions, reader, metrics);

        assertThatThrownBy(() -> runner.runOnce(view(failure))).isSameAs(failure);

        verify(metrics).onRunFailed("orders", failure);
        verify(positions, never()).updateProjectionPosition(any(), anyLong());
    }

    /**
     * \u6784\u9020 <code>ProjectionView&lt;String&gt;</code> \u4f5c\u4e3a\u5f53\u524d\u6d4b\u8bd5\u7684\u8f93\u5165\u6216\u7ed3\u679c\u3002
     * @param failure \u89e6\u53d1\u5f53\u524d\u6d4b\u8bd5\u9519\u8bef\u5904\u7406\u8def\u5f84\u7684\u5931\u8d25
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>ProjectionView&lt;String&gt;</code> \u7ed3\u679c
     */
    private static ProjectionView<String> view(RuntimeException failure) {
        return new ProjectionView<String>() {
            @Override
            public String getName() {
                return "orders";
            }

            @Override
            public String getCron() {
                return "0/5 * * * * ?";
            }

            @Override
            public Collection<String> getEventTypes() {
                return Collections.singletonList("OrderCreated");
            }

            @Override
            public void handleEvents(Collection<String> events) {
                if (Objects.nonNull(failure)) {
                    throw failure;
                }
            }
        };
    }



    /**
     * 验证<code>service.readProjectionPosition("orders")</code> 等于 <code>10</code>，<code>runner.getProjectionStatus("orders").running()</code>为假，<code>service.readProjectionPosition("orders")</code> 等于 <code>20</code>。
     */
    @Test
    void gapFailureDoesNotCommitSourceEndAndReleasesGuard() {
        ProjectionService service = new DefaultProjectionService(new InMemoryProjectionPositionRepository());
        service.updateProjectionPosition("orders", 10);
        ProjectionRunner<String> runner = new ProjectionRunner<>(service,
                (id, from, size, types) -> new EventChunk<>(java.util.Arrays.asList("event-11", "event-19"), 20));
        assertThatThrownBy(() -> runner.runOnce(view(new IllegalStateException("write failed"))))
                .hasMessage("write failed");
        assertThat(service.readProjectionPosition("orders")).isEqualTo(10);
        assertThat(runner.getProjectionStatus("orders").running()).isFalse();
        runner.runOnce(view(null));
        assertThat(service.readProjectionPosition("orders")).isEqualTo(20);
    }

    /**
     * 验证<code>persisted.readProjectionPosition("orders")</code> 等于 <code>10</code>，<code>effects</code> 的大小为 <code>2</code>，<code>persisted.readProjectionPosition("orders")</code> 等于 <code>20</code>。
     */
    @Test
    void checkpointFailureReplaysBlockAndConsumerIdempotencyAvoidsDuplicateEffects() {
        InMemoryProjectionPositionRepository repository = new InMemoryProjectionPositionRepository();
        DefaultProjectionService persisted = new DefaultProjectionService(repository);
        persisted.updateProjectionPosition("orders", 10);
        AtomicBoolean failSave = new AtomicBoolean(true);
        ProjectionService failing = new DefaultProjectionService(repository) {
            @Override
            public ProjectionPosition updateProjectionPosition(String id, long next) {
                if (failSave.getAndSet(false)) {
                    throw new IllegalStateException("checkpoint write failed");
                }
                return super.updateProjectionPosition(id, next);
            }
        };
        Set<String> effects = new HashSet<>();
        java.util.concurrent.atomic.AtomicInteger attempts = new java.util.concurrent.atomic.AtomicInteger();
        ProjectionView<String> idempotent = new ProjectionRunnerTest.TestView("orders", null, 100, java.util.Arrays.asList("created")) {
            @Override
            public void handleEvents(Collection<String> events) {
                attempts.incrementAndGet();
                effects.addAll(events);
            }
        };
        ProjectionRunner<String> runner = new ProjectionRunner<>(failing,
                (id, from, size, types) -> new EventChunk<>(java.util.Arrays.asList("event-11", "event-19"), 20));
        assertThatThrownBy(() -> runner.runOnce(idempotent)).hasMessage("checkpoint write failed");
        assertThat(persisted.readProjectionPosition("orders")).isEqualTo(10);
        assertThat(effects).hasSize(2); // 跨存储副作用不会被内核自动回滚。
        runner.runOnce(idempotent);
        assertThat(attempts).hasValue(2);
        assertThat(effects).hasSize(2); // 去重由业务消费者提供，不代表 exactly-once。
        assertThat(persisted.readProjectionPosition("orders")).isEqualTo(20);
    }
}
