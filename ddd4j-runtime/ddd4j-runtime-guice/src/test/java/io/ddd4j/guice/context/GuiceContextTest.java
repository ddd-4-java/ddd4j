package io.ddd4j.guice.context;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.name.Names;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiceContextTest {

    @AfterEach
    void tearDown() {
        GuiceContext.clear();
        System.clearProperty("guice.context.test.prop");
    }

    private Injector injector() {
        return Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(String.class).annotatedWith(Names.named("greeting")).toInstance("hello");
                bind(Runnable.class).to(CountingRunnable.class).in(com.google.inject.Singleton.class);
            }
        });
    }

    @Test
    void setAndGetInjectorAndInstances() {
        GuiceContext.setInjector(injector());

        assertTrue(GuiceContext.isInitialized());
        assertNotNull(GuiceContext.getInjector());
        assertEquals("hello", GuiceContext.getInstance("greeting", String.class));
    }

    @Test
    void getInstancesCollectsSubtypeBindings() {
        GuiceContext.setInjector(injector());

        Collection<Runnable> runnables = GuiceContext.getInstances(Runnable.class);

        assertEquals(1, runnables.size());
        assertTrue(runnables.iterator().next() instanceof CountingRunnable);
    }

    @Test
    void getPropertyResolvesSystemPropertyFirst() {
        System.setProperty("guice.context.test.prop", "sys-value");
        GuiceContext.setAttribute("guice.context.test.prop", "attr-value");

        assertEquals("sys-value", GuiceContext.getProperty("guice.context.test.prop"));
    }

    @Test
    void getPropertyFallsBackToAttributesAndDefault() {
        GuiceContext.setAttribute("guice.context.attr", "attr-value");

        assertEquals("attr-value", GuiceContext.getProperty("guice.context.attr"));
        assertEquals("fallback", GuiceContext.getProperty("guice.context.missing", "fallback"));
        assertNull(GuiceContext.getProperty("guice.context.missing"));
    }

    @Test
    void attributesRoundTrip() {
        GuiceContext.setAttribute("k", "v");

        assertEquals("v", GuiceContext.getAttribute("k"));
        assertNull(GuiceContext.getAttribute("missing"));
    }

    @Test
    void getBoundTypesListsAllBindingTypes() {
        GuiceContext.setInjector(injector());

        assertTrue(GuiceContext.getBoundTypes().contains(String.class));
        assertTrue(GuiceContext.getBoundTypes().contains(Runnable.class));
    }

    @Test
    void clearResetsState() {
        GuiceContext.setInjector(injector());
        GuiceContext.setAttribute("k", "v");
        GuiceContext.clear();

        assertFalse(GuiceContext.isInitialized());
        assertNull(GuiceContext.getAttribute("k"));
    }

    public static final class CountingRunnable implements Runnable {

        @Override
        public void run() {
        }
    }
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

    @Test
    void getPropertyReadsEnvironmentVariables() {
        String home = System.getenv("HOME");
        if (home != null) {
            assertEquals(home, GuiceContext.getProperty("HOME"));
        }
    }

    @Test
    void setInjectorTwiceWarnsButOverwrites() {
        GuiceContext.setInjector(injector());
        GuiceContext.setInjector(injector());

        assertEquals("hello", GuiceContext.getInstance("greeting", String.class));
    }

    public static final class BrokenRunnable implements Runnable {

        public BrokenRunnable() {
            throw new IllegalStateException("cannot create");
        }

        @Override
        public void run() {
        }
    }

}