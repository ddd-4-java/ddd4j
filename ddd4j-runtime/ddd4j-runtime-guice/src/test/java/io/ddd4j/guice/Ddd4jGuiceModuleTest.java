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
package io.ddd4j.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import io.ddd4j.cache.CacheKit;
import io.ddd4j.core.cqrs.readmodel.ViewManager;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.guice.annotation.ddd.AnnotationFusionVerifier;
import io.ddd4j.guice.annotation.ddd.DomainService;
import io.ddd4j.guice.cqrs.GuiceViewManager;
import io.ddd4j.guice.i18n.GuiceI18nProvider;
import io.ddd4j.guice.subject.GuiceSubjectProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** \u9a8c\u8bc1 <code>Ddd4jGuiceModule</code> \u5728<code>dddAnnotationModule</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
class Ddd4jGuiceModuleTest {

    /** \u9a8c\u8bc1<code>handled</code> \u7b49\u4e8e <code>java.util.Collections.singletonList("event")</code>\u3002 */
    @Test
    void projectionRunnerMustUseExplicitReaderBinding() {
        io.ddd4j.core.cqrs.readmodel.EventChunkReader<Object> reader = (stream, from, size, types) ->
                new io.ddd4j.core.cqrs.readmodel.EventChunk<Object>(
                        java.util.Collections.singletonList("event"), from + 1);
        Injector injector = Guice.createInjector(new Ddd4jGuiceModule(), new AbstractModule() {
            @Override
            protected void configure() {
                bind(new com.google.inject.TypeLiteral<io.ddd4j.core.cqrs.readmodel.EventChunkReader<Object>>() {
                })
                        .toInstance(reader);
            }
        });
        try {
            java.util.List<Object> handled = new java.util.ArrayList<>();
            io.ddd4j.core.cqrs.readmodel.ProjectionRunner<Object> runner = injector.getInstance(
                    Key.get(new com.google.inject.TypeLiteral<io.ddd4j.core.cqrs.readmodel.ProjectionRunner<Object>>() {
                    }));
            runner.runOnce(new io.ddd4j.core.cqrs.readmodel.ProjectionView<Object>() {
                public String getName() {
                    return "custom-reader";
                }

                public String getCron() {
                    return "* * * * *";
                }

                public java.util.Collection<String> getEventTypes() {
                    return java.util.Collections.emptyList();
                }

                public void handleEvents(java.util.Collection<Object> events) {
                    handled.addAll(events);
                }
            });
            assertEquals(java.util.Collections.singletonList("event"), handled);
            assertEquals(1L, injector.getInstance(io.ddd4j.core.cqrs.readmodel.ProjectionService.class)
                    .readProjectionPosition("custom-reader"));
        } finally {
            injector.getInstance(Ddd4jGuiceRuntime.class).close();
        }
    }

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>CacheKit.unregister()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void tearDown() {
        CacheKit.unregister("guice-local-1");
        CacheKit.unregister("guice-local-2");
        CacheKit.unregister("guice-external");
    }

    /** \u9a8c\u8bc1<code>injector.getInstance(ViewManager.class)</code>\u975e\u7a7a\uff0c<code>injector.getInstance(ViewManager.class).getClass()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>GuiceViewManager.class</code>\uff0c<code>injector.getInstance(io.ddd4j.guice.Ddd4jGuiceRuntime.class)</code>\u975e\u7a7a\u3002 */
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

    /** \u9a8c\u8bc1<code>injector</code>\u975e\u7a7a\uff0c<code>CacheKit.getCache("guice-local-1")</code>\u975e\u7a7a\uff0c<code>CacheKit.getCache("guice-external")</code>\u975e\u7a7a\u3002 */
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

    /** \u9a8c\u8bc1<code>injector</code>\u975e\u7a7a\uff0c<code>injector.getInstance(RepositoryStub.class)</code>\u975e\u7a7a\uff0c<code>injector.getInstance(RepositoryStub.class)</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>injector.getInstance(RepositoryStub.class)</code>\u3002 */
    @Test
    void mybatisGuiceModuleBindsRepositories() {
        Injector injector = Guice.createInjector(new Ddd4jMybatisGuiceModule(new NoopDataSource())
                .addMapper(MapperStub.class)
                .bindRepository(RepositoryStub.class, MapperStub.class));

        assertNotNull(injector);
        assertNotNull(injector.getInstance(RepositoryStub.class));
        assertSame(injector.getInstance(RepositoryStub.class), injector.getInstance(RepositoryStub.class));
    }

