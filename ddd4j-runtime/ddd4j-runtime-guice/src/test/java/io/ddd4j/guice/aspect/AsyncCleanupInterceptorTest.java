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

import static org.junit.jupiter.api.Assertions.*;

/** \u9a8c\u8bc1 <code>AsyncCleanupInterceptor</code> \u5728<code>invokeClearsThreadContextEven</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
class AsyncCleanupInterceptorTest {

    /** \u9a8c\u8bc1<code>ThreadContext.get("async-key")</code>\u4e3a\u7a7a\u3002 */
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

    /** \u9a8c\u8bc1<code>injector.getInstance(BoomWorker.class).boom()</code> \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>ThreadContext.get("async-key")</code>\u4e3a\u7a7a\u3002 */
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

    /** \u63d0\u4f9b <code>work()</code> \u7b49\u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u72ec\u7acb\u64cd\u4f5c\u3002 */
    public static class Worker {

        /** \u4e3a\u5f02\u6b65\u62e6\u622a\u5668\u6d4b\u8bd5\u63d0\u4f9b\u6b63\u5e38\u7ed3\u675f\u5e76\u53ef\u88ab\u8c03\u7528\u7684\u65b9\u6cd5\u3002 */
        public void work() {
            assertTrue(true);
        }
    }

    /** \u63d0\u4f9b <code>boom()</code> \u7b49\u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u72ec\u7acb\u64cd\u4f5c\u3002 */
    public static class BoomWorker {

        /** \u6784\u9020 <code>IllegalStateException</code> \u4f5c\u4e3a\u5f53\u524d\u6d4b\u8bd5\u7684\u8f93\u5165\u6216\u7ed3\u679c\u3002 */
        public void boom() {
            throw new IllegalStateException("boom");
        }
    }
}
