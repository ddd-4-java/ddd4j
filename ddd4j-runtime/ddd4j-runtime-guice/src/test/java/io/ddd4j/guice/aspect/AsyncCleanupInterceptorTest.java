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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.matcher.Matchers;
import io.ddd4j.core.context.ThreadContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 类型说明。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */
class AsyncCleanupInterceptorTest {

    @Test
    void invokeClearsThreadContextAfterProceed() {
        Injector injector = Guice.createInjector(new AbstractModule() {
            /**
             * configure() 方法。
             */
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
            /**
             * configure() 方法。
             */
            @Override
            protected void configure() {
                bindInterceptor(Matchers.any(), Matchers.any(), new AsyncCleanupInterceptor());
                bind(BoomWorker.class);
            }
        });
/**
 * ThreadContext.put() 方法。
 * @param "async-key" "async-key" 参数。
 * @param "value" "value" 参数。
 */

        ThreadContext.put("async-key", "value");
        assertThrows(IllegalStateException.class, () -> injector.getInstance(BoomWorker.class).boom());
        assertNull(ThreadContext.get("async-key"));
    }

    /**
     * 正常执行的测试组件，用于验证拦截器在方法成功返回后清理线程上下文。
     */
    public static class Worker {

        /**
         * work() 方法。
         */
        public void work() {
            assertTrue(true);
        }
    }

    /**
     * 抛出异常的测试组件，用于验证拦截器在方法异常时仍清理线程上下文。
     */
    public static class BoomWorker {

        /**
         * boom() 方法。
         */
        public void boom() {
            throw new IllegalStateException("boom");
        }
    }
}
