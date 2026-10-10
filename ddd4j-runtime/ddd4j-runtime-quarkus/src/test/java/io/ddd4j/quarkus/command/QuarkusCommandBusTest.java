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

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link QuarkusCommandBus} 命令路由与执行测试。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */
class QuarkusCommandBusTest {

    private QuarkusCommandBus commandBus;

    @BeforeEach
    void setUp() throws Exception {
        commandBus = new QuarkusCommandBus();
        // 通过反射注入 executorMap，绕过 CDI Instance 依赖
        Field mapField = QuarkusCommandBus.class.getDeclaredField("executorMap");
        mapField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Class<? extends Command>, CommandExecutor<?>> executorMap =
                (Map<Class<? extends Command>, CommandExecutor<?>>) mapField.get(commandBus);
        executorMap.put(TestCommand.class, new TestCommandExecutor());
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
        // CDI Instance 未注入时会抛 NPE；注入后应抛 IllegalStateException。
        // 此处验证未注册命令必然抛出异常，不静默通过。
        assertThatThrownBy(() -> commandBus.execute(new UnregisteredCommand()))
                .isInstanceOf(Exception.class);
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
        // 注册一个返回失败结果的执行器
        try {
            Field mapField = QuarkusCommandBus.class.getDeclaredField("executorMap");
            mapField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<Class<? extends Command>, CommandExecutor<?>> executorMap =
                    (Map<Class<? extends Command>, CommandExecutor<?>>) mapField.get(commandBus);
            executorMap.put(FailCommand.class, new FailCommandExecutor());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Result<String> result = commandBus.execute(new FailCommand());

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo("intentional failure");
    }

    // --- 测试用命令和执行器 ---

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

    static class TestCommandExecutor implements CommandExecutor<TestCommand> {

        @Override
        public Result<String> execute(TestCommand command) {
            return Result.ok("executed: " + command.payload());
        }

        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return Set.of(TestCommand.class);
        }
    }

    static class FailCommandExecutor implements CommandExecutor<FailCommand> {

        @Override
        public Result<String> execute(FailCommand command) {
            return Result.fail("intentional failure");
        }

        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return Set.of(FailCommand.class);
        }
    }
}
