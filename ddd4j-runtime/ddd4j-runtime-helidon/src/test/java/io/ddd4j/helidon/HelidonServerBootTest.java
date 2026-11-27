package io.ddd4j.helidon;

import io.ddd4j.core.health.RuntimeReadinessRegistry;
import io.helidon.microprofile.server.Server;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 <code>HelidonServerBoot</code> 在 <code>startsHelidon</code> 等场景中的行为。
 */
class HelidonServerBootTest {

    /**
     * 验证<code>server.port() &gt; 0</code>为真，<code>jakarta.enterprise.inject.spi.CDI.current().select(RuntimeReadinessRegistry.class).get()</code>非空。
     */
    @Test
    void startsHelidonWithDdd4jCdiExtension() {
        Server server = Server.builder().host("127.0.0.1").port(0).build();
        try {
            server.start();
            assertTrue(server.port() > 0);
            assertNotNull(jakarta.enterprise.inject.spi.CDI.current().select(RuntimeReadinessRegistry.class).get());
        } finally {
            server.stop();
        }
    }
}
