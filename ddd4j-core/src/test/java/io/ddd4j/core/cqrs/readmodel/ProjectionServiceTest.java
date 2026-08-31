package io.ddd4j.core.cqrs.readmodel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectionServiceTest {
    @Test
    void shouldDefaultToZeroThenUpdateAndResetProjectionPosition() {
        ProjectionService service = new DefaultProjectionService(new InMemoryProjectionPositionRepository());
        assertEquals(0L, service.readProjectionPosition("orders"));
        assertEquals(12L, service.updateProjectionPosition("orders", 12L).getNextEventNumber());
        assertEquals(12L, service.readProjectionPosition("orders"));
        service.resetProjectionPosition("orders");
        assertEquals(0L, service.readProjectionPosition("orders"));
    }
}
