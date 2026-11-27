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
package io.ddd4j.quarkus.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link QuarkusCommandBus} 命令路由与执行测试。
 *
 * @author ddd4j
 * @since 1.0.x
 */
class QuarkusCommandBusTest {

    /** \u6d4b\u8bd5\u4ece\u6846\u67b6\u73af\u5883\u53d1\u73b0\u7684\u6ce8\u518c\u9879\u6216\u6267\u884c\u5668\u3002 */
    private final java.util.List<CommandExecutor<?>> discovered = new java.util.ArrayList<>();
    /** \u6267\u884c\u7528\u4f8b\u547d\u4ee4\u5e76\u66b4\u9732\u8def\u7531\u7ed3\u679c\u7684\u547d\u4ee4\u603b\u7ebf\u3002 */
    private QuarkusCommandBus commandBus;

    /** \u6d4b\u8bd5\u6267\u884c\u524d\u6267\u884c <code>discovered.add()</code>\u3001<code>org.mockito.Mockito.mock()</code>\u3001<code>commandBus.executors.iterator()</code>\u3001<code>discovered.iterator()</code> \u5b8c\u6210\u8d44\u6e90\u51c6\u5907\u3002 */
    @BeforeEach
    void setUp() {
        commandBus = new QuarkusCommandBus();
        discovered.add(new TestCommandExecutor());
        commandBus.executors = org.mockito.Mockito.mock(jakarta.enterprise.inject.Instance.class);
        org.mockito.Mockito.when(commandBus.executors.iterator()).thenAnswer(ignored -> discovered.iterator());
        org.mockito.Mockito.doCallRealMethod().when(commandBus.executors).forEach(org.mockito.ArgumentMatchers.any());
        commandBus.onStart(new io.quarkus.runtime.StartupEvent());
    }

