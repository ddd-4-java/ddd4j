package io.ddd4j.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.ddd4j.core.api.R;
import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.readmodel.DefaultProjectionPosition;
import io.ddd4j.guice.cqrs.GuiceJdbcProjectionPositionRepository;
import io.javalin.Javalin;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.HttpURLConnection;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 真实 Javalin HTTP 到 Guice 命令路由；事务由执行器拥有。
 */
class GuiceJavalinIntegrationTest {
    /**
     * 验证<code>response.statusCode()</code> 等于 <code>200</code>，<code>response.body()</code> 等于 <code>"committed"</code>，<code>bus.execute(new TxCommand("throw"))</code> 抛出 <code>IllegalStateException.class</code>。
     *
     * @throws Exception 发起测试请求时失败
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
        Javalin app = Javalin.create(config -> config.showJavalinBanner = false);
        app.get("/command", context -> context.result(bus.<String>execute(new TxCommand("ok")).getData()));
        app.events(events -> events.serverStopping(() -> injector.getInstance(Ddd4jGuiceRuntime.class).close()));
        app.start(0);
        try {
            HttpURLConnection response = (HttpURLConnection) URI.create(
                    "http://127.0.0.1:" + app.port() + "/command").toURL().openConnection();
            try {
                assertEquals(200, response.getResponseCode());
                try (java.io.InputStream input = response.getInputStream()) {
                    assertEquals("committed", new String(com.google.common.io.ByteStreams.toByteArray(input),
                            java.nio.charset.StandardCharsets.UTF_8));
                }
            } finally {
                response.disconnect();
            }
            assertEquals(1L, repository.findByStreamId("r07-tx").orElseThrow(IllegalStateException::new).getNextEventNumber());
            assertSame(executor.failed, bus.execute(new TxCommand("fail")));
            assertEquals(1L, repository.findByStreamId("r07-tx").orElseThrow(IllegalStateException::new).getNextEventNumber());
            assertSame(executor.failure, assertThrows(IllegalStateException.class, () -> bus.execute(new TxCommand("throw"))));
            assertEquals(1L, repository.findByStreamId("r07-tx").orElseThrow(IllegalStateException::new).getNextEventNumber());
        } finally {
            app.stop();
        }
    }

    /**
     * 事务测试命令，不改变总线事务边界。
     */
    static final class TxCommand implements Command {
        /**
         * 当前用例用于切换鉴权、投影或处理行为的测试模式。
         */
        private final String mode;

        /**
         * 设置 <code>mode</code>，用于构造 <code>TxCommand</code> 测试实例。
         *
         * @param mode 用于初始化 <code>TxCommand</code> 的 <code>String</code> 值
         */
        TxCommand(String mode) {
            this.mode = mode;
        }
    }

    /**
     * 模拟应用既有事务拥有者，失败 R 和异常均由执行器回滚。
     */
    static final class TxExecutor implements CommandExecutor<TxCommand> {
        /**
         * 当前转换或复制测试使用的输入对象。
         */
        private final JdbcDataSource source;
        /**
         * 记录受测调用最终是否失败。
         */
        private final R<String> failed = R.fail("rejected");
        /**
         * 当前失败路径所用或观察到的原始异常。
         */
        private final IllegalStateException failure = new IllegalStateException("executor failure");

        /**
         * 设置 <code>source</code>，用于构造 <code>TxExecutor</code> 测试实例。
         *
         * @param source 测试中待转换、读取或传播的数据来源
         */
        TxExecutor(JdbcDataSource source) {
            this.source = source;
        }

        /**
         * 声明当前处理器可以接收的命令类型。
         *
         * <p>测试夹具会调用 <code>java.util.Collections.singleton(TxCommand.class)</code>。</p>
         *
         * @return 实际返回<code>java.util.Collections.singleton(TxCommand.class)</code>
         */
        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return java.util.Collections.singleton(TxCommand.class);
        }

        /**
         * 用当前处理器执行传入命令或操作。
         *
         * <p>测试夹具会调用 <code>source.getConnection()</code>、<code>connection.setAutoCommit(false)</code>。</p>
         *
         * @param command 命令对象
         * @return 根据执行路径返回<code>failed</code>、<code>R.ok("committed")</code>
         */
        @Override
        public R<String> execute(TxCommand command) {
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
                    return R.ok("committed");
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
