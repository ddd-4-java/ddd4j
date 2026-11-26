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

    private final java.util.List<CommandExecutor<?>> discovered = new java.util.ArrayList<>();
    private QuarkusCommandBus commandBus;

    @BeforeEach
    void setUp() {
        commandBus = new QuarkusCommandBus();
        discovered.add(new TestCommandExecutor());
        commandBus.executors = org.mockito.Mockito.mock(jakarta.enterprise.inject.Instance.class);
        org.mockito.Mockito.when(commandBus.executors.iterator()).thenAnswer(ignored -> discovered.iterator());
        org.mockito.Mockito.doCallRealMethod().when(commandBus.executors).forEach(org.mockito.ArgumentMatchers.any());
        commandBus.onStart(new io.quarkus.runtime.StartupEvent());
    }

    @Test
    void startupRejectsDuplicateExecutors() {
        discovered.add(new TestCommandExecutor());
        assertThatThrownBy(() -> commandBus.onStart(new io.quarkus.runtime.StartupEvent()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining(TestCommand.class.getName());
    }

    @Test
    void discoversLateCdiExecutorAndCachesItsRoute() {
        discovered.add(new FailCommandExecutor());
        Result<?> first = commandBus.execute(new FailCommand());
        discovered.clear();
        assertThat(commandBus.execute(new FailCommand()).getMessage()).isEqualTo(first.getMessage());
    }

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

    @Test
    void executeShouldRouteToRegisteredExecutor() {
        Result<String> result = commandBus.execute(new TestCommand("hello"));

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo("executed: hello");
    }

    @Test
    void executeShouldThrowWhenCommandIsNull() {
        assertThatThrownBy(() -> commandBus.execute(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Command cannot be null");
    }

    @Test
    void executeShouldThrowWhenNoExecutorRegistered() {
        assertThatThrownBy(() -> commandBus.execute(new UnregisteredCommand()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No executor found for command:");
    }

    @Test
    void executeVoidShouldDelegateToExecute() {
        Result<?> result = commandBus.executeVoid(new TestCommand("test"));

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo("executed: test");
    }

    @Test
    void executeShouldReturnFailureResultWhenExecutorReturnsFail() {
        discovered.add(new FailCommandExecutor());

        Result<String> result = commandBus.execute(new FailCommand());

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo("intentional failure");
    }

    // --- 测试用命令和执行器 ---

    /**
     * 携带字符串负载的测试命令，已注册执行器用于验证正常路由。
     *
     * @param payload 命令负载
     */
    final static class TestCommand implements Command {

        private static final long serialVersionUID = 0L;

        private final String payload;

        @JsonCreator()
        TestCommand(@JsonProperty("payload") String payload) {
            this.payload = payload;
        }

        @JsonProperty("payload")
        public String payload() {
            return payload;
        }

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

        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(payload);
            return result;
        }

        @Override
        public String toString() {
            return "TestCommand[payload=" + payload + "]";
        }
    }

    /**
     * 未注册执行器的测试命令，用于验证未知命令抛出异常。
     */
    final static class UnregisteredCommand implements Command {

        private static final long serialVersionUID = 0L;

        @JsonCreator()
        UnregisteredCommand() {
        }

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

        @Override
        public int hashCode() {
            int result = 0;
            return result;
        }

        @Override
        public String toString() {
            return "UnregisteredCommand[]";
        }
    }

    /**
     * 会返回失败结果的测试命令，用于验证失败结果透传。
     */
    final static class FailCommand implements Command {

        private static final long serialVersionUID = 0L;

        @JsonCreator()
        FailCommand() {
        }

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

        @Override
        public int hashCode() {
            int result = 0;
            return result;
        }

        @Override
        public String toString() {
            return "FailCommand[]";
        }
    }

    /**
     * {@link TestCommand} 的执行器，返回携带负载的成功结果。
     */
    static class TestCommandExecutor implements CommandExecutor<TestCommand> {

        @Override
        public Result<String> execute(TestCommand command) {
            return Result.ok("executed: " + command.payload());
        }

        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return Collections.<Class<? extends Command>>singleton(TestCommand.class);
        }
    }

    /**
     * {@link FailCommand} 的执行器，恒返回失败结果。
     */
    static class FailCommandExecutor implements CommandExecutor<FailCommand> {

        @Override
        public Result<String> execute(FailCommand command) {
            return Result.fail("intentional failure");
        }

        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return Collections.<Class<? extends Command>>singleton(FailCommand.class);
        }
    }
}
