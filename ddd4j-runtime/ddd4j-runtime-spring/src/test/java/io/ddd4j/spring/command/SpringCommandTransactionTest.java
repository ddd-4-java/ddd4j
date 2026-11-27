package io.ddd4j.spring.command;

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
 * 使用真实 H2 连接与 Spring 事务代理，验证两套公开入口的原有提交和回滚契约。
 */
class SpringCommandTransactionTest {
    /**
     * \u9a8c\u8bc1\u547d\u4ee4\u6267\u884c\u5668\u91c7\u7528\u5f53\u524d\u4e8b\u52a1\u58f0\u660e\u65f6\u7684\u4f20\u64ad\u884c\u4e3a\u3002
     * @throws Exception \u51c6\u5907\u6d4b\u8bd5\u6570\u636e\u5e93\u3001\u542f\u52a8\u6846\u67b6\u6216\u6267\u884c\u4e1a\u52a1\u5408\u540c\u5931\u8d25
     */
    @Test
    void currentTransactions() throws Exception {
        verify(false);
    }

    /**
     * \u9a8c\u8bc1\u517c\u5bb9\u4e8b\u52a1\u58f0\u660e\u4ecd\u80fd\u6b63\u786e\u7ba1\u7406\u547d\u4ee4\u5904\u7406\u8fb9\u754c\u3002
     * @throws Exception \u51c6\u5907\u6d4b\u8bd5\u6570\u636e\u5e93\u3001\u542f\u52a8\u6846\u67b6\u6216\u6267\u884c\u4e1a\u52a1\u5408\u540c\u5931\u8d25
     */
    @Test
    void legacyTransactions() throws Exception {
        verify(true);
    }

    /**
     * \u8c03\u7528 <code>source.setURL()</code>\u3001<code>UUID.randomUUID()</code>\u3001<code>source.getConnection()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param legacy \u672c\u6b21\u8c03\u7528\u4f7f\u7528\u7684 <code>boolean</code> \u53c2\u6570 <code>legacy</code>
     * @throws Exception \u6267\u884c\u6570\u636e\u5e93\u64cd\u4f5c\u65f6\u5931\u8d25
     */
    private void verify(boolean legacy) throws Exception {
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

                public Result execute(WriteCommand command) {
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
                    return command.id == 2 ? Result.fail(409, "expected business failure") : Result.ok();
                }
            });
            if (legacy) {
                context.registerBean("springCommandBus", io.ddd4j.data.cqrs.spring.SpringCommandBus.class,
                        () -> new io.ddd4j.data.cqrs.spring.SpringCommandBus(context, new CommandRegistry()));
            } else {
                context.registerBean("springCommandBus", SpringCommandBus.class, () -> new SpringCommandBus(context));
            }
            context.refresh();
            CommandBus bus = context.getBean(CommandBus.class);
            assertTrue(bus.execute(new WriteCommand(1)).isSuccess());
            assertFalse(bus.execute(new WriteCommand(2)).isSuccess());
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

    /** \u63d0\u4f9b\u6295\u5f71\u5408\u540c\u6d4b\u8bd5\u6240\u9700\u7684\u4e8b\u52a1\u8fb9\u754c\u6216\u4e8b\u52a1\u66ff\u8eab\u3002 */
    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Transactions {
    }

    /** \u4f9b\u547d\u4ee4\u603b\u7ebf\u9a8c\u8bc1\u7c7b\u578b\u5339\u914d\u548c\u5206\u53d1\u7684\u547d\u4ee4\u6837\u672c\u3002 */
    static final class WriteCommand implements Command {
        /** \u6807\u8bc6\u5f53\u524d\u6d4b\u8bd5\u5bf9\u8c61\u7684\u4e3b\u952e\u6216\u4e8b\u4ef6\u6807\u8bc6\u3002 */
        private final int id;

        /**
         * \u8bbe\u7f6e <code>id</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>WriteCommand</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param id \u6807\u8bc6\u5f53\u524d\u6d4b\u8bd5\u5bf9\u8c61\u7684\u4e3b\u952e\u6216\u4e8b\u4ef6\u6807\u8bc6
         */
        WriteCommand(int id) {
            this.id = id;
        }
    }
}
