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

/**
 * 类型说明。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */
class Ddd4jGuiceModuleTest {

    @Test
    void projectionRunnerMustUseExplicitReaderBinding() {
        /**
         * =() 方法。
         * @param stream stream 参数。
         * @param from 起始值。
         * @param size 每页条数。
         * @param types types 参数。
         * @return 方法执行结果。
         */
        io.ddd4j.core.cqrs.readmodel.EventChunkReader<Object> reader = (stream, from, size, types) ->
                new io.ddd4j.core.cqrs.readmodel.EventChunk<Object>(
                        java.util.Collections.singletonList("event"), from + 1);
        Injector injector = Guice.createInjector(new Ddd4jGuiceModule(), new AbstractModule() {
            /**
             * configure() 方法。
             */
            @Override
            protected void configure() {
                bind(new com.google.inject.TypeLiteral<io.ddd4j.core.cqrs.readmodel.EventChunkReader<Object>>() {})
                        /**
                         * .toInstance() 方法。
                         * @param reader reader 参数。
                         */
                        .toInstance(reader);
            }
        });
        try {
            java.util.List<Object> handled = new java.util.ArrayList<>();
            io.ddd4j.core.cqrs.readmodel.ProjectionRunner<Object> runner = injector.getInstance(
                    Key.get(new com.google.inject.TypeLiteral<io.ddd4j.core.cqrs.readmodel.ProjectionRunner<Object>>() {}));
            runner.runOnce(new io.ddd4j.core.cqrs.readmodel.ProjectionView<Object>() {
                /**
                 * 获取值。
                 * @return 结果字符串。
                 */
                public String getName() { return "custom-reader"; }
                /**
                 * 获取值。
                 * @return 结果字符串。
                 */
                public String getCron() { return "* * * * *"; }
                public java.util.Collection<String> getEventTypes() { return java.util.Collections.emptyList(); }
                /**
                 * handleEvents() 方法。
                 * @param events 领域事件集合。
                 */
                public void handleEvents(java.util.Collection<Object> events) { handled.addAll(events); }
            });
            assertEquals(java.util.Collections.singletonList("event"), handled);
            assertEquals(1L, injector.getInstance(io.ddd4j.core.cqrs.readmodel.ProjectionService.class)
                    .readProjectionPosition("custom-reader"));
        } finally {
            injector.getInstance(Ddd4jGuiceRuntime.class).close();
        }
    }

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
    void mybatisGuiceModuleBindsRepositories() {
        Injector injector = Guice.createInjector(new Ddd4jMybatisGuiceModule(new NoopDataSource())
                .addMapper(MapperStub.class)
                .bindRepository(RepositoryStub.class, MapperStub.class));

        assertNotNull(injector);
        assertNotNull(injector.getInstance(RepositoryStub.class));
        assertSame(injector.getInstance(RepositoryStub.class), injector.getInstance(RepositoryStub.class));
    }

    @Test
    void cryptoAndLogsModulesProvideBindings() {
        Injector injector = Guice.createInjector(new Ddd4jCryptoGuiceModule(), new Ddd4jLogsGuiceModule());

        assertNotNull(injector.getInstance(io.ddd4j.data.crypto.CryptoProperties.class));
        assertNotNull(injector.getInstance(io.ddd4j.data.logs.ApiOperationLogProvider.class));
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
            /**
             * on() 方法。
             * @param event 事件消息。
             */
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

    /**
     * 标注了领域服务注解的测试 Bean，用于验证注解扫描与单例绑定。
     */
    @DomainService
    public static class TestDomainService {
    }

    /**
     * Mapper 接口桩，仅用于验证 MyBatis 模块的 Mapper 注册流程。
     */
    public interface MapperStub {
    }

    /**
     * 仓储类桩，用于验证仓储与 Mapper 的绑定关系。
     */
    public static class RepositoryStub {
    }

    /**
     * 空实现的数据源桩，所有方法返回空值，避免测试依赖真实数据库连接。
     */
    public static final class NoopDataSource implements javax.sql.DataSource {

        /**
         * 获取值。
         * @return 方法执行结果。
         */
        @Override
        public java.sql.Connection getConnection() {
            return null;
        }

        /**
         * 获取值。
         * @param username 用户名。
         * @param password 密码。
         * @return 方法执行结果。
         */
        @Override
        public java.sql.Connection getConnection(String username, String password) {
            return null;
        }

        /**
         * 获取值。
         * @return 方法执行结果。
         */
        @Override
        public java.io.PrintWriter getLogWriter() {
            return null;
        }

        /**
         * 设置值。
         * @param out out 参数。
         */
        @Override
        public void setLogWriter(java.io.PrintWriter out) {
        }

        /**
         * 设置值。
         * @param seconds seconds 参数。
         */
        @Override
        public void setLoginTimeout(int seconds) {
        }

        /**
         * 获取值。
         * @return 结果数值。
         */
        @Override
        public int getLoginTimeout() {
            return 0;
        }

        /**
         * 获取值。
         * @return 方法执行结果。
         */
        @Override
        public java.util.logging.Logger getParentLogger() {
            return java.util.logging.Logger.getGlobal();
        }

        /**
         * unwrap() 方法。
         * @param <T> 类型参数。
         * @param iface 类型字面量。
         * @return 结果值。
         */
        @Override
        public <T> T unwrap(Class<T> iface) {
            return null;
        }

        /**
         * 判断布尔状态。
         * @param iface 类型字面量。
         * @return 判断结果。
         */
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
            /**
             * configure() 方法。
             */
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
        session.close();
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
            /**
             * configure() 方法。
             */
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

    /**
     * 类型说明。
     * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
     * @since 3.0.x
     */
    public static class ExecutorStub implements io.ddd4j.core.cqrs.command.CommandExecutor<io.ddd4j.core.cqrs.command.Command> {

        @Override
        public java.util.Set<Class<? extends io.ddd4j.core.cqrs.command.Command>> supportedCommands() {
            return java.util.Collections.emptySet();
        }

        /**
         * 执行处理逻辑。
         * @param command command 参数。
         * @return 方法执行结果。
         */
        @Override
        public io.ddd4j.core.cqrs.command.Result execute(io.ddd4j.core.cqrs.command.Command command) {
            return io.ddd4j.core.cqrs.command.Result.ok();
        }
    }

}
