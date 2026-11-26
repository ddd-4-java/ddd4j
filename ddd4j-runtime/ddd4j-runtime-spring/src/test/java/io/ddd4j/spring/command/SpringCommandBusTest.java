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
package io.ddd4j.spring.command;

import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link SpringCommandBus} 单元测试。
 * <p>
 * 使用轻量级 {@link AnnotationConfigApplicationContext} 手动装配，
 * 不依赖 Spring Boot，与模块现有测试风格一致。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
@DisplayName("SpringCommandBus 验收测试")
class SpringCommandBusTest {

    private AnnotationConfigApplicationContext context;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(TestConfig.class);
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    @DisplayName("注册：容器刷新后应自动发现并注册 CommandExecutor Bean")
    void shouldRegisterExecutorsOnContextRefresh() {
        SpringCommandBus commandBus = context.getBean(SpringCommandBus.class);
        assertNotNull(commandBus, "SpringCommandBus 应作为 Bean 注册");
    }

    @Test
    @DisplayName("路由：execute 应正确路由到对应执行器")
    void shouldRouteCommandToCorrectExecutor() {
        SpringCommandBus commandBus = context.getBean(SpringCommandBus.class);
        Result<String> result = commandBus.execute(new TestCommand("hello"));
        assertTrue(result.isSuccess());
        assertEquals("executed: hello", result.getData());
    }

