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

    /** \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u6846\u67b6\u4e0a\u4e0b\u6587\u6216\u8bf7\u6c42\u4f5c\u7528\u57df\u3002 */
    private AnnotationConfigApplicationContext context;

    /** \u6839\u636e\u672c\u6b21\u8c03\u7528\u66f4\u65b0 <code>context = new AnnotationConfigApplicationContext(TestConfig.class)</code>\uff0c\u7528\u4e8e\u540e\u7eed\u6d4b\u8bd5\u6b65\u9aa4\u3002 */
    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(TestConfig.class);
    }

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>context.close()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    /** \u6ce8\u518c\uff1a\u5bb9\u5668\u5237\u65b0\u540e\u5e94\u81ea\u52a8\u53d1\u73b0\u5e76\u6ce8\u518c CommandExecutor Bean\u3002 */
    @Test
    @DisplayName("注册：容器刷新后应自动发现并注册 CommandExecutor Bean")
    void shouldRegisterExecutorsOnContextRefresh() {
        SpringCommandBus commandBus = context.getBean(SpringCommandBus.class);
        assertNotNull(commandBus, "SpringCommandBus 应作为 Bean 注册");
    }

    /** \u8def\u7531\uff1aexecute \u5e94\u6b63\u786e\u8def\u7531\u5230\u5bf9\u5e94\u6267\u884c\u5668\u3002 */
    @Test
    @DisplayName("路由：execute 应正确路由到对应执行器")
    void shouldRouteCommandToCorrectExecutor() {
        SpringCommandBus commandBus = context.getBean(SpringCommandBus.class);
        Result<String> result = commandBus.execute(new TestCommand("hello"));
        assertTrue(result.isSuccess());
        assertEquals("executed: hello", result.getData());
    }

    /** \u9632\u5fa1\uff1a\u672a\u6ce8\u518c\u7684 command \u5e94\u629b\u51fa IllegalStateException\u3002 */
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

    /** \u9632\u5fa1\uff1acommand \u4e3a null \u65f6\u5e94\u629b\u51fa IllegalArgumentException\u3002 */
    @Test
    @DisplayName("防御：command 为 null 时应抛出 IllegalArgumentException")
    void shouldThrowForNullCommand() {
        SpringCommandBus commandBus = context.getBean(SpringCommandBus.class);
        assertThrows(IllegalArgumentException.class, () -> commandBus.execute(null));
    }

    /** \u8def\u7531\uff1a\u591a\u4e2a\u6267\u884c\u5668\u5e94\u5404\u81ea\u8def\u7531\u5230\u6b63\u786e\u7684\u547d\u4ee4\u7c7b\u578b\u3002 */
    @Test
    @DisplayName("路由：多个执行器应各自路由到正确的命令类型")
    void shouldRouteMultipleExecutorsCorrectly() {
        SpringCommandBus commandBus = context.getBean(SpringCommandBus.class);
        Result<String> r1 = commandBus.execute(new TestCommand("a"));
        Result<String> r2 = commandBus.execute(new AnotherCommand("b"));
        assertEquals("executed: a", r1.getData());
        assertEquals("another: b", r2.getData());
    }

    // ========== 测试用命令和执行器 ==========

    /**
     * 测试命令
     */
    static class TestCommand implements Command {
        /** \u53d1\u9001\u7ed9\u5f53\u524d\u5939\u5177\u6216\u7528\u4e8e\u65ad\u8a00\u5f80\u8fd4\u7ed3\u679c\u7684\u503c\u3002 */
        private final String value;

        /**
         * \u8bbe\u7f6e <code>value</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>TestCommand</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param value \u5f53\u524d\u7528\u4f8b\u4f20\u5165\u7684\u503c
         */
        TestCommand(String value) {
            this.value = value;
        }

        /**
         * \u8bfb\u53d6<code>value</code> \u5b57\u6bb5\u3002
         * @return <code>value</code> \u5b57\u6bb5
         */
        String getValue() {
            return value;
        }
    }

    /**
     * 另一个测试命令
     */
    static class AnotherCommand implements Command {
        /** \u53d1\u9001\u7ed9\u5f53\u524d\u5939\u5177\u6216\u7528\u4e8e\u65ad\u8a00\u5f80\u8fd4\u7ed3\u679c\u7684\u503c\u3002 */
        private final String value;

        /**
         * \u8bbe\u7f6e <code>value</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>AnotherCommand</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param value \u5f53\u524d\u7528\u4f8b\u4f20\u5165\u7684\u503c
         */
        AnotherCommand(String value) {
            this.value = value;
        }

        /**
         * \u8bfb\u53d6<code>value</code> \u5b57\u6bb5\u3002
         * @return <code>value</code> \u5b57\u6bb5
         */
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

        /** {@inheritDoc} */
        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return java.util.Collections.<Class<? extends Command>>singleton(TestCommand.class);
        }

        /** {@inheritDoc} */
        @Override
        public Result execute(TestCommand command) {
            return Result.ok("executed: " + command.getValue());
        }
    }

    /**
     * AnotherCommand 执行器
     */
    static class AnotherCommandExecutor implements CommandExecutor<AnotherCommand> {

        /** {@inheritDoc} */
        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return java.util.Collections.<Class<? extends Command>>singleton(AnotherCommand.class);
        }

        /** {@inheritDoc} */
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

        /**
         * \u521b\u5efa <code>new SpringCommandBus</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
         * @param context \u5f53\u524d\u6846\u67b6\u4fdd\u5b58\u8bf7\u6c42\u6216\u8fd0\u884c\u65f6\u72b6\u6001\u7684\u4e0a\u4e0b\u6587
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new SpringCommandBus(context)</code>
         */
        @Bean
        public SpringCommandBus springCommandBus(AnnotationConfigApplicationContext context) {
            return new SpringCommandBus(context);
        }

        /**
         * \u521b\u5efa <code>new TestCommandExecutor</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new TestCommandExecutor()</code>
         */
        @Bean
        public TestCommandExecutor testCommandExecutor() {
            return new TestCommandExecutor();
        }

        /**
         * \u521b\u5efa <code>new AnotherCommandExecutor</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new AnotherCommandExecutor()</code>
         */
        @Bean
        public AnotherCommandExecutor anotherCommandExecutor() {
            return new AnotherCommandExecutor();
        }
    }
}
