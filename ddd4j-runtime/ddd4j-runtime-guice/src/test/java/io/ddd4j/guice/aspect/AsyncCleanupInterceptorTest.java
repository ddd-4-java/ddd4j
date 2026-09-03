package io.ddd4j.guice.aspect;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.matcher.Matchers;
import io.ddd4j.core.context.ThreadContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncCleanupInterceptorTest {

    @Test
    void invokeClearsThreadContextAfterProceed() {
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bindInterceptor(Matchers.any(), Matchers.any(), new AsyncCleanupInterceptor());
                bind(Worker.class);
            }
        });

        ThreadContext.put("async-key", "value");
        injector.getInstance(Worker.class).work();

        assertNull(ThreadContext.get("async-key"));
    }

    @Test
    void invokeClearsThreadContextEvenWhenProceedThrows() {
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bindInterceptor(Matchers.any(), Matchers.any(), new AsyncCleanupInterceptor());
                bind(BoomWorker.class);
            }
        });

        ThreadContext.put("async-key", "value");
        assertThrows(IllegalStateException.class, () -> injector.getInstance(BoomWorker.class).boom());
        assertNull(ThreadContext.get("async-key"));
    }

    public static class Worker {

        public void work() {
            assertTrue(true);
        }
    }

    public static class BoomWorker {

        public void boom() {
            throw new IllegalStateException("boom");
        }
    }
}
