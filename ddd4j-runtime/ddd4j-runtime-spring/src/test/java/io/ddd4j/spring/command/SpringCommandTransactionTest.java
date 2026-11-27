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
    @Test
    void currentTransactions() throws Exception {
        verify(false);
    }

    @Test
    void legacyTransactions() throws Exception {
        verify(true);
    }

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

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Transactions {
    }

    static final class WriteCommand implements Command {
        private final int id;

        WriteCommand(int id) {
            this.id = id;
        }
    }
}
