package io.ddd4j.helidon;

import io.ddd4j.core.health.RuntimeReadinessRegistry;
import io.helidon.microprofile.server.Server;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HelidonServerBootTest {

    @Test
    void startsHelidonWithDdd4jCdiExtension() {
        Server server = Server.builder().host("127.0.0.1").port(0).build();
        try {
            server.start();
            assertTrue(server.port() > 0);
            assertNotNull(server.cdiContainer().select(RuntimeReadinessRegistry.class).get());
        } finally {
            server.stop();
        }
    }
}
