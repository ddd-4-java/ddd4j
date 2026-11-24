package io.ddd4j.core.cqrs.readmodel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectionRunnerFailureContractTest {

    @Mock
    private ProjectionService positions;

    @Mock
    private EventChunkReader<String> reader;

    @Mock
    private ProjectionMetrics metrics;

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

    @Test
    void gapFailureDoesNotCommitSourceEndAndReleasesGuard() {
        ProjectionService service = new DefaultProjectionService(new InMemoryProjectionPositionRepository());
        service.updateProjectionPosition("orders", 10);
        ProjectionRunner<String> runner = new ProjectionRunner<>(service,
                (id, from, size, types) -> new EventChunk<>(List.of("event-11", "event-19"), 20));
        assertThatThrownBy(() -> runner.runOnce(view(new IllegalStateException("write failed"))))
                .hasMessage("write failed");
        assertThat(service.readProjectionPosition("orders")).isEqualTo(10);
        assertThat(runner.getProjectionStatus("orders").running()).isFalse();
        runner.runOnce(view(null));
        assertThat(service.readProjectionPosition("orders")).isEqualTo(20);
    }

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
        ProjectionView<String> idempotent = new ProjectionRunnerTest.TestView("orders", null, 100, List.of("created")) {
            @Override
            public void handleEvents(Collection<String> events) {
                attempts.incrementAndGet();
                effects.addAll(events);
            }
        };
        ProjectionRunner<String> runner = new ProjectionRunner<>(failing,
                (id, from, size, types) -> new EventChunk<>(List.of("event-11", "event-19"), 20));
        assertThatThrownBy(() -> runner.runOnce(idempotent)).hasMessage("checkpoint write failed");
        assertThat(persisted.readProjectionPosition("orders")).isEqualTo(10);
        assertThat(effects).hasSize(2); // 跨存储副作用不会被内核自动回滚。
        runner.runOnce(idempotent);
        assertThat(attempts).hasValue(2);
        assertThat(effects).hasSize(2); // 去重由业务消费者提供，不代表 exactly-once。
        assertThat(persisted.readProjectionPosition("orders")).isEqualTo(20);
    }

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
}
