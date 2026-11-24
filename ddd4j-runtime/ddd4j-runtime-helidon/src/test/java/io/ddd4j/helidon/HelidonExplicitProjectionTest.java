package io.ddd4j.helidon;

import io.helidon.microprofile.server.Server;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 真正 MP 启动必须拒绝显式启用但未提供来源和持久位置的配置。 */
class HelidonExplicitProjectionTest {
    @Test
    void explicitProjectionWithoutStorageFailsNativeStartup() {
        String key = "ddd4j.cqrs.projection.enabled";
        System.setProperty(key, "true");
        Server[] server = new Server[1];
        try {
            Exception failure = assertThrows(Exception.class, () -> {
                server[0] = Server.builder().host("127.0.0.1").port(0).build();
                server[0].start();
            });
            Throwable cause = failure;
            String expected = "Explicit Helidon projection requires EventChunkReader and ProjectionPositionRepository";
            while (Objects.nonNull(cause) && !expected.equals(cause.getMessage())) {
                cause = cause.getCause();
            }
            assertNotNull(cause, failure::toString);
        } finally {
            if (Objects.nonNull(server[0])) {
                server[0].stop();
            }
            System.clearProperty(key);
        }
    }
}