    @Test
    @DisplayName("防御：未注册的 command 应抛出 IllegalStateException")
    void shouldThrowForUnregisteredCommand() {
        SpringCommandBus commandBus = context.getBean(SpringCommandBus.class);
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> commandBus.execute(new UnknownCommand())
        );
        assertTrue(ex.getMessage().contains("No executor found for command"),
                "异常消息应包含 'No executor found for command'");
        assertTrue(ex.getMessage().contains(UnknownCommand.class.getName()),
                "异常消息应包含未注册命令的类名");
    }

    @Test
    @DisplayName("防御：command 为 null 时应抛出 IllegalArgumentException")
    void shouldThrowForNullCommand() {
        SpringCommandBus commandBus = context.getBean(SpringCommandBus.class);
        assertThrows(IllegalArgumentException.class, () -> commandBus.execute(null));
    }

    @Test
    @DisplayName("路由：多个执行器应各自路由到正确的命令类型")
    void shouldRouteMultipleExecutorsCorrectly() {
        SpringCommandBus commandBus = context.getBean(SpringCommandBus.class);
        Result<String> r1 = commandBus.execute(new TestCommand("a"));
        Result<String> r2 = commandBus.execute(new AnotherCommand("b"));
        assertEquals("executed: a", r1.getData());
        assertEquals("another: b", r2.getData());
    }

    @Test
    void failedDiscoveryMustNotPublishPartialRoutes() {
        try (AnnotationConfigApplicationContext isolated = new AnnotationConfigApplicationContext()) {
            isolated.registerBean("first", TestCommandExecutor.class);
            isolated.registerBean("duplicate", TestCommandExecutor.class);
            isolated.refresh();
            SpringCommandBus bus = new SpringCommandBus(isolated);
            assertThrows(IllegalStateException.class, bus::afterSingletonsInstantiated);
            assertThrows(IllegalStateException.class, () -> bus.execute(new TestCommand("partial")));
        }
    }

    @Test
    void discoversAtLifecycleCallbackAndPreservesHandlerExceptionIdentity() {
        try (AnnotationConfigApplicationContext isolated = new AnnotationConfigApplicationContext()) {
            SpringCommandBus bus = new SpringCommandBus(isolated);
            IllegalStateException failure = new IllegalStateException("No executor found for command: business");
            isolated.registerBean("late", CommandExecutor.class, () -> new CommandExecutor<TestCommand>() {
                public Set<Class<? extends Command>> supportedCommands() {
                    return Set.of(TestCommand.class);
                }

                public Result execute(TestCommand command) {
                    throw failure;
                }
            });
            isolated.refresh();
            bus.afterSingletonsInstantiated();
            assertSame(failure, assertThrows(IllegalStateException.class,
                    () -> bus.execute(new TestCommand("late"))));
        }
    }

    @Test
    void explicitEntryReusesLegacyOrCustomBusRegardlessOfRegistrationOrder() {
        for (Class<?> existing : new Class<?>[]{LegacyConfig.class, CustomConfig.class}) {
            for (boolean reverse : new boolean[]{false, true}) {
                try (AnnotationConfigApplicationContext isolated = new AnnotationConfigApplicationContext()) {
                    isolated.register(reverse ? existing : ExplicitConfig.class,
                            reverse ? ExplicitConfig.class : existing);
                    isolated.refresh();
                    assertEquals(1, isolated.getBeansOfType(io.ddd4j.core.cqrs.command.CommandBus.class).size());
                    assertEquals("executed: explicit", isolated.getBean(io.ddd4j.core.cqrs.command.CommandBus.class)
                            .execute(new TestCommand("explicit")).getData());
                }
            }
        }
        try (AnnotationConfigApplicationContext isolated = new AnnotationConfigApplicationContext(ExplicitConfig.class)) {
            assertEquals(1, isolated.getBeansOfType(io.ddd4j.core.cqrs.command.CommandBus.class).size());
            assertInstanceOf(SpringCommandBus.class, isolated.getBean("springCommandBus"));
        }
    }

    @Test
    void eachHistoricalComponentScanWorksAlongsideExplicitEntry() {
        for (Class<?> type : new Class<?>[]{SpringCommandBus.class, io.ddd4j.data.cqrs.spring.SpringCommandBus.class}) {
            try (AnnotationConfigApplicationContext isolated = new AnnotationConfigApplicationContext()) {
                isolated.registerBean(io.ddd4j.core.cqrs.command.CommandRegistry.class);
                isolated.registerBean(TestCommandExecutor.class);
                isolated.register(ExplicitConfig.class);
                org.springframework.context.annotation.ClassPathBeanDefinitionScanner scanner =
                        new org.springframework.context.annotation.ClassPathBeanDefinitionScanner(isolated, false);
                scanner.addIncludeFilter(new org.springframework.core.type.filter.AssignableTypeFilter(type));
                scanner.scan(type.getPackageName());
                isolated.refresh();
                assertEquals(1, isolated.getBeansOfType(io.ddd4j.core.cqrs.command.CommandBus.class).size());
                assertEquals(type, isolated.getBean("springCommandBus").getClass());
            }
        }
    }

    @Test
    void explicitEntryReusesPrototypeAndCustomScopedBusWithoutEagerCreation() {
        for (String scope : new String[]{"prototype", "requestLike"}) {
            try (AnnotationConfigApplicationContext isolated = new AnnotationConfigApplicationContext()) {
                java.util.concurrent.atomic.AtomicInteger creations = new java.util.concurrent.atomic.AtomicInteger();
                isolated.getBeanFactory().registerScope("requestLike", new org.springframework.context.support.SimpleThreadScope());
                isolated.register(ExplicitConfig.class);
                isolated.registerBean("customScopedBus", io.ddd4j.core.cqrs.command.CommandBus.class, () -> {
                    creations.incrementAndGet();
                    return new io.ddd4j.core.cqrs.command.DefaultCommandBus(java.util.List.of(new TestCommandExecutor()));
                }, definition -> definition.setScope(scope));
                isolated.refresh();
                assertArrayEquals(new String[]{"customScopedBus"},
                        isolated.getBeanNamesForType(io.ddd4j.core.cqrs.command.CommandBus.class));
                assertEquals(0, creations.get(), "Bean existence checks must not eagerly instantiate custom scopes");
                assertEquals("executed: scoped", isolated.getBean(io.ddd4j.core.cqrs.command.CommandBus.class)
                        .execute(new TestCommand("scoped")).getData());
                assertEquals(1, creations.get());
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    @org.springframework.context.annotation.Import(io.ddd4j.spring.config.SpringCommandConfig.class)
    static class ExplicitConfig {
    }

    @Configuration(proxyBeanMethods = false)
    static class LegacyConfig {
        @Bean
        TestCommandExecutor executor() {
            return new TestCommandExecutor();
        }

        @Bean
        io.ddd4j.data.cqrs.spring.SpringCommandBus springCommandBus(org.springframework.context.ApplicationContext context) {
            return new io.ddd4j.data.cqrs.spring.SpringCommandBus(context,
                    new io.ddd4j.core.cqrs.command.CommandRegistry());
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomConfig {
        @Bean
        io.ddd4j.core.cqrs.command.CommandBus customBus() {
            return new io.ddd4j.core.cqrs.command.DefaultCommandBus(java.util.List.of(new TestCommandExecutor()));
        }
    }

    // ========== 测试用命令和执行器 ==========

    /**
     * 测试命令
     */
    static class TestCommand implements Command {
        private final String value;

        TestCommand(String value) {
            this.value = value;
        }

        String getValue() {
            return value;
        }
    }

    /**
     * 另一个测试命令
     */
    static class AnotherCommand implements Command {
        private final String value;

        AnotherCommand(String value) {
            this.value = value;
        }

        String getValue() {
            return value;
        }
    }

    /**
     * 未注册的命令
     */
    static class UnknownCommand implements Command {
    }

    /**
     * TestCommand 执行器
     */
    static class TestCommandExecutor implements CommandExecutor<TestCommand> {

        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return java.util.Collections.<Class<? extends Command>>singleton(TestCommand.class);
        }

        @Override
        public Result execute(TestCommand command) {
            return Result.ok("executed: " + command.getValue());
        }
    }

    /**
     * AnotherCommand 执行器
     */
    static class AnotherCommandExecutor implements CommandExecutor<AnotherCommand> {

        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return java.util.Collections.<Class<? extends Command>>singleton(AnotherCommand.class);
        }

        @Override
        public Result execute(AnotherCommand command) {
            return Result.ok("another: " + command.getValue());
        }
    }

    /**
     * 测试配置
     */
    @Configuration
    static class TestConfig {

        @Bean
        public SpringCommandBus springCommandBus(AnnotationConfigApplicationContext context) {
            return new SpringCommandBus(context);
        }

        @Bean
        public TestCommandExecutor testCommandExecutor() {
            return new TestCommandExecutor();
        }

        @Bean
        public AnotherCommandExecutor anotherCommandExecutor() {
            return new AnotherCommandExecutor();
        }
    }
}
