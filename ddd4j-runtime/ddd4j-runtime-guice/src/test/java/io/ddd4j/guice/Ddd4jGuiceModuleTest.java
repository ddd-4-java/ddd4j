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
import io.ddd4j.core.api.R;
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

/**
 * 验证 <code>Ddd4jGuiceModule</code> 在<code>dddAnnotationModule</code>等操作中的行为契约。
 */
class Ddd4jGuiceModuleTest {

    /**
     * 验证<code>handled</code> 等于 <code>java.util.Collections.singletonList("event")</code>。
     */
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

    /**
     * 测试结束后执行 <code>CacheKit.unregister()</code> 完成资源回收或状态恢复。
     */
    @AfterEach
    void tearDown() {
        CacheKit.unregister("guice-local-1");
        CacheKit.unregister("guice-local-2");
        CacheKit.unregister("guice-external");
    }

    /**
     * 验证<code>injector.getInstance(ViewManager.class)</code>非空，<code>injector.getInstance(ViewManager.class).getClass()</code> 与…为同一实例 <code>GuiceViewManager.class</code>，<code>injector.getInstance(io.ddd4j.guice.Ddd4jGuiceRuntime.class)</code>非空。
     */
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

    /**
     * 验证<code>injector</code>非空，<code>CacheKit.getCache("guice-local-1")</code>非空，<code>CacheKit.getCache("guice-external")</code>非空。
     */
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

    /**
     * 验证<code>injector</code>非空，<code>injector.getInstance(RepositoryStub.class)</code>非空，<code>injector.getInstance(RepositoryStub.class)</code> 与…为同一实例 <code>injector.getInstance(RepositoryStub.class)</code>。
     */
    @Test
    void mybatisGuiceModuleBindsRepositories() {
        Injector injector = Guice.createInjector(new Ddd4jMybatisGuiceModule(new NoopDataSource())
                .addMapper(MapperStub.class)
                .bindRepository(RepositoryStub.class, MapperStub.class));

        assertNotNull(injector);
        assertNotNull(injector.getInstance(RepositoryStub.class));
        assertSame(injector.getInstance(RepositoryStub.class), injector.getInstance(RepositoryStub.class));
    }

    /**
     * 验证<code>injector.getInstance(io.ddd4j.data.crypto.CryptoProperties.class)</code>非空，<code>injector.getInstance(io.ddd4j.data.logs.ApiOperationLogProvider.class)</code>非空。
     */
    @Test
    void cryptoAndLogsModulesProvideBindings() {
        Injector injector = Guice.createInjector(new Ddd4jCryptoGuiceModule(), new Ddd4jLogsGuiceModule());

        assertNotNull(injector.getInstance(io.ddd4j.data.crypto.CryptoProperties.class));
        assertNotNull(injector.getInstance(io.ddd4j.data.logs.ApiOperationLogProvider.class));
    }

    /**
     * 验证<code>AnnotationFusionVerifier.main(new String[0])</code> 正常返回。
     */
    @Test
    void annotationFusionVerifierMainPasses() {
        assertDoesNotThrow(() -> AnnotationFusionVerifier.main(new String[0]));
    }

    /**
     * 验证<code>provider.getMessage("plain")</code> 等于 <code>"Plain message"</code>，<code>provider.getMessage("greeting", "World")</code> 等于 <code>"Hello World"</code>，<code>provider.getMessage(null)</code> 等于 <code>null</code>。
     */
    @Test
    void i18nProviderResolvesBundles() {
        GuiceI18nProvider provider = new GuiceI18nProvider();

        assertEquals("Plain message", provider.getMessage("plain"));
        assertEquals("Hello World", provider.getMessage("greeting", "World"));
        assertEquals("missing.key", provider.getMessage("missing.key"));
        assertEquals(null, provider.getMessage(null));
    }

    /**
     * 验证<code>received.get()</code> 与…为同一实例 <code>event</code>。
     */
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

    /**
     * 验证<code>injector.getInstance(TestDomainService.class)</code>非空，<code>injector.getInstance(TestDomainService.class)</code> 与…为同一实例 <code>injector.getInstance(TestDomainService.class)</code>。
     */
    @Test
    void dddAnnotationModuleScansAndBindsAnnotatedBeans() {
        Injector injector = Guice.createInjector(new DddAnnotationModule(true, "io.ddd4j.guice"));

        assertNotNull(injector.getInstance(TestDomainService.class));
        assertSame(injector.getInstance(TestDomainService.class),
                injector.getInstance(TestDomainService.class));
    }

    /**
     * 验证<code>injector</code>非空。
     */
    @Test
    void dddAnnotationModuleWithScanDisabledIsSafe() {
        Injector injector = Guice.createInjector(new DddAnnotationModule(false, "io.ddd4j.guice"));

        assertNotNull(injector);
    }

    /**
     * 验证<code>injector.getInstance(com.google.common.eventbus.EventBus.class)</code>非空，<code>injector.getInstance(io.ddd4j.core.cqrs.readmodel.ProjectionService.class)</code>非空，<code>injector.getInstance(io.ddd4j.core.cqrs.command.CommandBus.class)</code>非空。
     */
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

    /**
     * 验证<code>provider.getSubject()</code>为空，<code>injected.getSubject()</code> 与…为同一实例 <code>subject</code>，<code>emptyProvider.getSubject()</code>为空。
     */
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

