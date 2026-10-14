package io.ddd4j.core.cqrs.readmodel;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectionRunnerNullMetricsParityTest {

    @Test
    void nullMetricsUsesNoopWhileProjectionStillAdvances() {
        ProjectionService service = mock(ProjectionService.class);
        @SuppressWarnings("unchecked")
        EventChunkReader<String> reader = mock(EventChunkReader.class);
        @SuppressWarnings("unchecked")
        ProjectionView<String> view = mock(ProjectionView.class);
        when(view.getName()).thenReturn("view");
        when(view.getStreamId()).thenReturn("view");
        when(view.getChunkSize()).thenReturn(10);
        when(view.getEventTypes()).thenReturn(Collections.singletonList("created"));
        when(service.readProjectionPosition("view")).thenReturn(0L);
        EventChunk<String> chunk = new EventChunk<>(Collections.singletonList("event"), 1L);
        when(reader.read("view", 0L, 10, Collections.singletonList("created"))).thenReturn(chunk);

        ProjectionRunner<String> runner = new ProjectionRunner<>(service, reader, null);
        assertEquals(chunk, runner.runOnce(view));
        verify(view).handleEvents(Collections.singletonList("event"));
        verify(service).updateProjectionPosition("view", 1L);
    }
}
