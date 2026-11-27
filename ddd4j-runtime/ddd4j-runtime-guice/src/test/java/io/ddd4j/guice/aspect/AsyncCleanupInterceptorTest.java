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

/**
 * 验证 <code>AsyncCleanupInterceptor</code> 在<code>invokeClearsThreadContextEven</code>等操作中的行为契约。
 */
class AsyncCleanupInterceptorTest {

    /**
     * 验证<code>ThreadContext.get("async-key")</code>为空。
     */
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

    /**
     * 验证<code>injector.getInstance(BoomWorker.class).boom()</code> 抛出 <code>IllegalStateException.class</code>，<code>ThreadContext.get("async-key")</code>为空。
     */
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

    /**
     * 提供 <code>work()</code> 等供当前测试使用的独立操作。
     */
    public static class Worker {

        /**
         * 为异步拦截器测试提供正常结束并可被调用的方法。
         */
        public void work() {
            assertTrue(true);
        }
    }

    /**
     * 提供 <code>boom()</code> 等供当前测试使用的独立操作。
     */
    public static class BoomWorker {

        /**
         * 构造 <code>IllegalStateException</code> 作为当前测试的输入或结果。
         */
        public void boom() {
            throw new IllegalStateException("boom");
        }
    }
}
