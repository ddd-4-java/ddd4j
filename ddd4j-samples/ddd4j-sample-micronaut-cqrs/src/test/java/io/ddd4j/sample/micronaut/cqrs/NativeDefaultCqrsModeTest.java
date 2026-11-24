package io.ddd4j.sample.micronaut.cqrs;

import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.Result;
import io.ddd4j.sample.micronaut.cqrs.shared.SharedOrderController;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 默认原生 CQRS 应用通过运行时发现命令，持久入口和资源按需启用。 */
class NativeDefaultCqrsModeTest {

    @Test
    void defaultModeDiscoversCommandsWithoutDurableRoutes() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("micronaut.server.port", -1);
        properties.put("sample.test.command-discovery", true);

        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, properties);
             HttpClient client = HttpClient.create(server.getURL())) {
            ApplicationContext context = server.getApplicationContext();
            assertAll(
                    () -> assertEquals(Optional.of("discovered"),
                            context.getBean(CommandBus.class).<String>execute(new DiscoveryCommand()).data()),
                    () -> assertFalse(context.containsBean(DataSource.class)),
                    () -> assertFalse(context.containsBean(SharedOrderController.class)),
                    () -> assertEquals(HttpStatus.NOT_FOUND,
                            assertThrows(HttpClientResponseException.class, () -> client.toBlocking()
                                    .exchange(HttpRequest.GET("/sample/orders/missing"), String.class)).getStatus()),
                    () -> assertEquals(HttpStatus.NOT_FOUND,
                            assertThrows(HttpClientResponseException.class, () -> client.toBlocking()
                                    .exchange(HttpRequest.POST("/sample/projections/orders/run", ""), String.class))
                                    .getStatus()));

            Map<String, String> order = new HashMap<>();
            order.put("orderNo", "MICRONAUT-DEFAULT-001");
            order.put("buyerId", "buyer-1");
            order.put("buyerName", "Alice");
            assertEquals(HttpStatus.CREATED, client.toBlocking().exchange(HttpRequest.POST("/orders", order)).getStatus());
        }
    }

    static final class DiscoveryCommand implements Command {
    }

    @Singleton
    @Requires(property = "sample.test.command-discovery", value = "true")
    static class DiscoveryCommandExecutor implements CommandExecutor<DiscoveryCommand> {
        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return Collections.singleton(DiscoveryCommand.class);
        }

        @Override
        public Result<String> execute(DiscoveryCommand command) {
            return Result.ok("discovered");
        }
    }
}
