/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
