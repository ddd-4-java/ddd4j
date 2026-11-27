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
package io.ddd4j.guice.cqrs;

import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository;
import io.ddd4j.core.cqrs.readmodel.ViewManager;
import io.ddd4j.core.cqrs.readmodel.ViewScheduler;
import io.ddd4j.guice.command.GuiceCommandBus;
import io.ddd4j.runtime.testkit.AbstractCqrsRuntimeContractTest;
import io.ddd4j.runtime.testkit.CqrsRuntimeContract;

import java.util.Arrays;
import java.util.Objects;

/**
 * Guice 运行时 CQRS 契约测试。
 * <p>
 * 使用 {@code Guice.createInjector} 装配，与模块现有 {@code GuiceCommandBusTest} 风格一致。
 * <p>
 * 装配方式：
 * <ul>
 *   <li>CommandBus：{@link GuiceCommandBus}，构造器直接注入执行器集合</li>
 *   <li>ViewManager：{@link GuiceViewManager}，无参构造（默认线程池大小 2）</li>
 *   <li>ViewScheduler：{@link GuiceViewManager}（Guice 实现同时实现 ViewManager 和 ViewScheduler）</li>
 *   <li>PositionRepository：{@link GuiceInMemoryProjectionPositionRepository}（Guice 原生内存实现）</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
class GuiceCqrsRuntimeContractTest extends AbstractCqrsRuntimeContractTest {

    /**
     * \u6784\u9020 <code>io.ddd4j.core.cqrs.readmodel.ProjectionView&lt;&gt;</code> \u4f5c\u4e3a\u5f53\u524d\u6d4b\u8bd5\u7684\u8f93\u5165\u6216\u7ed3\u679c\u3002
     * @param name \u5f53\u524d\u6d4b\u8bd5\u6307\u5b9a\u7684\u5bf9\u8c61\u6216\u5c5e\u6027\u540d\u79f0
     * @param cron \u7528\u4e8e\u6784\u9020\u672c\u6b21\u8fd4\u56de\u7ed3\u679c\u7684 <code>String</code> \u8f93\u5165
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>io.ddd4j.core.cqrs.readmodel.ProjectionView&lt;Object&gt;</code> \u7ed3\u679c
     */
    private static io.ddd4j.core.cqrs.readmodel.ProjectionView<Object> startupView(String name, String cron) {
        return new io.ddd4j.core.cqrs.readmodel.ProjectionView<>() {
            public String getName() {
                return name;
            }

            public String getCron() {
                return cron;
            }

            public java.util.Collection<String> getEventTypes() {
                return java.util.List.of();
            }

            public void handleEvents(java.util.Collection<Object> events) {
            }
        };
    }

    /**
     * \u6839\u636e\u672c\u6b21\u8c03\u7528\u66f4\u65b0 <code>previous = com.google.inject.Guice.createInjector(new io.ddd4j.guice.Ddd4jGuiceModule())</code>\u3001<code>priorRuntime = previous.getInstance(io.ddd4j.guice.Ddd4jGuiceRuntime.class)</code>\uff0c\u7528\u4e8e\u540e\u7eed\u6d4b\u8bd5\u6b65\u9aa4\u3002
     * @param prior \u4f20\u7ed9 <code>"empty".equals(prior)</code> \u7684 <code>String</code> \u8f93\u5165
     * @param projectionFirst \u4f20\u7ed9 <code>org.junit.jupiter.api.Assertions.assertThrows()</code> \u7684 <code>boolean</code> \u8f93\u5165
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"empty,false", "empty,true", "registrations,false", "registrations,true", "runtime,false", "runtime,true"})
    void failedProjectionStartupPreservesGlobalsAndReleasesOwnedResources(String prior, boolean projectionFirst) throws Exception {
        io.ddd4j.core.context.BaseContext.clear();
        io.ddd4j.guice.context.GuiceContext.clear();
        String[] keys = {io.ddd4j.core.constant.SpiKeys.DOMAIN_EVENT_PUBLISHER,
                io.ddd4j.core.constant.SpiKeys.SUBJECT_PROVIDER, io.ddd4j.core.constant.SpiKeys.I18N_PROVIDER,
                io.ddd4j.core.constant.SpiKeys.COMMAND_BUS};
        com.google.inject.Injector previous = null;
        io.ddd4j.guice.Ddd4jGuiceRuntime priorRuntime = null;
        ViewScheduler.ViewScheduleHandle priorHandle = null;
        if (!"empty".equals(prior)) {
            previous = com.google.inject.Guice.createInjector(new io.ddd4j.guice.Ddd4jGuiceModule());
            priorRuntime = previous.getInstance(io.ddd4j.guice.Ddd4jGuiceRuntime.class);
            priorHandle = previous.getInstance(GuiceViewManager.class).scheduleAtFixedRate("prior-runtime", 3600, () -> {
            });
        }
        java.util.Map<String, Object> before = new java.util.HashMap<>();
        for (String key : keys) {
            before.put(key, io.ddd4j.core.context.BaseContext.get(key, Object.class).orElse(null));
        }
        if ("registrations".equals(prior)) {
            priorRuntime.close();
            priorRuntime = null;
            previous = null;
            before.forEach((key, value) -> io.ddd4j.core.context.BaseContext.inject(key, Object.class, value));
        }
        io.ddd4j.guice.context.GuiceContext.setAttribute("r07-prior", "untouched");
        java.util.List<io.ddd4j.core.cqrs.readmodel.ProjectionView<Object>> views = java.util.List.of(
                startupView("scheduled-first", "0/3600"), startupView("fails-second", "invalid-cron"));
        GuiceInMemoryProjectionPositionRepository repository = new GuiceInMemoryProjectionPositionRepository();
        io.ddd4j.core.cqrs.readmodel.EventChunkReader<Object> reader = (stream, from, size, types) ->
                io.ddd4j.core.cqrs.readmodel.EventChunk.empty(from);
        io.ddd4j.core.cqrs.readmodel.ProjectionRunner<Object> runner = new io.ddd4j.core.cqrs.readmodel.ProjectionRunner<>(
                new io.ddd4j.core.cqrs.readmodel.DefaultProjectionService(repository), reader);
        java.util.List<ViewScheduler.ViewScheduleHandle> handles = new java.util.ArrayList<>();
        java.util.List<java.util.concurrent.ScheduledExecutorService> pools = new java.util.ArrayList<>();
        java.lang.reflect.Field executorField = GuiceViewManager.class.getDeclaredField("executor");
        executorField.setAccessible(true);
        GuiceViewManager manager = new GuiceViewManager(1, repository, runner, views) {
            @Override
            public ViewScheduleHandle scheduleAtFixedRate(String name, long interval, Runnable task) {
                ViewScheduleHandle handle = super.scheduleAtFixedRate(name, interval, task);
                handles.add(handle);
                try {
                    pools.add((java.util.concurrent.ScheduledExecutorService) executorField.get(this));
                } catch (IllegalAccessException failure) {
                    throw new AssertionError(failure);
                }
                return handle;
            }
        };
        com.google.inject.Module projection = com.google.inject.util.Modules.override(
                new io.ddd4j.guice.Ddd4jProjectionGuiceModule(views)).with(new com.google.inject.AbstractModule() {
            @Override
            protected void configure() {
                bind(GuiceViewManager.class).toInstance(manager);
                bind(ProjectionPositionRepository.class).toInstance(repository);
                bind(new com.google.inject.TypeLiteral<io.ddd4j.core.cqrs.readmodel.EventChunkReader<Object>>() {
                }).toInstance(reader);
            }
        });
        com.google.inject.Injector expectedPrevious = previous;
        ViewScheduler.ViewScheduleHandle existingHandle = priorHandle;
        try {
            com.google.inject.CreationException failure = org.junit.jupiter.api.Assertions.assertThrows(
                    com.google.inject.CreationException.class, () -> {
                        if (projectionFirst) {
                            com.google.inject.Guice.createInjector(projection, io.ddd4j.guice.Ddd4jGuiceModule.basic());
                        } else {
                            com.google.inject.Guice.createInjector(io.ddd4j.guice.Ddd4jGuiceModule.basic(), projection);
                        }
                    });
            org.junit.jupiter.api.Assertions.assertTrue(failure.getMessage().contains("Unsupported cron expression format: invalid-cron"));
            org.junit.jupiter.api.Assertions.assertFalse(handles.isEmpty(), "must schedule a real task before failing");
            org.junit.jupiter.api.Assertions.assertAll(
                    () -> org.junit.jupiter.api.Assertions.assertSame(expectedPrevious,
                            io.ddd4j.guice.context.GuiceContext.isInitialized() ? io.ddd4j.guice.context.GuiceContext.getInjector() : null),
                    () -> {
                        for (String key : keys) {
                            org.junit.jupiter.api.Assertions.assertSame(before.get(key),
                                    io.ddd4j.core.context.BaseContext.get(key, Object.class).orElse(null), key);
                        }
                    },
                    () -> org.junit.jupiter.api.Assertions.assertEquals("untouched", io.ddd4j.guice.context.GuiceContext.getAttribute("r07-prior")),
                    () -> {
                        if ("runtime".equals(prior)) {
                            org.junit.jupiter.api.Assertions.assertTrue(existingHandle.isActive(), "existing runtime task must remain active");
                            org.junit.jupiter.api.Assertions.assertTrue(expectedPrevious.getInstance(GuiceViewManager.class).isRunning());
                        }
                    },
                    () -> org.junit.jupiter.api.Assertions.assertFalse(manager.isRunning()),
                    () -> org.junit.jupiter.api.Assertions.assertTrue(handles.stream().noneMatch(ViewScheduler.ViewScheduleHandle::isActive)),
                    () -> org.junit.jupiter.api.Assertions.assertTrue(pools.stream().allMatch(pool -> pool.isShutdown() && pool.isTerminated())));
        } finally {
            manager.close();
            if (Objects.nonNull(priorRuntime)) {
                priorRuntime.close();
            }
            io.ddd4j.core.context.BaseContext.clear();
            io.ddd4j.guice.context.GuiceContext.clear();
        }
    }

    /** \u9a8c\u8bc1<code>basic.getExistingBinding(com.google.inject.Key.get(ProjectionPositionRepository.class))</code>\u4e3a\u7a7a\uff0c<code>basic.getExistingBinding(com.google.inject.Key.get(ViewManager.class))</code>\u4e3a\u7a7a\uff0c<code>basic.getInstance(CommandBus.class)</code>\u975e\u7a7a\u3002 */
    @org.junit.jupiter.api.Test
    void basicHasNoProjectionAndLegacyKeepsMemoryDefault() {
        com.google.inject.Injector basic = com.google.inject.Guice.createInjector(io.ddd4j.guice.Ddd4jGuiceModule.basic());
        try (io.ddd4j.guice.Ddd4jGuiceRuntime runtime = basic.getInstance(io.ddd4j.guice.Ddd4jGuiceRuntime.class)) {
            org.junit.jupiter.api.Assertions.assertNull(basic.getExistingBinding(com.google.inject.Key.get(ProjectionPositionRepository.class)));
            org.junit.jupiter.api.Assertions.assertNull(basic.getExistingBinding(com.google.inject.Key.get(ViewManager.class)));
            org.junit.jupiter.api.Assertions.assertNotNull(basic.getInstance(CommandBus.class));
        }
        com.google.inject.Injector legacy = com.google.inject.Guice.createInjector(new io.ddd4j.guice.Ddd4jGuiceModule());
        try (io.ddd4j.guice.Ddd4jGuiceRuntime runtime = legacy.getInstance(io.ddd4j.guice.Ddd4jGuiceRuntime.class)) {
            org.junit.jupiter.api.Assertions.assertInstanceOf(io.ddd4j.core.cqrs.readmodel.InMemoryProjectionPositionRepository.class,
                    legacy.getInstance(ProjectionPositionRepository.class));
            org.junit.jupiter.api.Assertions.assertNotNull(legacy.getInstance(com.google.inject.Key.get(
                    new com.google.inject.TypeLiteral<io.ddd4j.core.cqrs.readmodel.ProjectionRunner<Object>>() {
                    })));
        }
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>com.google.inject.CreationException.class</code>\u3002 */
    @org.junit.jupiter.api.Test
    void explicitProjectionRejectsMissingRepositoryAndReaderAtInjectorCreation() {
        for (boolean supplyRepository : new boolean[]{false, true}) {
            org.junit.jupiter.api.Assertions.assertThrows(com.google.inject.CreationException.class, () ->
                    com.google.inject.Guice.createInjector(io.ddd4j.guice.Ddd4jGuiceModule.basic(),
                            new io.ddd4j.guice.Ddd4jProjectionGuiceModule(java.util.List.of()),
                            new com.google.inject.AbstractModule() {
                                protected void configure() {
                                    if (supplyRepository) {
                                        bind(ProjectionPositionRepository.class).toInstance(new GuiceInMemoryProjectionPositionRepository());
                                    } else {
                                        bind(new com.google.inject.TypeLiteral<io.ddd4j.core.cqrs.readmodel.EventChunkReader<Object>>() {
                                        })
                                                .toInstance((stream, from, size, types) -> io.ddd4j.core.cqrs.readmodel.EventChunk.empty(from));
                                    }
                                }
                            }));
        }
    }

    /**
     * \u9a8c\u8bc1<code>checkpoint</code>\u975e\u7a7a\uff0c<code>injector.getInstance(ViewManager.class)</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>manager</code>\uff0c<code>handle.isActive()</code>\u4e3a\u5047\u3002
     * @throws Exception \u51c6\u5907\u6216\u8bfb\u53d6\u5939\u5177\u6587\u4ef6\u65f6\u5931\u8d25
     */
    @org.junit.jupiter.api.Test
    void explicitProjectionRestoresFrozenPositionProcessesEventsAndClosesTasks() throws Exception {
        org.h2.jdbcx.JdbcDataSource source = new org.h2.jdbcx.JdbcDataSource();
        source.setURL("jdbc:h2:mem:r07_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        GuiceJdbcProjectionPositionRepository repository = new GuiceJdbcProjectionPositionRepository(source);
        // R00 冻结的旧版 Java 序列化检查点，保持原始字节。
        java.io.InputStream checkpoint = GuiceCqrsRuntimeContractTest.class.getResourceAsStream("checkpoint-10.ser");
        org.junit.jupiter.api.Assertions.assertNotNull(checkpoint, "Missing frozen R00 checkpoint");
        try (java.io.InputStream resource = checkpoint;
             java.io.ObjectInputStream input = new java.io.ObjectInputStream(resource)) {
            repository.save((io.ddd4j.core.cqrs.readmodel.ProjectionPosition) input.readObject());
        }
        java.util.concurrent.atomic.AtomicInteger delivered = new java.util.concurrent.atomic.AtomicInteger();
        io.ddd4j.core.cqrs.readmodel.ProjectionView<Object> view = new io.ddd4j.core.cqrs.readmodel.ProjectionView<>() {
            public String getName() {
                return "order-summary";
            }

            public String getCron() {
                return "0/3600";
            }

            public java.util.Collection<String> getEventTypes() {
                return java.util.List.of("Order");
            }

            public void handleEvents(java.util.Collection<Object> events) {
                delivered.addAndGet(events.size());
            }
        };
        com.google.inject.Injector injector = com.google.inject.Guice.createInjector(io.ddd4j.guice.Ddd4jGuiceModule.basic(),
                new io.ddd4j.guice.Ddd4jProjectionGuiceModule(java.util.List.of(view)), new com.google.inject.AbstractModule() {
                    protected void configure() {
                        bind(ProjectionPositionRepository.class).toInstance(repository);
                        bind(new com.google.inject.TypeLiteral<io.ddd4j.core.cqrs.readmodel.EventChunkReader<Object>>() {
                        })
                                .toInstance((stream, from, size, types) -> from == 10L
                                        ? new io.ddd4j.core.cqrs.readmodel.EventChunk<>(java.util.List.of("event-11", "event-19"), 20L)
                                        : io.ddd4j.core.cqrs.readmodel.EventChunk.empty(from));
                    }
                });
        GuiceViewManager manager = injector.getInstance(GuiceViewManager.class);
        org.junit.jupiter.api.Assertions.assertSame(manager, injector.getInstance(ViewManager.class));
        org.junit.jupiter.api.Assertions.assertSame(manager, injector.getInstance(ViewScheduler.class));
        org.junit.jupiter.api.Assertions.assertTrue(manager.isRunning());
        manager.triggerOnce();
        manager.triggerOnce();
        org.junit.jupiter.api.Assertions.assertEquals(2, delivered.get());
        org.junit.jupiter.api.Assertions.assertEquals(20L, new GuiceJdbcProjectionPositionRepository(source)
                .findByStreamId("order-summary").orElseThrow().getNextEventNumber());
        io.ddd4j.core.cqrs.readmodel.ViewScheduler.ViewScheduleHandle handle = manager.scheduleAtFixedRate("close-check", 3600, () -> {
        });
        injector.getInstance(io.ddd4j.guice.Ddd4jGuiceRuntime.class).close();
        org.junit.jupiter.api.Assertions.assertFalse(manager.isRunning());
        org.junit.jupiter.api.Assertions.assertFalse(handle.isActive());
    }

    /** {@inheritDoc} */
    @Override
    protected CqrsRuntimeContract createContract() {
        StubExecutorA executorA = new StubExecutorA();
        StubExecutorB executorB = new StubExecutorB();

        GuiceCommandBus commandBus = new GuiceCommandBus(Arrays.asList(executorA, executorB));
        GuiceViewManager viewManager = new GuiceViewManager();
        GuiceInMemoryProjectionPositionRepository positionRepository =
                new GuiceInMemoryProjectionPositionRepository();

        return new CqrsRuntimeContract() {

            @Override
            public String runtimeName() {
                return "guice";
            }

            @Override
            public CommandBus commandBus() {
                return commandBus;
            }

            @Override
            public ViewManager viewManager() {
                return viewManager;
            }

            @Override
            public ViewScheduler viewScheduler() {
                // GuiceViewManager 同时实现 ViewManager 和 ViewScheduler
                return viewManager;
            }

            @Override
            public ProjectionPositionRepository positionRepository() {
                return positionRepository;
            }

            @Override
            public void close() {
                viewManager.close();
            }
        };
    }
}
