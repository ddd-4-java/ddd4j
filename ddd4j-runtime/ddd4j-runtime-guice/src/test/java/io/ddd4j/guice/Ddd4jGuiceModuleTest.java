package io.ddd4j.guice;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import io.ddd4j.cache.CacheKit;
import io.ddd4j.core.cqrs.readmodel.ViewManager;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.guice.annotation.ddd.AnnotationFusionVerifier;
import com.google.inject.AbstractModule;
import io.ddd4j.guice.annotation.ddd.DomainService;
import io.ddd4j.guice.cqrs.GuiceViewManager;
import io.ddd4j.guice.DddAnnotationModule;
import io.ddd4j.guice.i18n.GuiceI18nProvider;
import io.ddd4j.guice.subject.GuiceSubjectProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class Ddd4jGuiceModuleTest {

    @AfterEach
    void tearDown() {
        CacheKit.unregister("guice-local-1");
        CacheKit.unregister("guice-local-2");
        CacheKit.unregister("guice-external");
    }

    @Test
    void ddd4jGuiceModuleWiresCoreServices() {
        Injector injector = Guice.createInjector(new Ddd4jGuiceModule());

        assertNotNull(injector.getInstance(ViewManager.class));
        assertSame(GuiceViewManager.class, injector.getInstance(ViewManager.class).getClass());
        assertNotNull(injector.getInstance(GuiceViewManager.class));
        assertNotNull(injector.getInstance(DomainEventPublisher.class));
        assertNotNull(injector.getInstance(SubjectProvider.class));
        assertNotNull(injector.getInstance(I18nProvider.class));
        assertNotNull(injector.getInstance(GuiceSubjectProvider.class));
        assertNotNull(injector.getInstance(io.ddd4j.guice.Ddd4jGuiceRuntime.class));
    }

    @Test
    void cacheGuiceModuleBuildsLocalAndExternalCaches() {
        CacheKit.build("guice-external", 300L);
        Injector injector = Guice.createInjector(new Ddd4jCacheGuiceModule()
                .build("guice-local-1", 300L)
                .build("guice-local-2", builder -> {
                    builder.maximumSize(10);
                    return builder;
                })
                .register("guice-external", CacheKit.getCache("guice-external")));

        assertNotNull(injector);
        assertNotNull(CacheKit.getCache("guice-local-1"));
        assertNotNull(CacheKit.getCache("guice-local-2"));
        assertNotNull(CacheKit.getCache("guice-external"));
    }

    @Test
    void cryptoAndLogsModulesProvideBindings() {
        Injector injector = Guice.createInjector(new Ddd4jCryptoGuiceModule(), new Ddd4jLogsGuiceModule());

        assertNotNull(injector.getInstance(io.ddd4j.data.crypto.CryptoProperties.class));
        assertNotNull(injector.getInstance(io.ddd4j.data.logs.aspect.ApiOperationLogProvider.class));
    }

    @Test
    void annotationFusionVerifierMainPasses() {
        assertDoesNotThrow(() -> AnnotationFusionVerifier.main(new String[0]));
    }

    @Test
    void i18nProviderResolvesBundles() {
        GuiceI18nProvider provider = new GuiceI18nProvider();

        assertEquals("Plain message", provider.getMessage("plain"));
        assertEquals("Hello World", provider.getMessage("greeting", "World"));
        assertEquals("missing.key", provider.getMessage("missing.key"));
        assertEquals(null, provider.getMessage(null));
    }

    @Test
    void domainEventPublisherPostsToEventBus() {
        com.google.common.eventbus.EventBus bus = new com.google.common.eventbus.EventBus();
        java.util.concurrent.atomic.AtomicReference<Object> received = new java.util.concurrent.atomic.AtomicReference<>();
        bus.register(new Object() {
            @com.google.common.eventbus.Subscribe
            public void on(io.ddd4j.core.ddd.event.DomainEvent<io.ddd4j.core.ddd.event.EntityId> event) {
                received.set(event);
            }
        });
        io.ddd4j.guice.event.GuiceDomainEventPublisher direct =
                new io.ddd4j.guice.event.GuiceDomainEventPublisher(bus);
        io.ddd4j.core.ddd.event.DomainEvent<io.ddd4j.core.ddd.event.EntityId> event =
                new io.ddd4j.core.ddd.event.DomainEvent<io.ddd4j.core.ddd.event.EntityId>() {
                };
        direct.publish(event);

        assertSame(event, received.get());
        direct.publish(null);
        assertSame(event, received.get());
    }

    @Test
    void dddAnnotationModuleScansAndBindsAnnotatedBeans() {
        Injector injector = Guice.createInjector(new DddAnnotationModule(true, "io.ddd4j.guice"));

        assertNotNull(injector.getInstance(TestDomainService.class));
        assertSame(injector.getInstance(TestDomainService.class),
                injector.getInstance(TestDomainService.class));
    }

    @Test
    void dddAnnotationModuleWithScanDisabledIsSafe() {
        Injector injector = Guice.createInjector(new DddAnnotationModule(false, "io.ddd4j.guice"));

        assertNotNull(injector);
    }

    @DomainService
    public static class TestDomainService {
    }

    public interface MapperStub {
    }

    public static class RepositoryStub {
    }

    public static final class NoopDataSource implements javax.sql.DataSource {

        @Override
        public java.sql.Connection getConnection() {
            return null;
        }

        @Override
        public java.sql.Connection getConnection(String username, String password) {
            return null;
        }

        @Override
        public java.io.PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(java.io.PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public java.util.logging.Logger getParentLogger() {
            return java.util.logging.Logger.getGlobal();
        }

        @Override
        public <T> T unwrap(Class<T> iface) {
            return null;
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
    @Test
    void providesMethodsInstantiateOnDemand() {
        Injector injector = Guice.createInjector(new Ddd4jGuiceModule());

        assertNotNull(injector.getInstance(com.google.common.eventbus.EventBus.class));
        assertNotNull(injector.getInstance(io.ddd4j.core.cqrs.readmodel.ProjectionService.class));
        assertNotNull(injector.getInstance(com.google.inject.Key.get(
                new com.google.inject.TypeLiteral<io.ddd4j.core.cqrs.readmodel.ProjectionRunner<Object>>() {
                })));
        assertNotNull(injector.getInstance(io.ddd4j.core.cqrs.command.CommandBus.class));
    }

    @Test
    void subjectProviderResolvesOptionalBinding() {
        io.ddd4j.guice.subject.GuiceSubjectProvider provider = new io.ddd4j.guice.subject.GuiceSubjectProvider();
        assertNull(provider.getSubject());

        io.ddd4j.core.subject.Subject subject = (io.ddd4j.core.subject.Subject)
                java.lang.reflect.Proxy.newProxyInstance(
                        io.ddd4j.core.subject.Subject.class.getClassLoader(),
                        new Class[]{io.ddd4j.core.subject.Subject.class},
                        (proxy, method, args) -> null);
        Injector withSubject = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(io.ddd4j.core.subject.Subject.class).toInstance(subject);
            }
        });
        io.ddd4j.guice.subject.GuiceSubjectProvider injected = new io.ddd4j.guice.subject.GuiceSubjectProvider();
        withSubject.injectMembers(injected);
        assertSame(subject, injected.getSubject());

        io.ddd4j.guice.subject.GuiceSubjectProvider emptyProvider = new io.ddd4j.guice.subject.GuiceSubjectProvider();
        Guice.createInjector().injectMembers(emptyProvider);
        assertNull(emptyProvider.getSubject());
    }

    @Test
    void dddAnnotationModuleBindsTopLevelAnnotatedBean() {
        Injector injector = Guice.createInjector(
                new DddAnnotationModule(true, "io.ddd4j.guice.testbeans"));

        io.ddd4j.guice.testbeans.OrderService first =
                injector.getInstance(io.ddd4j.guice.testbeans.OrderService.class);
        assertSame(first, injector.getInstance(io.ddd4j.guice.testbeans.OrderService.class));
    }

    @Test
    void commandBusCollectsCommandExecutors() {
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                install(new Ddd4jGuiceModule());
                bind(ExecutorStub.class);
            }
        });

        assertNotNull(injector.getInstance(io.ddd4j.core.cqrs.command.CommandBus.class));
    }

    @Test
    void cacheModuleSupportsDefaultTypeOverride() {
        Guice.createInjector(new Ddd4jCacheGuiceModule()
                .setDefaultType(io.ddd4j.cache.CacheKit.LocalCacheType.GUAVA)
                .build("guice-type-test", 60L));

        assertNotNull(io.ddd4j.cache.CacheKit.getCache("guice-type-test"));
        io.ddd4j.cache.CacheKit.unregister("guice-type-test");
    }

    @Test
    void dddAnnotationModuleOneArgConstructorEnablesScan() {
        Injector injector = Guice.createInjector(
                new DddAnnotationModule("io.ddd4j.guice.testbeans"));

        assertNotNull(injector.getInstance(io.ddd4j.guice.testbeans.OrderService.class));
    }

    public static class ExecutorStub implements io.ddd4j.core.cqrs.command.CommandExecutor<io.ddd4j.core.cqrs.command.Command> {

        @Override
        public java.util.Set<Class<? extends io.ddd4j.core.cqrs.command.Command>> supportedCommands() {
            return java.util.Collections.emptySet();
        }

        @Override
        public io.ddd4j.core.cqrs.command.Result execute(io.ddd4j.core.cqrs.command.Command command) {
            return io.ddd4j.core.cqrs.command.Result.ok();
        }
    }

}