    /** \u9a8c\u8bc1<code>injector.getInstance(io.ddd4j.data.crypto.CryptoProperties.class)</code>\u975e\u7a7a\uff0c<code>injector.getInstance(io.ddd4j.data.logs.ApiOperationLogProvider.class)</code>\u975e\u7a7a\u3002 */
    @Test
    void cryptoAndLogsModulesProvideBindings() {
        Injector injector = Guice.createInjector(new Ddd4jCryptoGuiceModule(), new Ddd4jLogsGuiceModule());

        assertNotNull(injector.getInstance(io.ddd4j.data.crypto.CryptoProperties.class));
        assertNotNull(injector.getInstance(io.ddd4j.data.logs.ApiOperationLogProvider.class));
    }

    /** \u9a8c\u8bc1<code>AnnotationFusionVerifier.main(new String[0])</code> \u6b63\u5e38\u8fd4\u56de\u3002 */
    @Test
    void annotationFusionVerifierMainPasses() {
        assertDoesNotThrow(() -> AnnotationFusionVerifier.main(new String[0]));
    }

    /** \u9a8c\u8bc1<code>provider.getMessage("plain")</code> \u7b49\u4e8e <code>"Plain message"</code>\uff0c<code>provider.getMessage("greeting", "World")</code> \u7b49\u4e8e <code>"Hello World"</code>\uff0c<code>provider.getMessage(null)</code> \u7b49\u4e8e <code>null</code>\u3002 */
    @Test
    void i18nProviderResolvesBundles() {
        GuiceI18nProvider provider = new GuiceI18nProvider();

        assertEquals("Plain message", provider.getMessage("plain"));
        assertEquals("Hello World", provider.getMessage("greeting", "World"));
        assertEquals("missing.key", provider.getMessage("missing.key"));
        assertEquals(null, provider.getMessage(null));
    }

    /** \u9a8c\u8bc1<code>received.get()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>event</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>injector.getInstance(TestDomainService.class)</code>\u975e\u7a7a\uff0c<code>injector.getInstance(TestDomainService.class)</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>injector.getInstance(TestDomainService.class)</code>\u3002 */
    @Test
    void dddAnnotationModuleScansAndBindsAnnotatedBeans() {
        Injector injector = Guice.createInjector(new DddAnnotationModule(true, "io.ddd4j.guice"));

        assertNotNull(injector.getInstance(TestDomainService.class));
        assertSame(injector.getInstance(TestDomainService.class),
                injector.getInstance(TestDomainService.class));
    }

    /** \u9a8c\u8bc1<code>injector</code>\u975e\u7a7a\u3002 */
    @Test
    void dddAnnotationModuleWithScanDisabledIsSafe() {
        Injector injector = Guice.createInjector(new DddAnnotationModule(false, "io.ddd4j.guice"));

        assertNotNull(injector);
    }

    /** \u9a8c\u8bc1<code>injector.getInstance(com.google.common.eventbus.EventBus.class)</code>\u975e\u7a7a\uff0c<code>injector.getInstance(io.ddd4j.core.cqrs.readmodel.ProjectionService.class)</code>\u975e\u7a7a\uff0c<code>injector.getInstance(io.ddd4j.core.cqrs.command.CommandBus.class)</code>\u975e\u7a7a\u3002 */
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

    /** \u9a8c\u8bc1<code>provider.getSubject()</code>\u4e3a\u7a7a\uff0c<code>injected.getSubject()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>subject</code>\uff0c<code>emptyProvider.getSubject()</code>\u4e3a\u7a7a\u3002 */
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