    /** \u9a8c\u8bc1<code>commandBus.onStart(new io.quarkus.runtime.StartupEvent())</code> \u629b\u51fa <code>IllegalStateException.class</code>\u3002 */
    @Test
    void startupRejectsDuplicateExecutors() {
        discovered.add(new TestCommandExecutor());
        assertThatThrownBy(() -> commandBus.onStart(new io.quarkus.runtime.StartupEvent()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining(TestCommand.class.getName());
    }

    /** \u9a8c\u8bc1<code>commandBus.execute(new FailCommand()).getMessage()</code> \u7b49\u4e8e <code>first.getMessage()</code>\u3002 */
    @Test
    void discoversLateCdiExecutorAndCachesItsRoute() {
        discovered.add(new FailCommandExecutor());
        Result<?> first = commandBus.execute(new FailCommand());
        discovered.clear();
        assertThat(commandBus.execute(new FailCommand()).getMessage()).isEqualTo(first.getMessage());
    }

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>discovered.add()</code>\u3001<code>commandBus.execute()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void propagatesExecutorExceptionWithoutWrapping() {
        IllegalStateException failure = new IllegalStateException("application failure");
        discovered.add(new FailCommandExecutor() {
            @Override
            public Result<String> execute(FailCommand command) {
                throw failure;
            }
        });
        assertThatThrownBy(() -> commandBus.execute(new FailCommand())).isSameAs(failure);
    }

    /** \u9a8c\u8bc1<code>result</code>\u975e\u7a7a\uff0c<code>result.isSuccess()</code>\u4e3a\u771f\uff0c<code>result.getData()</code> \u7b49\u4e8e <code>"executed: hello"</code>\u3002 */
    @Test
    void executeShouldRouteToRegisteredExecutor() {
        Result<String> result = commandBus.execute(new TestCommand("hello"));

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo("executed: hello");
    }

    /** \u9a8c\u8bc1<code>commandBus.execute(null)</code> \u629b\u51fa <code>IllegalArgumentException.class</code>\u3002 */
    @Test
    void executeShouldThrowWhenCommandIsNull() {
        assertThatThrownBy(() -> commandBus.execute(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Command cannot be null");
    }

    /** \u9a8c\u8bc1<code>commandBus.execute(new UnregisteredCommand())</code> \u629b\u51fa <code>IllegalStateException.class</code>\u3002 */
    @Test
    void executeShouldThrowWhenNoExecutorRegistered() {
        assertThatThrownBy(() -> commandBus.execute(new UnregisteredCommand()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No executor found for command:");
    }

    /** \u9a8c\u8bc1<code>result</code>\u975e\u7a7a\uff0c<code>result.isSuccess()</code>\u4e3a\u771f\uff0c<code>result.getData()</code> \u7b49\u4e8e <code>"executed: test"</code>\u3002 */
    @Test
    void executeVoidShouldDelegateToExecute() {
        Result<?> result = commandBus.executeVoid(new TestCommand("test"));

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo("executed: test");
    }

    /** \u9a8c\u8bc1<code>result</code>\u975e\u7a7a\uff0c<code>result.isSuccess()</code>\u4e3a\u5047\uff0c<code>result.getMessage()</code> \u7b49\u4e8e <code>"intentional failure"</code>\u3002 */
    @Test
    void executeShouldReturnFailureResultWhenExecutorReturnsFail() {
        discovered.add(new FailCommandExecutor());

        Result<String> result = commandBus.execute(new FailCommand());

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo("intentional failure");
    }

    // --- 测试用命令和执行器 ---

    /** \u643a\u5e26\u5b57\u7b26\u4e32\u8d1f\u8f7d\u7684\u6d4b\u8bd5\u547d\u4ee4\uff0c\u7531\u5df2\u6ce8\u518c\u7684\u6267\u884c\u5668\u5904\u7406\uff0c\u7528\u4e8e\u9a8c\u8bc1\u6b63\u5e38\u8def\u7531\u3002 */
    final static class TestCommand implements Command {

        /** Java \u5e8f\u5217\u5316\u4f7f\u7528\u7684\u7248\u672c\u6807\u8bc6\u3002 */
        private static final long serialVersionUID = 0L;

        /** \u5f53\u524d\u4e8b\u4ef6\u6216\u8bf7\u6c42\u5939\u5177\u643a\u5e26\u7684\u8d1f\u8f7d\u3002 */
        private final String payload;

        /**
         * \u8bbe\u7f6e <code>payload</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>TestCommand</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param payload \u5f53\u524d\u4e8b\u4ef6\u6216\u8bf7\u6c42\u5939\u5177\u643a\u5e26\u7684\u8d1f\u8f7d
         */
        @JsonCreator()
        TestCommand(@JsonProperty("payload") String payload) {
            this.payload = payload;
        }

        /**
         * \u8bfb\u53d6<code>payload</code> \u5b57\u6bb5\u3002
         * @return <code>payload</code> \u5b57\u6bb5
         */
        @JsonProperty("payload")
        public String payload() {
            return payload;
        }

        /** {@inheritDoc} */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (Objects.isNull(obj) || getClass() != obj.getClass()) {
                return false;
            }
            TestCommand other = (TestCommand) obj;
            return Objects.equals(this.payload, other.payload);
        }

        /** {@inheritDoc} */
        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(payload);
            return result;
        }

        /** {@inheritDoc} */
        @Override
        public String toString() {
            return "TestCommand[payload=" + payload + "]";
        }
    }

    /**
     * 未注册执行器的测试命令，用于验证未知命令抛出异常。
     */
    final static class UnregisteredCommand implements Command {

        /** Java \u5e8f\u5217\u5316\u4f7f\u7528\u7684\u7248\u672c\u6807\u8bc6\u3002 */
        private static final long serialVersionUID = 0L;

        /** \u521b\u5efa\u5b57\u6bb5\u4f7f\u7528\u9ed8\u8ba4\u521d\u59cb\u503c\u7684 <code>UnregisteredCommand</code> \u6d4b\u8bd5\u5bf9\u8c61\u3002 */
        @JsonCreator()
        UnregisteredCommand() {
        }

        /** {@inheritDoc} */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (Objects.isNull(obj) || getClass() != obj.getClass()) {
                return false;
            }
            UnregisteredCommand other = (UnregisteredCommand) obj;
            return true;
        }

        /** {@inheritDoc} */
        @Override
        public int hashCode() {
            int result = 0;
            return result;
        }

        /** {@inheritDoc} */
        @Override
        public String toString() {
            return "UnregisteredCommand[]";
        }
    }

    /**
     * 会返回失败结果的测试命令，用于验证失败结果透传。
     */
    final static class FailCommand implements Command {

        /** Java \u5e8f\u5217\u5316\u4f7f\u7528\u7684\u7248\u672c\u6807\u8bc6\u3002 */
        private static final long serialVersionUID = 0L;

        /** \u521b\u5efa\u5b57\u6bb5\u4f7f\u7528\u9ed8\u8ba4\u521d\u59cb\u503c\u7684 <code>FailCommand</code> \u6d4b\u8bd5\u5bf9\u8c61\u3002 */
        @JsonCreator()
        FailCommand() {
        }

        /** {@inheritDoc} */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (Objects.isNull(obj) || getClass() != obj.getClass()) {
                return false;
            }
            FailCommand other = (FailCommand) obj;
            return true;
        }

        /** {@inheritDoc} */
        @Override
        public int hashCode() {
            int result = 0;
            return result;
        }

        /** {@inheritDoc} */
        @Override
        public String toString() {
            return "FailCommand[]";
        }
    }

    /**
     * {@link TestCommand} 的执行器，返回携带负载的成功结果。
     */
    static class TestCommandExecutor implements CommandExecutor<TestCommand> {

        /** {@inheritDoc} */
        @Override
        public Result<String> execute(TestCommand command) {
            return Result.ok("executed: " + command.payload());
        }

        /** {@inheritDoc} */
        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return Collections.<Class<? extends Command>>singleton(TestCommand.class);
        }
    }

    /**
     * {@link FailCommand} 的执行器，恒返回失败结果。
     */
    static class FailCommandExecutor implements CommandExecutor<FailCommand> {

        /** {@inheritDoc} */
        @Override
        public Result<String> execute(FailCommand command) {
            return Result.fail("intentional failure");
        }

        /** {@inheritDoc} */
        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return Collections.<Class<? extends Command>>singleton(FailCommand.class);
        }
    }
}
