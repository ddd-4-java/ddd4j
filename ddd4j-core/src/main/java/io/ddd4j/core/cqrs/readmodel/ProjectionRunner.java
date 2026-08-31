package io.ddd4j.core.cqrs.readmodel;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** 增量投影运行器：提供失败传播与隔离两种批量运行 API。 */
public class ProjectionRunner<E> {
    public static final int CONSECUTIVE_FAILURE_THRESHOLD = 5;
    private final ProjectionService projectionService; private final EventChunkReader<E> chunkReader; private final ProjectionMetrics metrics;
    private final Map<String, AtomicInteger> consecutiveFailures = new ConcurrentHashMap<String, AtomicInteger>();
    public ProjectionRunner(ProjectionService projectionService, EventChunkReader<E> chunkReader) { this(projectionService, chunkReader, NoopProjectionMetrics.INSTANCE); }
    public ProjectionRunner(ProjectionService projectionService, EventChunkReader<E> chunkReader, ProjectionMetrics metrics) {
        this.projectionService = Objects.requireNonNull(projectionService, "projectionService"); this.chunkReader = Objects.requireNonNull(chunkReader, "chunkReader");
        this.metrics = metrics == null ? NoopProjectionMetrics.INSTANCE : metrics;
    }
    public EventChunk<E> runOnce(ProjectionView<E> view) {
        ProjectionView<E> actual = validate(view); String streamId = actual.getStreamId(); long previous = projectionService.readProjectionPosition(streamId); long started = System.nanoTime(); metrics.onRunStarted(streamId);
        try {
            EventChunk<E> chunk = Objects.requireNonNull(chunkReader.read(streamId, previous, actual.getChunkSize(), actual.getEventTypes()), "chunkReader result");
            if (chunk.hasEvents()) actual.handleEvents(chunk.getEvents());
            long advance = chunk.getNextEventNumber() > previous ? chunk.getNextEventNumber() - previous : 0L;
            if (advance > 0) projectionService.updateProjectionPosition(streamId, chunk.getNextEventNumber());
            metrics.onRunCompleted(streamId, chunk.getEvents().size(), System.nanoTime() - started, advance);
            return chunk;
        } catch (RuntimeException exception) { metrics.onRunFailed(streamId, exception); throw exception; }
    }
    public void runAll(Collection<? extends ProjectionView<E>> views) {
        if (views == null || views.isEmpty()) return;
        for (ProjectionView<E> view : views) runOnce(view);
    }
    public void runAllIsolated(Collection<? extends ProjectionView<E>> views) {
        if (views == null || views.isEmpty()) return;
        for (ProjectionView<E> view : views) {
            String name = view.getName();
            try { runOnce(view); consecutiveFailures.remove(name); }
            catch (RuntimeException exception) {
                AtomicInteger counter = consecutiveFailures.get(name);
                if (counter == null) { AtomicInteger created = new AtomicInteger(); AtomicInteger previous = ((ConcurrentHashMap<String, AtomicInteger>) consecutiveFailures).putIfAbsent(name, created); counter = previous == null ? created : previous; }
                int failures = counter.incrementAndGet();
                if (failures >= CONSECUTIVE_FAILURE_THRESHOLD && failures % CONSECUTIVE_FAILURE_THRESHOLD == 0) metrics.onCircuitOpened(name, failures);
            }
        }
    }
    private ProjectionView<E> validate(ProjectionView<E> view) {
        ProjectionView<E> actual = Objects.requireNonNull(view, "view");
        if (actual.getName() == null || actual.getName().trim().isEmpty()) throw new IllegalArgumentException("view name must not be blank");
        if (actual.getStreamId() == null || actual.getStreamId().trim().isEmpty()) throw new IllegalArgumentException("view streamId must not be blank");
        if (actual.getChunkSize() <= 0) throw new IllegalArgumentException("view chunkSize must be positive");
        return actual;
    }
}
