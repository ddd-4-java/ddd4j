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
package io.ddd4j.guice.context;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.name.Names;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 通过 <code>GuiceContext.setInjector()</code>、<code>injector()</code>、<code>GuiceContext.isInitialized()</code> 验证 <code>GuiceContext</code> 的运行行为。
 */
class GuiceContextTest {

    /**
     * 测试结束后执行 <code>GuiceContext.clear()</code>、<code>System.clearProperty()</code> 完成资源回收或状态恢复。
     */
    @AfterEach
    void tearDown() {
        GuiceContext.clear();
        System.clearProperty("guice.context.test.prop");
    }

    /**
     * 调用 <code>Guice.createInjector()</code> 执行当前夹具操作。
     *
     * @return 提供给后续契约断言的 <code>Injector</code> 结果
     */
    private Injector injector() {
        return Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(String.class).annotatedWith(Names.named("greeting")).toInstance("hello");
                bind(Runnable.class).to(CountingRunnable.class).in(com.google.inject.Singleton.class);
            }
        });
    }

    /**
     * 验证<code>GuiceContext.isInitialized()</code>为真，<code>GuiceContext.getInjector()</code>非空，<code>GuiceContext.getInstance("greeting", String.class)</code> 等于 <code>"hello"</code>。
     */
    @Test
    void setAndGetInjectorAndInstances() {
        GuiceContext.setInjector(injector());

        assertTrue(GuiceContext.isInitialized());
        assertNotNull(GuiceContext.getInjector());
        assertEquals("hello", GuiceContext.getInstance("greeting", String.class));
    }

    /**
     * 验证<code>runnables.size()</code> 等于 <code>1</code>，<code>runnables.iterator().next() instanceof CountingRunnable</code>为真。
     */
    @Test
    void getInstancesCollectsSubtypeBindings() {
        GuiceContext.setInjector(injector());

        Collection<Runnable> runnables = GuiceContext.getInstances(Runnable.class);

        assertEquals(1, runnables.size());
        assertTrue(runnables.iterator().next() instanceof CountingRunnable);
    }

    /**
     * 验证<code>GuiceContext.getProperty("guice.context.test.prop")</code> 等于 <code>"sys-value"</code>。
     */
    @Test
    void getPropertyResolvesSystemPropertyFirst() {
        System.setProperty("guice.context.test.prop", "sys-value");
        GuiceContext.setAttribute("guice.context.test.prop", "attr-value");

        assertEquals("sys-value", GuiceContext.getProperty("guice.context.test.prop"));
    }

    /**
     * 验证<code>GuiceContext.getProperty("guice.context.attr")</code> 等于 <code>"attr-value"</code>，<code>GuiceContext.getProperty("guice.context.missing", "fallback")</code> 等于 <code>"fallback"</code>，<code>GuiceContext.getProperty("guice.context.missing")</code>为空。
     */
    @Test
    void getPropertyFallsBackToAttributesAndDefault() {
        GuiceContext.setAttribute("guice.context.attr", "attr-value");

        assertEquals("attr-value", GuiceContext.getProperty("guice.context.attr"));
        assertEquals("fallback", GuiceContext.getProperty("guice.context.missing", "fallback"));
        assertNull(GuiceContext.getProperty("guice.context.missing"));
    }

    /**
     * 验证<code>GuiceContext.getAttribute("k")</code> 等于 <code>"v"</code>，<code>GuiceContext.getAttribute("missing")</code>为空。
     */
    @Test
    void attributesRoundTrip() {
        GuiceContext.setAttribute("k", "v");

        assertEquals("v", GuiceContext.getAttribute("k"));
        assertNull(GuiceContext.getAttribute("missing"));
    }

    /**
     * 验证<code>GuiceContext.getBoundTypes().contains(String.class)</code>为真，<code>GuiceContext.getBoundTypes().contains(Runnable.class)</code>为真。
     */
    @Test
    void getBoundTypesListsAllBindingTypes() {
        GuiceContext.setInjector(injector());

        assertTrue(GuiceContext.getBoundTypes().contains(String.class));
        assertTrue(GuiceContext.getBoundTypes().contains(Runnable.class));
    }

    /**
     * 验证<code>GuiceContext.isInitialized()</code>为假，<code>GuiceContext.getAttribute("k")</code>为空。
     */
    @Test
    void clearResetsState() {
        GuiceContext.setInjector(injector());
        GuiceContext.setAttribute("k", "v");
        GuiceContext.clear();

        assertFalse(GuiceContext.isInitialized());
        assertNull(GuiceContext.getAttribute("k"));
    }

    /**
     * 验证<code>captured.get()</code>非空。
     *
     * @throws Exception 等待并发操作时失败
     */
    @Test
    void getInjectorInterruptedWhileWaitingThrows() throws Exception {
        GuiceContext.clear();
        java.util.concurrent.atomic.AtomicReference<Throwable> captured =
                new java.util.concurrent.atomic.AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                GuiceContext.getInjector();
            } catch (IllegalStateException e) {
                captured.set(e);
            }
        });
        thread.start();
        thread.interrupt();
        thread.join(2000);

        assertNotNull(captured.get());
    }

    /**
     * 验证<code>runnables.isEmpty()</code>为真。
     */
    @Test
    void getInstancesSkipsBindingsThatFailInstantiation() {
        GuiceContext.setInjector(Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(BrokenRunnable.class).in(com.google.inject.Singleton.class);
            }
        }));

        Collection<Runnable> runnables = GuiceContext.getInstances(Runnable.class);

        assertTrue(runnables.isEmpty());
    }

    /**
     * 验证<code>GuiceContext.getProperty("HOME")</code> 等于 <code>home</code>。
     */
    @Test
    void getPropertyReadsEnvironmentVariables() {
        String home = System.getenv("HOME");
        if (home != null) {
            assertEquals(home, GuiceContext.getProperty("HOME"));
        }
    }

    /**
     * 验证<code>GuiceContext.getInstance("greeting", String.class)</code> 等于 <code>"hello"</code>。
     */
    @Test
    void setInjectorTwiceWarnsButOverwrites() {
        GuiceContext.setInjector(injector());
        GuiceContext.setInjector(injector());

        assertEquals("hello", GuiceContext.getInstance("greeting", String.class));
    }

    /**
     * 在当前用例所选线程上执行可观察的任务。
     */
    public static final class CountingRunnable implements Runnable {

        /**
         * 提供测试替身回调，调用时保持夹具状态。
         */
        @Override
        public void run() {
        }
    }

    /**
     * 在当前用例所选线程上执行可观察的任务。
     */
    public static final class BrokenRunnable implements Runnable {

        /**
         * 构造供当前测试使用的 <code>BrokenRunnable</code> 实例。
         */
        public BrokenRunnable() {
            throw new IllegalStateException("cannot create");
        }

        /**
         * 提供测试替身回调，调用时保持夹具状态。
         */
        @Override
        public void run() {
        }
    }

}
