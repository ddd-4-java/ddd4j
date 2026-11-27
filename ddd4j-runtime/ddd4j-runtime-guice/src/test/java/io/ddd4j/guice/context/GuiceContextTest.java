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

/** \u901a\u8fc7 <code>GuiceContext.setInjector()</code>\u3001<code>injector()</code>\u3001<code>GuiceContext.isInitialized()</code> \u9a8c\u8bc1 <code>GuiceContext</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class GuiceContextTest {

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>GuiceContext.clear()</code>\u3001<code>System.clearProperty()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void tearDown() {
        GuiceContext.clear();
        System.clearProperty("guice.context.test.prop");
    }

    /**
     * \u8c03\u7528 <code>Guice.createInjector()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>Injector</code> \u7ed3\u679c
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

    /** \u9a8c\u8bc1<code>GuiceContext.isInitialized()</code>\u4e3a\u771f\uff0c<code>GuiceContext.getInjector()</code>\u975e\u7a7a\uff0c<code>GuiceContext.getInstance("greeting", String.class)</code> \u7b49\u4e8e <code>"hello"</code>\u3002 */
    @Test
    void setAndGetInjectorAndInstances() {
        GuiceContext.setInjector(injector());

        assertTrue(GuiceContext.isInitialized());
        assertNotNull(GuiceContext.getInjector());
        assertEquals("hello", GuiceContext.getInstance("greeting", String.class));
    }

    /** \u9a8c\u8bc1<code>runnables.size()</code> \u7b49\u4e8e <code>1</code>\uff0c<code>runnables.iterator().next() instanceof CountingRunnable</code>\u4e3a\u771f\u3002 */
    @Test
    void getInstancesCollectsSubtypeBindings() {
        GuiceContext.setInjector(injector());

        Collection<Runnable> runnables = GuiceContext.getInstances(Runnable.class);

        assertEquals(1, runnables.size());
        assertTrue(runnables.iterator().next() instanceof CountingRunnable);
    }

    /** \u9a8c\u8bc1<code>GuiceContext.getProperty("guice.context.test.prop")</code> \u7b49\u4e8e <code>"sys-value"</code>\u3002 */
    @Test
    void getPropertyResolvesSystemPropertyFirst() {
        System.setProperty("guice.context.test.prop", "sys-value");
        GuiceContext.setAttribute("guice.context.test.prop", "attr-value");

        assertEquals("sys-value", GuiceContext.getProperty("guice.context.test.prop"));
    }

    /** \u9a8c\u8bc1<code>GuiceContext.getProperty("guice.context.attr")</code> \u7b49\u4e8e <code>"attr-value"</code>\uff0c<code>GuiceContext.getProperty("guice.context.missing", "fallback")</code> \u7b49\u4e8e <code>"fallback"</code>\uff0c<code>GuiceContext.getProperty("guice.context.missing")</code>\u4e3a\u7a7a\u3002 */
    @Test
    void getPropertyFallsBackToAttributesAndDefault() {
        GuiceContext.setAttribute("guice.context.attr", "attr-value");

        assertEquals("attr-value", GuiceContext.getProperty("guice.context.attr"));
        assertEquals("fallback", GuiceContext.getProperty("guice.context.missing", "fallback"));
        assertNull(GuiceContext.getProperty("guice.context.missing"));
    }

    /** \u9a8c\u8bc1<code>GuiceContext.getAttribute("k")</code> \u7b49\u4e8e <code>"v"</code>\uff0c<code>GuiceContext.getAttribute("missing")</code>\u4e3a\u7a7a\u3002 */
    @Test
    void attributesRoundTrip() {
        GuiceContext.setAttribute("k", "v");

        assertEquals("v", GuiceContext.getAttribute("k"));
        assertNull(GuiceContext.getAttribute("missing"));
    }

    /** \u9a8c\u8bc1<code>GuiceContext.getBoundTypes().contains(String.class)</code>\u4e3a\u771f\uff0c<code>GuiceContext.getBoundTypes().contains(Runnable.class)</code>\u4e3a\u771f\u3002 */
    @Test
    void getBoundTypesListsAllBindingTypes() {
        GuiceContext.setInjector(injector());

        assertTrue(GuiceContext.getBoundTypes().contains(String.class));
        assertTrue(GuiceContext.getBoundTypes().contains(Runnable.class));
    }

    /** \u9a8c\u8bc1<code>GuiceContext.isInitialized()</code>\u4e3a\u5047\uff0c<code>GuiceContext.getAttribute("k")</code>\u4e3a\u7a7a\u3002 */
    @Test
    void clearResetsState() {
        GuiceContext.setInjector(injector());
        GuiceContext.setAttribute("k", "v");
        GuiceContext.clear();

        assertFalse(GuiceContext.isInitialized());
        assertNull(GuiceContext.getAttribute("k"));
    }

    /**
     * \u9a8c\u8bc1<code>captured.get()</code>\u975e\u7a7a\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
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

    /** \u9a8c\u8bc1<code>runnables.isEmpty()</code>\u4e3a\u771f\u3002 */
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

    /** \u9a8c\u8bc1<code>GuiceContext.getProperty("HOME")</code> \u7b49\u4e8e <code>home</code>\u3002 */
    @Test
    void getPropertyReadsEnvironmentVariables() {
        String home = System.getenv("HOME");
        if (home != null) {
            assertEquals(home, GuiceContext.getProperty("HOME"));
        }
    }

    /** \u9a8c\u8bc1<code>GuiceContext.getInstance("greeting", String.class)</code> \u7b49\u4e8e <code>"hello"</code>\u3002 */
    @Test
    void setInjectorTwiceWarnsButOverwrites() {
        GuiceContext.setInjector(injector());
        GuiceContext.setInjector(injector());

        assertEquals("hello", GuiceContext.getInstance("greeting", String.class));
    }

    /** \u5728\u5f53\u524d\u7528\u4f8b\u6240\u9009\u7ebf\u7a0b\u4e0a\u6267\u884c\u53ef\u89c2\u5bdf\u7684\u4efb\u52a1\u3002 */
    public static final class CountingRunnable implements Runnable {

        /** {@inheritDoc} */
        @Override
        public void run() {
        }
    }

    /** \u5728\u5f53\u524d\u7528\u4f8b\u6240\u9009\u7ebf\u7a0b\u4e0a\u6267\u884c\u53ef\u89c2\u5bdf\u7684\u4efb\u52a1\u3002 */
    public static final class BrokenRunnable implements Runnable {

        /** \u6784\u9020\u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684 <code>BrokenRunnable</code> \u5b9e\u4f8b\u3002 */
        public BrokenRunnable() {
            throw new IllegalStateException("cannot create");
        }

        /** {@inheritDoc} */
        @Override
        public void run() {
        }
    }

}