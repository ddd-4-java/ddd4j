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
package io.ddd4j.spring.cqrs;

import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.readmodel.InMemoryProjectionPositionRepository;
import io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository;
import io.ddd4j.core.cqrs.readmodel.ViewManager;
import io.ddd4j.core.cqrs.readmodel.ViewScheduler;
import io.ddd4j.runtime.testkit.AbstractCqrsRuntimeContractTest;
import io.ddd4j.runtime.testkit.CqrsRuntimeContract;
import io.ddd4j.spring.command.SpringCommandBus;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;

import java.util.concurrent.Executors;

/**
 * Spring 运行时 CQRS 契约测试。
 * <p>
 * 使用轻量级 {@link AnnotationConfigApplicationContext} 手动装配，
 * 不依赖 Spring Boot，与模块现有测试风格一致。
 * <p>
 * 装配方式：
 * <ul>
 *   <li>CommandBus：{@link SpringCommandBus}，通过 Spring 容器自动发现执行器 Bean</li>
 *   <li>ViewManager：{@link SpringJpaViewManager}，依赖 ViewScheduler</li>
 *   <li>ViewScheduler：{@link SpringViewScheduler}，基于 ConcurrentTaskScheduler</li>
 *   <li>PositionRepository：{@link InMemoryProjectionPositionRepository}（避免 JPA 重量级装配）</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
class SpringCqrsRuntimeContractTest extends AbstractCqrsRuntimeContractTest {

    private static io.ddd4j.core.cqrs.readmodel.ProjectionRunner<Object> emptyFixtureRunner() {
        return new io.ddd4j.core.cqrs.readmodel.ProjectionRunner<>(
                new io.ddd4j.core.cqrs.readmodel.DefaultProjectionService(new InMemoryProjectionPositionRepository()),
                (stream, from, size, types) -> io.ddd4j.core.cqrs.readmodel.EventChunk.empty(from));
    }

    @org.junit.jupiter.api.Test
    void basicContextDoesNotActivatePersistenceOrProjection() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                io.ddd4j.spring.config.SpringCoreConfig.class)) {
            org.junit.jupiter.api.Assertions.assertTrue(context.getBeansOfType(ViewManager.class).isEmpty());
            org.junit.jupiter.api.Assertions.assertTrue(context.getBeansOfType(ViewScheduler.class).isEmpty());
            org.junit.jupiter.api.Assertions.assertTrue(context.getBeansOfType(
                    io.ddd4j.core.cqrs.readmodel.ProjectionRunner.class).isEmpty());
            org.junit.jupiter.api.Assertions.assertTrue(context.getBeansOfType(jakarta.persistence.EntityManagerFactory.class).isEmpty());
        }
    }

    @org.junit.jupiter.api.Test
    void explicitProjectionWithoutRequiredConfigurationFails() {
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.beans.BeansException.class,
                () -> new AnnotationConfigApplicationContext(ExplicitProjection.class));
    }

    @org.junit.jupiter.api.Test
    void explicitProjectionReusesExistingManagerWithoutAddingSchedulerOrRunner() {
        for (boolean reverse : new boolean[]{false, true}) {
            try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
                context.register(reverse ? ExistingManager.class : ExplicitProjection.class,
                        reverse ? ExplicitProjection.class : ExistingManager.class);
                context.refresh();
                org.junit.jupiter.api.Assertions.assertEquals(1, context.getBeansOfType(ViewManager.class).size());
                org.junit.jupiter.api.Assertions.assertTrue(context.getBeansOfType(ViewScheduler.class).isEmpty());
                org.junit.jupiter.api.Assertions.assertTrue(context.getBeansOfType(
                        io.ddd4j.core.cqrs.readmodel.ProjectionRunner.class).isEmpty());
            }
        }
    }

    @org.junit.jupiter.api.Test
    void explicitProjectionReusesPrototypeAndCustomScopedManagerWithoutEagerCreation() {
        for (String scope : new String[]{"prototype", "requestLike"}) {
            try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
                java.util.concurrent.atomic.AtomicInteger creations = new java.util.concurrent.atomic.AtomicInteger();
                context.getBeanFactory().registerScope("requestLike", new org.springframework.context.support.SimpleThreadScope());
                context.register(ExplicitProjection.class);
                context.registerBean("customScopedManager", ViewManager.class, () -> {
                    creations.incrementAndGet();
                    return new io.ddd4j.core.cqrs.readmodel.DefaultViewManager<Object>(
                            (name, cron, task) -> {
                                throw new AssertionError("No implicit scheduling");
                            },
                            emptyFixtureRunner(), java.util.List.of());
                }, definition -> definition.setScope(scope));
                context.refresh();
                org.junit.jupiter.api.Assertions.assertArrayEquals(new String[]{"customScopedManager"},
                        context.getBeanNamesForType(ViewManager.class));
                org.junit.jupiter.api.Assertions.assertTrue(context.getBeansOfType(ViewScheduler.class).isEmpty());
                org.junit.jupiter.api.Assertions.assertEquals(0, creations.get());
                ViewManager manager = context.getBean(ViewManager.class);
                manager.start();
                org.junit.jupiter.api.Assertions.assertTrue(manager.isRunning());
                manager.stop();
                org.junit.jupiter.api.Assertions.assertEquals(1, creations.get());
            }
        }
    }

    @org.junit.jupiter.api.Test
    void explicitProjectionReusesPrototypeAndCustomScopedScheduler() {
        for (String scope : new String[]{"prototype", "requestLike"}) {
            try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
                context.getBeanFactory().registerScope("requestLike", new org.springframework.context.support.SimpleThreadScope());
                context.register(ExplicitProjection.class);
                context.registerBean("customScopedScheduler", ViewScheduler.class,
                        () -> (name, cron, task) -> {
                            throw new AssertionError("Empty views must not schedule tasks");
                        },
                        definition -> definition.setScope(scope));
                context.registerBean("runner", io.ddd4j.core.cqrs.readmodel.ProjectionRunner.class,
                        SpringCqrsRuntimeContractTest::emptyFixtureRunner);
                context.refresh();
                org.junit.jupiter.api.Assertions.assertArrayEquals(new String[]{"customScopedScheduler"},
                        context.getBeanNamesForType(ViewScheduler.class));
                org.junit.jupiter.api.Assertions.assertEquals(1, context.getBeansOfType(ViewManager.class).size());
                org.junit.jupiter.api.Assertions.assertTrue(context.getBean(ViewManager.class).isRunning());
            }
        }
    }

    @org.junit.jupiter.api.Test
    void enabledProjectionRecoversPositionRunsNativeCronAndClosesResources() throws Exception {
        InMemoryProjectionPositionRepository positions = new InMemoryProjectionPositionRepository();
        positions.save(new io.ddd4j.core.cqrs.readmodel.DefaultProjectionPosition("historical", 41));
        java.util.concurrent.CountDownLatch handled = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger deliveries = new java.util.concurrent.atomic.AtomicInteger();
        org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler scheduler =
                new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        ViewManager manager;
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(ExplicitProjection.class);
            context.registerBean("taskScheduler", org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler.class,
                    () -> scheduler);
            context.registerBean("runner", io.ddd4j.core.cqrs.readmodel.ProjectionRunner.class, () ->
                    new io.ddd4j.core.cqrs.readmodel.ProjectionRunner<String>(
                            new io.ddd4j.core.cqrs.readmodel.DefaultProjectionService(positions),
                            (stream, from, size, types) -> from == 41
                                    ? new io.ddd4j.core.cqrs.readmodel.EventChunk<>(java.util.List.of("persisted-event-41"), 42)
                                    : io.ddd4j.core.cqrs.readmodel.EventChunk.empty(from)));
            context.registerBean("view", io.ddd4j.core.cqrs.readmodel.ProjectionView.class, () ->
                    new io.ddd4j.core.cqrs.readmodel.ProjectionView<String>() {
                        public String getName() {
                            return "historical";
                        }

                        public String getCron() {
                            return "* * * * * *";
                        }

                        public java.util.Collection<String> getEventTypes() {
                            return java.util.List.of("OrderCreated");
                        }

                        public void handleEvents(java.util.Collection<String> events) {
                            org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of("persisted-event-41"), events);
                            deliveries.incrementAndGet();
                            handled.countDown();
                        }
                    });
            context.refresh();
            manager = context.getBean(ViewManager.class);
            org.junit.jupiter.api.Assertions.assertEquals(1, context.getBeansOfType(ViewManager.class).size());
            org.junit.jupiter.api.Assertions.assertTrue(handled.await(5, java.util.concurrent.TimeUnit.SECONDS));
            manager.stop();
            // 取消后等待已进入的 cron 任务退出，再单独验证手动触发。
            scheduler.getScheduledThreadPoolExecutor().submit(() -> {
                    })
                    .get(2, java.util.concurrent.TimeUnit.SECONDS);
            org.junit.jupiter.api.Assertions.assertEquals(42, manager.getProjectionStatus("historical").nextEventNumber());
            manager.triggerOnce();
            org.junit.jupiter.api.Assertions.assertEquals(1, deliveries.get());
            org.junit.jupiter.api.Assertions.assertEquals(42, manager.getProjectionStatus("historical").nextEventNumber());
            manager.start();
            org.junit.jupiter.api.Assertions.assertTrue(manager.isRunning());
        }
        org.junit.jupiter.api.Assertions.assertFalse(manager.isRunning());
        org.junit.jupiter.api.Assertions.assertTrue(scheduler.getScheduledThreadPoolExecutor().isShutdown());
    }

    @Override
    protected CqrsRuntimeContract createContract() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(SpringCqrsTestConfig.class);
        return new CqrsRuntimeContract() {

            @Override
            public String runtimeName() {
                return "spring";
            }

            @Override
            public CommandBus commandBus() {
                return context.getBean(SpringCommandBus.class);
            }

            @Override
            public ViewManager viewManager() {
                return context.getBean(SpringJpaViewManager.class);
            }

            @Override
            public ViewScheduler viewScheduler() {
                return context.getBean(SpringViewScheduler.class);
            }

            @Override
            public ProjectionPositionRepository positionRepository() {
                return context.getBean(InMemoryProjectionPositionRepository.class);
            }

            @Override
            public void close() {
                context.close();
            }
        };
    }

    @Configuration(proxyBeanMethods = false)
    static class ExistingManager {
        @Bean
        SpringJpaViewManager manualManager() {
            return new SpringJpaViewManager((name, cron, task) -> {
                throw new AssertionError("No implicit schedule allowed");
            });
        }
    }

    @Configuration(proxyBeanMethods = false)
    @org.springframework.context.annotation.Import(io.ddd4j.spring.config.SpringProjectionConfig.class)
    static class ExplicitProjection {
    }

    /**
     * Spring 测试配置：注册 CQRS 组件与测试执行器 Bean。
     */
    @Configuration
    static class SpringCqrsTestConfig {

        @Bean
        public SpringCommandBus springCommandBus(AnnotationConfigApplicationContext context) {
            return new SpringCommandBus(context);
        }

        @Bean
        public AbstractCqrsRuntimeContractTest.StubExecutorA stubExecutorA() {
            return new AbstractCqrsRuntimeContractTest.StubExecutorA();
        }

        @Bean
        public AbstractCqrsRuntimeContractTest.StubExecutorB stubExecutorB() {
            return new AbstractCqrsRuntimeContractTest.StubExecutorB();
        }

        @Bean
        public ConcurrentTaskScheduler taskScheduler() {
            return new ConcurrentTaskScheduler(Executors.newScheduledThreadPool(1));
        }

        @Bean
        public SpringViewScheduler springViewScheduler(ConcurrentTaskScheduler taskScheduler) {
            return new SpringViewScheduler(taskScheduler);
        }

        @Bean
        public SpringJpaViewManager springJpaViewManager(SpringViewScheduler scheduler) {
            return new SpringJpaViewManager(scheduler);
        }

        @Bean
        public InMemoryProjectionPositionRepository inMemoryProjectionPositionRepository() {
            return new InMemoryProjectionPositionRepository();
        }
    }
}