    /** \u9a8c\u8bc1<code>factory</code>\u975e\u7a7a\uff0c<code>session</code>\u975e\u7a7a\uff0c<code>((org.apache.ibatis.session.SqlSessionManager) session).isManagedSessionStarted()</code>\u4e3a\u5047\u3002 */
    @Test
    void mybatisModuleBuildsFactoryAndInjectsMappers() {
        Ddd4jMybatisGuiceModule module = new Ddd4jMybatisGuiceModule(new NoopDataSource())
                .addMapper(MapperStub.class)
                .bindRepository(RepositoryStub.class, MapperStub.class);
        Injector injector = Guice.createInjector(module);

        org.apache.ibatis.session.SqlSessionFactory factory =
                injector.getInstance(org.apache.ibatis.session.SqlSessionFactory.class);
        assertNotNull(factory);
        org.apache.ibatis.session.SqlSession session =
                injector.getInstance(org.apache.ibatis.session.SqlSession.class);
        assertNotNull(session);

        module.initRepositories(injector);
        // 工厂注入的是线程安全管理代理，尚未打开任何托管事务或 JDBC 会话。
        assertFalse(((org.apache.ibatis.session.SqlSessionManager) session).isManagedSessionStarted());
    }

    /** \u9a8c\u8bc1<code>injector.getInstance(io.ddd4j.guice.testbeans.OrderService.class)</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>first</code>\u3002 */
    @Test
    void dddAnnotationModuleBindsTopLevelAnnotatedBean() {
        Injector injector = Guice.createInjector(
                new DddAnnotationModule(true, "io.ddd4j.guice.testbeans"));

        io.ddd4j.guice.testbeans.OrderService first =
                injector.getInstance(io.ddd4j.guice.testbeans.OrderService.class);
        assertSame(first, injector.getInstance(io.ddd4j.guice.testbeans.OrderService.class));
    }

    /** \u9a8c\u8bc1<code>injector.getInstance(io.ddd4j.core.cqrs.command.CommandBus.class)</code>\u975e\u7a7a\u3002 */
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

    /** \u9a8c\u8bc1<code>io.ddd4j.cache.CacheKit.getCache("guice-type-test")</code>\u975e\u7a7a\u3002 */
    @Test
    void cacheModuleSupportsDefaultTypeOverride() {
        Guice.createInjector(new Ddd4jCacheGuiceModule()
                .setDefaultType(io.ddd4j.cache.CacheKit.LocalCacheType.GUAVA)
                .build("guice-type-test", 60L));

        assertNotNull(io.ddd4j.cache.CacheKit.getCache("guice-type-test"));
        io.ddd4j.cache.CacheKit.unregister("guice-type-test");
    }

    /** \u9a8c\u8bc1<code>injector.getInstance(io.ddd4j.guice.testbeans.OrderService.class)</code>\u975e\u7a7a\u3002 */
    @Test
    void dddAnnotationModuleOneArgConstructorEnablesScan() {
        Injector injector = Guice.createInjector(
                new DddAnnotationModule("io.ddd4j.guice.testbeans"));

        assertNotNull(injector.getInstance(io.ddd4j.guice.testbeans.OrderService.class));
    }

    /** \u4e3a\u6d4b\u8bd5\u6ce8\u5165\u63d0\u4f9b\u7684 Mapper \u66ff\u8eab\u63a5\u53e3\u3002 */
    public interface MapperStub {
    }

    /** \u9a8c\u8bc1\u5bb9\u5668\u6309\u9886\u57df\u7ec4\u4ef6\u6ce8\u89e3\u53d1\u73b0\u670d\u52a1\u7684\u6d4b\u8bd5\u5bf9\u8c61\u3002 */
    @DomainService
    public static class TestDomainService {
    }

    /** \u7528\u4e8e\u68c0\u67e5\u6ce8\u89e3\u7ec4\u4ef6\u80fd\u5426\u83b7\u5f97\u4ed3\u50a8\u4f9d\u8d56\u7684\u66ff\u8eab\u3002 */
    public static class RepositoryStub {
    }

    /** \u5b9e\u73b0 <code>DataSource</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>getConnection()</code>\u3001<code>getConnection()</code>\u3001<code>getLogWriter()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    public static final class NoopDataSource implements javax.sql.DataSource {

        /**
         * \u4e3a\u5f53\u524d\u7528\u4f8b\u63d0\u4f9b\u56fa\u5b9a\u7684 <code>getConnection</code> \u8fd4\u56de\u503c\u3002
         * @return <code>null</code>
         */
        @Override
        public java.sql.Connection getConnection() {
            return null;
        }

        /**
         * \u8bfb\u53d6 <code>null</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
         * @param username \u6d4b\u8bd5\u4f1a\u8bdd\u4f7f\u7528\u7684\u7528\u6237\u540d
         * @param password \u6d4b\u8bd5\u8d26\u53f7\u7684\u53e3\u4ee4
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>null</code>
         */
        @Override
        public java.sql.Connection getConnection(String username, String password) {
            return null;
        }

