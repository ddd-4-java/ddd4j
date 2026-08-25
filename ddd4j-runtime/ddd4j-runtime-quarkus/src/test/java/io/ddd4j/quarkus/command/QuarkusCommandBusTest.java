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
        assertThatThrownBy(() -> commandBus.execute(new UnregisteredCommand()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No executor found for command");
    }

    @Test
    void executeVoidShouldDelegateToExecute() {
        Result<?> result = commandBus.executeVoid(new TestCommand("test"));

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo("executed: test");
    }

    // --- 测试用命令和执行器 ---

    record TestCommand(String payload) implements Command {
    }

    record UnregisteredCommand() implements Command {
    }

    static class TestCommandExecutor implements CommandExecutor<TestCommand> {

        @Override
        @SuppressWarnings("unchecked")
        public Result<String> execute(TestCommand command) {
            return Result.ok("executed: " + command.payload());
        }

        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return Set.of(TestCommand.class);
        }
    }
}
