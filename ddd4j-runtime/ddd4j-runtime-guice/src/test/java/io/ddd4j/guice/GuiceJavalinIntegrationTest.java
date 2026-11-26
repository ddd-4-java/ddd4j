package io.ddd4j.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.Result;
import io.ddd4j.core.cqrs.readmodel.DefaultProjectionPosition;
import io.ddd4j.guice.cqrs.GuiceJdbcProjectionPositionRepository;
import io.javalin.Javalin;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 真实 Javalin HTTP 到 Guice 命令路由；事务由执行器拥有。
 */
class GuiceJavalinIntegrationTest {
    @Test
    void httpDispatchAndExecutorOwnedTransactions() throws Exception {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:r07_tx_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        GuiceJdbcProjectionPositionRepository repository = new GuiceJdbcProjectionPositionRepository(source);
        repository.save(new DefaultProjectionPosition("r07-tx", 0L));
        TxExecutor executor = new TxExecutor(source);
        Injector injector = Guice.createInjector(Ddd4jGuiceModule.basic(), new AbstractModule() {
            @Override
            protected void configure() {
                bind(TxExecutor.class).toInstance(executor);
            }
        });
        CommandBus bus = injector.getInstance(CommandBus.class);
        Javalin app = Javalin.create(config -> {
            config.startup.showJavalinBanner = false;
            config.routes.get("/command", context -> context.result(bus.execute(new TxCommand("ok")).data().orElseThrow().toString()));
            config.events.serverStopping(() -> injector.getInstance(Ddd4jGuiceRuntime.class).close());
        }).start(0);
        try {
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.port() + "/command")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertEquals("committed", response.body());
            assertEquals(1L, repository.findByStreamId("r07-tx").orElseThrow().getNextEventNumber());
            assertSame(executor.failed, bus.execute(new TxCommand("fail")));
            assertEquals(1L, repository.findByStreamId("r07-tx").orElseThrow().getNextEventNumber());
            assertSame(executor.failure, assertThrows(IllegalStateException.class, () -> bus.execute(new TxCommand("throw"))));
            assertEquals(1L, repository.findByStreamId("r07-tx").orElseThrow().getNextEventNumber());
        } finally {
            app.stop();
        }
    }

    /**
     * 事务测试命令，不改变总线事务边界。
     */
    static final class TxCommand implements Command {
        private final String mode;

        TxCommand(String mode) {
            this.mode = mode;
        }
    }

    /**
     * 模拟应用既有事务拥有者，失败 Result 和异常均由执行器回滚。
     */
    static final class TxExecutor implements CommandExecutor<TxCommand> {
        private final JdbcDataSource source;
        private final Result<String> failed = Result.fail("rejected");
        private final IllegalStateException failure = new IllegalStateException("executor failure");

        TxExecutor(JdbcDataSource source) {
            this.source = source;
        }

        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return Set.of(TxCommand.class);
        }

        @Override
        public Result<String> execute(TxCommand command) {
            try (Connection connection = source.getConnection()) {
                connection.setAutoCommit(false);
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("UPDATE DDD4J_PROJECTION_POSITION SET next_event_number = next_event_number + 1 WHERE stream_id = 'r07-tx'");
                    if ("throw".equals(command.mode)) {
                        throw failure;
                    }
                    if ("fail".equals(command.mode)) {
                        connection.rollback();
                        return failed;
                    }
                    connection.commit();
                    return Result.ok("committed");
                } catch (RuntimeException failure) {
                    connection.rollback();
                    throw failure;
                }
            } catch (java.sql.SQLException failure) {
                throw new IllegalStateException(failure);
            }
        }
    }
}