    /**
     * 验证<code>factory</code>非空，<code>session</code>非空，<code>((org.apache.ibatis.session.SqlSessionManager) session).isManagedSessionStarted()</code>为假。
     */
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

    /**
     * 验证<code>injector.getInstance(io.ddd4j.guice.testbeans.OrderService.class)</code> 与…为同一实例 <code>first</code>。
     */
    @Test
    void dddAnnotationModuleBindsTopLevelAnnotatedBean() {
        Injector injector = Guice.createInjector(
                new DddAnnotationModule(true, "io.ddd4j.guice.testbeans"));

        io.ddd4j.guice.testbeans.OrderService first =
                injector.getInstance(io.ddd4j.guice.testbeans.OrderService.class);
        assertSame(first, injector.getInstance(io.ddd4j.guice.testbeans.OrderService.class));
    }

    /**
     * 验证<code>injector.getInstance(io.ddd4j.core.cqrs.command.CommandBus.class)</code>非空。
     */
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

    /**
     * 验证<code>io.ddd4j.cache.CacheKit.getCache("guice-type-test")</code>非空。
     */
    @Test
    void cacheModuleSupportsDefaultTypeOverride() {
        Guice.createInjector(new Ddd4jCacheGuiceModule()
                .setDefaultType(io.ddd4j.cache.CacheKit.LocalCacheType.GUAVA)
                .build("guice-type-test", 60L));

        assertNotNull(io.ddd4j.cache.CacheKit.getCache("guice-type-test"));
        io.ddd4j.cache.CacheKit.unregister("guice-type-test");
    }

    /**
     * 验证<code>injector.getInstance(io.ddd4j.guice.testbeans.OrderService.class)</code>非空。
     */
    @Test
    void dddAnnotationModuleOneArgConstructorEnablesScan() {
        Injector injector = Guice.createInjector(
                new DddAnnotationModule("io.ddd4j.guice.testbeans"));

        assertNotNull(injector.getInstance(io.ddd4j.guice.testbeans.OrderService.class));
    }

    /**
     * 为测试注入提供的 Mapper 替身接口。
     */
    public interface MapperStub {
    }

    /**
     * 验证容器按领域组件注解发现服务的测试对象。
     */
    @DomainService
    public static class TestDomainService {
    }

    /**
     * 用于检查注解组件能否获得仓储依赖的替身。
     */
    public static class RepositoryStub {
    }

    /**
     * 实现 <code>DataSource</code>，供测试调用 <code>getConnection()</code>、<code>getConnection()</code>、<code>getLogWriter()</code> 并观察结果。
     */
    public static final class NoopDataSource implements javax.sql.DataSource {

        /**
         * 为当前用例提供固定的 <code>getConnection</code> 返回值。
         *
         * @return <code>null</code>
         */
        @Override
        public java.sql.Connection getConnection() {
            return null;
        }

        /**
         * 读取 <code>null</code> 供当前测试使用。
         *
         * @param username 测试会话使用的用户名
         * @param password 测试账号的口令
         * @return 当前测试使用的结果，取自 <code>null</code>
         */
        @Override
        public java.sql.Connection getConnection(String username, String password) {
            return null;
        }

        /**
         * 为当前用例提供固定的 <code>getLogWriter</code> 返回值。
         *
         * @return <code>null</code>
         */
        @Override
        public java.io.PrintWriter getLogWriter() {
            return null;
        }

        /**
         * 作为空实现的测试替身，调用时保持当前夹具状态。
         *
         * @param out 本次调用使用的 <code>java.io.PrintWriter</code> 参数 <code>out</code>
         */
        @Override
        public void setLogWriter(java.io.PrintWriter out) {
        }

        /**
         * 为当前用例提供固定的 <code>getLoginTimeout</code> 返回值。
         *
         * @return <code>0</code>
         */
        @Override
        public int getLoginTimeout() {
            return 0;
        }

        /**
         * 作为空实现的测试替身，调用时保持当前夹具状态。
         *
         * @param seconds 等待或推进的秒数
         */
        @Override
        public void setLoginTimeout(int seconds) {
        }

        /**
         * 通过 <code>java.util.logging.Logger.getGlobal()</code> 取得当前测试需要的结果。
         *
         * @return 当前测试使用的结果，取自 <code>java.util.logging.Logger.getGlobal()</code>
         */
        @Override
        public java.util.logging.Logger getParentLogger() {
            return java.util.logging.Logger.getGlobal();
        }

        /**
         * 读取 <code>null</code> 供当前测试使用。
         *
         * @param <T>   当前契约测试所使用的 Java 类型
         * @param iface 本次调用使用的 <code>Class&lt;T&gt;</code> 参数 <code>iface</code>
         * @return 当前测试使用的结果，取自 <code>null</code>
         */
        @Override
        public <T> T unwrap(Class<T> iface) {
            return null;
        }

        /**
         * 读取 <code>false</code> 供当前测试使用。
         *
         * @param iface 本次调用使用的 <code>Class&lt;?&gt;</code> 参数 <code>iface</code>
         * @return 当前测试使用的结果，取自 <code>false</code>
         */
        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }

    /**
     * 接收测试命令并返回可断言结果的执行器替身。
     */
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
         * <p>测试夹具会调用 <code>R.ok()</code>。</p>
         *
         * @param command 命令对象
         * @return 实际返回<code>R.ok()</code>
         */
        @Override
        public R<?> execute(io.ddd4j.core.cqrs.command.Command command) {
            return R.ok();
        }
    }

}
