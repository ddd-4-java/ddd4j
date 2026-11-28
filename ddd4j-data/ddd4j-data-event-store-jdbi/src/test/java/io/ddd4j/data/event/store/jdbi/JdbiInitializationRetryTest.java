package io.ddd4j.data.event.store.jdbi;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.SqlLogger;
import org.jdbi.v3.core.statement.SqlStatements;
import org.jdbi.v3.core.statement.StatementContext;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies that a failed lazy schema creation can be retried. */
class JdbiInitializationRetryTest {

    /** \u9a8c\u8bc1<code>store.readAll(0, 10)</code> \u629b\u51fa <code>RuntimeException.class</code>\uff0c<code>store.readAll(0, 10).isEmpty()</code>\u4e3a\u771f\uff0c<code>attempts.get()</code> \u7b49\u4e8e <code>2</code>\u3002 */
    @Test
    void failedSchemaCreationShouldRetryOnNextOperation() {
        Jdbi jdbi = Jdbi.create("jdbc:h2:mem:jdbi_init_retry_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        AtomicInteger attempts = new AtomicInteger();
        jdbi.getConfig(SqlStatements.class).setSqlLogger(new SqlLogger() {
            @Override
            public void logBeforeExecution(StatementContext context) {
                if (context.getRenderedSql().toUpperCase().startsWith("CREATE TABLE")
                        && attempts.incrementAndGet() == 1) {
                    throw new IllegalStateException("temporary schema creation failure");
                }
            }
        });
        JdbiEventStore store = new JdbiEventStore(jdbi);

        assertThrows(RuntimeException.class, () -> store.readAll(0, 10));
        assertTrue(store.readAll(0, 10).isEmpty());
        assertEquals(2, attempts.get());
    }
}
