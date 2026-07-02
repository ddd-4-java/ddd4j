package io.ddd4j.core.cqrs.readmodel;

import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectionRunnerTest {

    @Test
    void shouldHandleEventsAndAdvancePositionAfterSuccess() {
        RecordingProjectionService projectionService = new RecordingProjectionService();
        ProjectionRunner<String> runner = new ProjectionRunner<>(
                projectionService,
                (streamId, fromEventNumber, chunkSize, eventTypes) -> new EventChunk<>(List.of("created", "deleted"), 2)
        );
        RecordingView view = new RecordingView();

        EventChunk<String> chunk = runner.runOnce(view);

        assertEquals(2, chunk.getEvents().size());
        assertEquals(2, view.handledCount.get());
        assertEquals(2L, projectionService.position.get());
    }

    @Test
    void shouldNotInvokeViewWhenChunkIsEmpty() {
        RecordingProjectionService projectionService = new RecordingProjectionService();
        ProjectionRunner<String> runner = new ProjectionRunner<>(
                projectionService,
                (streamId, fromEventNumber, chunkSize, eventTypes) -> EventChunk.empty(fromEventNumber)
        );
        RecordingView view = new RecordingView();

        runner.runOnce(view);

        assertEquals(0, view.handledCount.get());
        assertEquals(0L, projectionService.position.get());
    }

    @Test
    void shouldRunAllViews() {
        RecordingProjectionService projectionService = new RecordingProjectionService();
        ProjectionRunner<String> runner = new ProjectionRunner<>(
                projectionService,
                (streamId, fromEventNumber, chunkSize, eventTypes) -> new EventChunk<>(List.of(streamId), fromEventNumber + 1)
        );
        RecordingView first = new RecordingView("first");
        RecordingView second = new RecordingView("second");

        runner.runAll(List.of(first, second));

        assertEquals(1, first.handledCount.get());
        assertEquals(1, second.handledCount.get());
        assertEquals(2L, projectionService.position.get());
    }

    static class RecordingProjectionService implements ProjectionService {

        private final AtomicLong position = new AtomicLong();

        @Override
        public void resetProjectionPosition(String streamId) {
            position.set(0);
        }

        @Override
        public long readProjectionPosition(String streamId) {
            return position.get();
        }

        @Override
        public ProjectionPosition updateProjectionPosition(String streamId, long nextEventNumber) {
            position.set(nextEventNumber);
            return new DefaultProjectionPosition(streamId, nextEventNumber);
        }
    }

    static class RecordingView implements ProjectionView<String> {

        private final AtomicInteger handledCount = new AtomicInteger();

        private final String name;

        RecordingView() {
            this("person-list");
        }

        RecordingView(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getCron() {
            return "0/5 * * * * ?";
        }

        @Override
        public Collection<String> getEventTypes() {
            return List.of("created", "deleted");
        }

        @Override
        public void handleEvents(Collection<String> events) {
            handledCount.addAndGet(events.size());
        }
    }
}
