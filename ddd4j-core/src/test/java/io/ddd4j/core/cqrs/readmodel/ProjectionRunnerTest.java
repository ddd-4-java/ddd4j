package io.ddd4j.core.cqrs.readmodel;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProjectionRunnerTest {
    @Test
    void shouldRunChunkAndAdvancePosition() {
        ProjectionService service = new DefaultProjectionService(new InMemoryProjectionPositionRepository());
        ProjectionRunner<String> runner = new ProjectionRunner<String>(service, new FixedReader());
        RecordingView view = new RecordingView("orders", false);
        runner.runOnce(view);
        assertEquals(1, view.handled.get());
        assertEquals(1L, service.readProjectionPosition("orders"));
    }
    @Test
    void shouldPropagateRunAllFailureButIsolateRunAllIsolatedFailures() {
        ProjectionService service = new DefaultProjectionService(new InMemoryProjectionPositionRepository());
        RecordingMetrics metrics = new RecordingMetrics();
        ProjectionRunner<String> runner = new ProjectionRunner<String>(service, new FixedReader(), metrics);
        RecordingView failing = new RecordingView("failing", true);
        RecordingView succeeding = new RecordingView("succeeding", false);
        assertThrows(IllegalStateException.class, () -> runner.runAll(Arrays.asList(failing, succeeding)));
        for (int i = 0; i < ProjectionRunner.CONSECUTIVE_FAILURE_THRESHOLD; i++) {
            runner.runAllIsolated(Arrays.asList(failing, succeeding));
        }
        assertEquals(ProjectionRunner.CONSECUTIVE_FAILURE_THRESHOLD, metrics.circuitOpened.get());
        assertEquals(ProjectionRunner.CONSECUTIVE_FAILURE_THRESHOLD, succeeding.handled.get());
    }
    private static final class FixedReader implements EventChunkReader<String> {
        @Override public EventChunk<String> read(String streamId, long from, int size, Collection<String> types) {
            return new EventChunk<String>(Collections.singletonList(streamId), from + 1L);
        }
    }
    private static final class RecordingView implements ProjectionView<String> {
        private final String name; private final boolean fail; private final AtomicInteger handled = new AtomicInteger();
        private RecordingView(String name, boolean fail) { this.name = name; this.fail = fail; }
        @Override public String getName() { return name; }
        @Override public String getCron() { return "* * * * *"; }
        @Override public Collection<String> getEventTypes() { return Collections.emptyList(); }
        @Override public void handleEvents(Collection<String> events) { if (fail) throw new IllegalStateException("expected"); handled.incrementAndGet(); }
    }
    private static final class RecordingMetrics implements ProjectionMetrics {
        private final AtomicInteger circuitOpened = new AtomicInteger();
        @Override public void onCircuitOpened(String viewName, int consecutiveFailures) { circuitOpened.set(consecutiveFailures); }
    }
}
