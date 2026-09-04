package io.ddd4j.web.core.health;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadinessEndpointTest {

    @Test
    void readinessReportsReady() {
        ReadinessEndpoint endpoint = new ReadinessEndpoint(() -> true);
        ReadinessResponse response = endpoint.readiness();
        assertNotNull(response);
        assertTrue(response.ready());
        assertEquals(200, response.httpStatus());
    }

    @Test
    void readinessReportsUnready() {
        ReadinessEndpoint endpoint = new ReadinessEndpoint(() -> false);
        ReadinessResponse response = endpoint.readiness();
        assertFalse(response.ready());
        assertEquals(503, response.httpStatus());
    }

    @Test
    void constructorRejectsNullSupplier() {
        assertThrows(NullPointerException.class, () -> new ReadinessEndpoint(null));
    }

    @Test
    void exposesProbePath() {
        assertEquals("/-/ready", ReadinessEndpoint.PATH);
    }
}