        /**
         * \u4e3a\u5f53\u524d\u7528\u4f8b\u63d0\u4f9b\u56fa\u5b9a\u7684 <code>getLogWriter</code> \u8fd4\u56de\u503c\u3002
         * @return <code>null</code>
         */
        @Override
        public java.io.PrintWriter getLogWriter() {
            return null;
        }

        /**
         * \u4f5c\u4e3a\u7a7a\u5b9e\u73b0\u7684\u6d4b\u8bd5\u66ff\u8eab\uff0c\u8c03\u7528\u65f6\u4fdd\u6301\u5f53\u524d\u5939\u5177\u72b6\u6001\u3002
         * @param out \u672c\u6b21\u8c03\u7528\u4f7f\u7528\u7684 <code>java.io.PrintWriter</code> \u53c2\u6570 <code>out</code>
         */
        @Override
        public void setLogWriter(java.io.PrintWriter out) {
        }

        /**
         * \u4e3a\u5f53\u524d\u7528\u4f8b\u63d0\u4f9b\u56fa\u5b9a\u7684 <code>getLoginTimeout</code> \u8fd4\u56de\u503c\u3002
         * @return <code>0</code>
         */
        @Override
        public int getLoginTimeout() {
            return 0;
        }

        /**
         * \u4f5c\u4e3a\u7a7a\u5b9e\u73b0\u7684\u6d4b\u8bd5\u66ff\u8eab\uff0c\u8c03\u7528\u65f6\u4fdd\u6301\u5f53\u524d\u5939\u5177\u72b6\u6001\u3002
         * @param seconds \u7b49\u5f85\u6216\u63a8\u8fdb\u7684\u79d2\u6570
         */
        @Override
        public void setLoginTimeout(int seconds) {
        }

        /**
         * \u901a\u8fc7 <code>java.util.logging.Logger.getGlobal()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>java.util.logging.Logger.getGlobal()</code>
         */
        @Override
        public java.util.logging.Logger getParentLogger() {
            return java.util.logging.Logger.getGlobal();
        }

        /**
         * \u8bfb\u53d6 <code>null</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
         * @param <T> \u5f53\u524d\u5951\u7ea6\u6d4b\u8bd5\u6240\u4f7f\u7528\u7684 Java \u7c7b\u578b
         * @param iface \u672c\u6b21\u8c03\u7528\u4f7f\u7528\u7684 <code>Class&lt;T&gt;</code> \u53c2\u6570 <code>iface</code>
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>null</code>
         */
        @Override
        public <T> T unwrap(Class<T> iface) {
            return null;
        }

        /**
         * \u8bfb\u53d6 <code>false</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
         * @param iface \u672c\u6b21\u8c03\u7528\u4f7f\u7528\u7684 <code>Class&lt;?&gt;</code> \u53c2\u6570 <code>iface</code>
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>false</code>
         */
        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }

    /** \u63a5\u6536\u6d4b\u8bd5\u547d\u4ee4\u5e76\u8fd4\u56de\u53ef\u65ad\u8a00\u7ed3\u679c\u7684\u6267\u884c\u5668\u66ff\u8eab\u3002 */
    public static class ExecutorStub implements io.ddd4j.core.cqrs.command.CommandExecutor<io.ddd4j.core.cqrs.command.Command> {

        /**
         * 声明当前处理器可以接收的命令类型。
         *
         * <p>测试夹具会调用 <code>java.util.Collections.emptySet()</code>。</p>
         *
         * @return 实际返回<code>java.util.Collections.emptySet()</code>
         */
        @Override
        public java.util.Set<Class<? extends io.ddd4j.core.cqrs.command.Command>> supportedCommands() {
            return java.util.Collections.emptySet();
        }

        /**
         * 用当前处理器执行传入命令或操作。
         *
         * <p>测试夹具会调用 <code>io.ddd4j.core.cqrs.command.Result.ok()</code>。</p>
         *
         * @param command 命令对象
         * @return 实际返回<code>io.ddd4j.core.cqrs.command.Result.ok()</code>
         */
        @Override
        public io.ddd4j.core.cqrs.command.Result execute(io.ddd4j.core.cqrs.command.Command command) {
            return io.ddd4j.core.cqrs.command.Result.ok();
        }
    }

}
