package io.ddd4j.spring.command;

import io.ddd4j.core.api.R;
import io.ddd4j.core.cqrs.command.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 使用真实 H2 连接与 Spring 事务代理，验证容器发现与显式注册表两种装配方式的提交和回滚契约。
 */
class SpringCommandTransactionTest {
    /**
     * 验证命令执行器采用当前事务声明时的传播行为。
     *
     * @throws Exception 准备测试数据库、启动框架或执行业务合同失败
     */
    @Test
    void currentTransactions() throws Exception {
        verify(false);
    }

    /**
     * 验证兼容事务声明仍能正确管理命令处理边界。
     *
     * @throws Exception 准备测试数据库、启动框架或执行业务合同失败
     */
    @Test
    void registryTransactions() throws Exception {
        verify(true);
    }

    /**
     * 调用 <code>source.setURL()</code>、<code>UUID.randomUUID()</code>、<code>source.getConnection()</code> 执行当前夹具操作。
     *
     * @param explicitRegistry 为真时从显式命令注册表装配，为假时在容器刷新后发现执行器
     * @throws Exception 执行数据库操作时失败
     */
    private void verify(boolean explicitRegistry) throws Exception {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        try (Connection connection = source.getConnection()) {
            connection.createStatement().execute("create table tx_probe (id integer primary key)");
        }
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(Transactions.class);
            context.registerBean("transactionManager", DataSourceTransactionManager.class,
                    () -> new DataSourceTransactionManager(source));
            context.registerBean("executor", CommandExecutor.class, () -> new CommandExecutor<WriteCommand>() {
                public Set<Class<? extends Command>> supportedCommands() {
                    return Collections.singleton(WriteCommand.class);
                }

                public R<?> execute(WriteCommand command) {
                    assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                    Connection connection = DataSourceUtils.getConnection(source);
                    try {
                        connection.createStatement().executeUpdate("insert into tx_probe values (" + command.id + ")");
                    } catch (SQLException failure) {
                        throw new IllegalStateException(failure);
                    } finally {
                        DataSourceUtils.releaseConnection(connection, source);
                    }
                    if (command.id == 3) {
                        throw new IllegalArgumentException("rollback");
                    }
                    return command.id == 2 ? R.fail(409, "expected business failure") : R.ok();
                }
            });
            if (explicitRegistry) {
                context.registerBean("springCommandBus", SpringCommandBus.class,
                        () -> new SpringCommandBus(context, new CommandRegistry()));
            } else {
                context.registerBean("springCommandBus", SpringCommandBus.class, () -> new SpringCommandBus(context));
            }
            context.refresh();
            CommandBus bus = context.getBean(CommandBus.class);
            assertTrue(bus.execute(new WriteCommand(1)).isOk());
            assertFalse(bus.execute(new WriteCommand(2)).isOk());
            assertThrows(IllegalArgumentException.class, () -> bus.execute(new WriteCommand(3)));
            try (Connection connection = source.getConnection();
                 ResultSet rows = connection.createStatement().executeQuery("select id from tx_probe order by id")) {
                assertTrue(rows.next());
                assertEquals(1, rows.getInt(1));
                assertTrue(rows.next());
                assertEquals(2, rows.getInt(1));
                assertFalse(rows.next());
            }
        }
    }

    /**
     * 提供投影合同测试所需的事务边界或事务替身。
     */
    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Transactions {
    }

    /**
     * 供命令总线验证类型匹配和分发的命令样本。
     */
    static final class WriteCommand implements Command {
        /**
         * 标识当前测试对象的主键或事件标识。
         */
        private final int id;

        /**
         * 设置 <code>id</code>，用于构造 <code>WriteCommand</code> 测试实例。
         *
         * @param id 标识当前测试对象的主键或事件标识
         */
        WriteCommand(int id) {
            this.id = id;
        }
    }
}
