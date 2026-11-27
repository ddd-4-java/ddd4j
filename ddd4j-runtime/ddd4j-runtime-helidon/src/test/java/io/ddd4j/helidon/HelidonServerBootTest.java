package io.ddd4j.helidon;

import io.ddd4j.core.health.RuntimeReadinessRegistry;
import io.helidon.microprofile.server.Server;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** \u9a8c\u8bc1 <code>HelidonServerBoot</code> \u5728 <code>startsHelidon</code> \u7b49\u573a\u666f\u4e2d\u7684\u884c\u4e3a\u3002 */
class HelidonServerBootTest {

    /** \u9a8c\u8bc1<code>server.port() &gt; 0</code>\u4e3a\u771f\uff0c<code>jakarta.enterprise.inject.spi.CDI.current().select(RuntimeReadinessRegistry.class).get()</code>\u975e\u7a7a\u3002 */
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
