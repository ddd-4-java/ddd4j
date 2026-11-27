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
    /**
     * \u9a8c\u8bc1<code>response.statusCode()</code> \u7b49\u4e8e <code>200</code>\uff0c<code>response.body()</code> \u7b49\u4e8e <code>"committed"</code>\uff0c<code>bus.execute(new TxCommand("throw"))</code> \u629b\u51fa <code>IllegalStateException.class</code>\u3002
     * @throws Exception \u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
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
        /** \u5f53\u524d\u7528\u4f8b\u7528\u4e8e\u5207\u6362\u9274\u6743\u3001\u6295\u5f71\u6216\u5904\u7406\u884c\u4e3a\u7684\u6d4b\u8bd5\u6a21\u5f0f\u3002 */
        private final String mode;

        /**
         * \u8bbe\u7f6e <code>mode</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>TxCommand</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param mode \u7528\u4e8e\u521d\u59cb\u5316 <code>TxCommand</code> \u7684 <code>String</code> \u503c
         */
        TxCommand(String mode) {
            this.mode = mode;
        }
    }

    /**
     * 模拟应用既有事务拥有者，失败 Result 和异常均由执行器回滚。
     */
    static final class TxExecutor implements CommandExecutor<TxCommand> {
        /** \u5f53\u524d\u8f6c\u6362\u6216\u590d\u5236\u6d4b\u8bd5\u4f7f\u7528\u7684\u8f93\u5165\u5bf9\u8c61\u3002 */
        private final JdbcDataSource source;
        /** \u8bb0\u5f55\u53d7\u6d4b\u8c03\u7528\u6700\u7ec8\u662f\u5426\u5931\u8d25\u3002 */
        private final Result<String> failed = Result.fail("rejected");
        /** \u5f53\u524d\u5931\u8d25\u8def\u5f84\u6240\u7528\u6216\u89c2\u5bdf\u5230\u7684\u539f\u59cb\u5f02\u5e38\u3002 */
        private final IllegalStateException failure = new IllegalStateException("executor failure");

        /**
         * \u8bbe\u7f6e <code>source</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>TxExecutor</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param source \u6d4b\u8bd5\u4e2d\u5f85\u8f6c\u6362\u3001\u8bfb\u53d6\u6216\u4f20\u64ad\u7684\u6570\u636e\u6765\u6e90
         */
        TxExecutor(JdbcDataSource source) {
            this.source = source;
        }

        /**
         * 声明当前处理器可以接收的命令类型。
         *
         * <p>测试夹具会调用 <code>Set.of(TxCommand.class)</code>。</p>
         *
         * @return 实际返回<code>Set.of(TxCommand.class)</code>
         */
        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return Set.of(TxCommand.class);
        }

        /**
         * 用当前处理器执行传入命令或操作。
         *
         * <p>测试夹具会调用 <code>source.getConnection()</code>、<code>connection.setAutoCommit(false)</code>。</p>
         *
         * @param command 命令对象
         * @return 根据执行路径返回<code>failed</code>、<code>Result.ok("committed")</code>
         */
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
