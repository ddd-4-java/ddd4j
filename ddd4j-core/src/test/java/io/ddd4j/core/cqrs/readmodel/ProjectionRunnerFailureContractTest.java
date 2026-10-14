package io.ddd4j.core.cqrs.readmodel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

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
