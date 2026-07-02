package io.ddd4j.core.cqrs.projection;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultProjectionServiceTest {

    @Test
    void shouldReadZeroWhenPositionMissing() {
        DefaultProjectionService service = new DefaultProjectionService(new InMemoryProjectionPositionRepository());

        assertEquals(0L, service.readProjectionPosition("person-list"));
    }

    @Test
    void shouldUpdateProjectionPosition() {
        InMemoryProjectionPositionRepository repository = new InMemoryProjectionPositionRepository();
        DefaultProjectionService service = new DefaultProjectionService(repository);

        ProjectionPosition position = service.updateProjectionPosition("person-list", 12);

        assertEquals("person-list", position.getStreamId());
        assertEquals(12L, service.readProjectionPosition("person-list"));
    }

    @Test
    void shouldResetProjectionPositionToZero() {
        InMemoryProjectionPositionRepository repository = new InMemoryProjectionPositionRepository();
        DefaultProjectionService service = new DefaultProjectionService(repository);
        service.updateProjectionPosition("person-list", 12);

        service.resetProjectionPosition("person-list");

        assertEquals(0L, service.readProjectionPosition("person-list"));
    }

    @Test
    void shouldRejectNegativePosition() {
        DefaultProjectionService service = new DefaultProjectionService(new InMemoryProjectionPositionRepository());

        assertThrows(IllegalArgumentException.class, () -> service.updateProjectionPosition("person-list", -1));
    }

    static class InMemoryProjectionPositionRepository implements ProjectionPositionRepository {

        private final ConcurrentMap<String, ProjectionPosition> store = new ConcurrentHashMap<>();

        @Override
        public Optional<ProjectionPosition> findByStreamId(String streamId) {
            return Optional.ofNullable(store.get(streamId));
        }

        @Override
        public List<ProjectionPosition> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public ProjectionPosition save(ProjectionPosition position) {
            store.put(position.getStreamId(), position);
            return position;
        }

        @Override
        public void deleteByStreamId(String streamId) {
            store.remove(streamId);
        }

        @Override
        public void resetToZero(String streamId) {
            save(DefaultProjectionPosition.zero(streamId));
        }
    }
}
