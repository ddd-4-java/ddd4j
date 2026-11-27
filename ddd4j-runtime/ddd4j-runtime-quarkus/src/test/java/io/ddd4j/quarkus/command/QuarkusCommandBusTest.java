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
import io.ddd4j.core.api.R;
import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandExecutor;
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

    /**
     * 测试从框架环境发现的注册项或执行器。
     */
    private final java.util.List<CommandExecutor<?>> discovered = new java.util.ArrayList<>();
    /**
     * 执行用例命令并暴露路由结果的命令总线。
     */
    private QuarkusCommandBus commandBus;

    /**
     * 测试执行前执行 <code>discovered.add()</code>、<code>org.mockito.Mockito.mock()</code>、<code>commandBus.executors.iterator()</code>、<code>discovered.iterator()</code> 完成资源准备。
     */
    @BeforeEach
    void setUp() {
        commandBus = new QuarkusCommandBus();
        discovered.add(new TestCommandExecutor());
        commandBus.executors = org.mockito.Mockito.mock(javax.enterprise.inject.Instance.class);
        org.mockito.Mockito.when(commandBus.executors.iterator()).thenAnswer(ignored -> discovered.iterator());
        org.mockito.Mockito.doCallRealMethod().when(commandBus.executors).forEach(org.mockito.ArgumentMatchers.any());
        commandBus.onStart(new io.quarkus.runtime.StartupEvent());
    }

    /**
     * 验证<code>commandBus.onStart(new io.quarkus.runtime.StartupEvent())</code> 抛出 <code>IllegalStateException.class</code>。
     */
    @Test
    void startupRejectsDuplicateExecutors() {
        discovered.add(new TestCommandExecutor());
        assertThatThrownBy(() -> commandBus.onStart(new io.quarkus.runtime.StartupEvent()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining(TestCommand.class.getName());
    }

    /**
     * 验证<code>commandBus.execute(new FailCommand()).getMessage()</code> 等于 <code>first.getMessage()</code>。
     */
    @Test
    void discoversLateCdiExecutorAndCachesItsRoute() {
        discovered.add(new FailCommandExecutor());
        R<?> first = commandBus.execute(new FailCommand());
        discovered.clear();
        assertThat(commandBus.execute(new FailCommand()).getMessage()).isEqualTo(first.getMessage());
    }

    /**
     * 在测试框架中执行 <code>discovered.add()</code>、<code>commandBus.execute()</code>，验证该场景。
     */
    @Test
    void propagatesExecutorExceptionWithoutWrapping() {
        IllegalStateException failure = new IllegalStateException("application failure");
        discovered.add(new FailCommandExecutor() {
            @Override
            public R<String> execute(FailCommand command) {
                throw failure;
            }
        });
        assertThatThrownBy(() -> commandBus.execute(new FailCommand())).isSameAs(failure);
    }

    /**
     * 验证<code>result</code>非空，<code>result.isOk()</code>为真，<code>result.getData()</code> 等于 <code>"executed: hello"</code>。
     */
    @Test
    void executeShouldRouteToRegisteredExecutor() {
        R<String> result = commandBus.execute(new TestCommand("hello"));

        assertThat(result).isNotNull();
        assertThat(result.isOk()).isTrue();
        assertThat(result.getData()).isEqualTo("executed: hello");
    }

    /**
     * 验证<code>commandBus.execute(null)</code> 抛出 <code>IllegalArgumentException.class</code>。
     */
    @Test
    void executeShouldThrowWhenCommandIsNull() {
        assertThatThrownBy(() -> commandBus.execute(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Command cannot be null");
    }

    /**
     * 验证<code>commandBus.execute(new UnregisteredCommand())</code> 抛出 <code>IllegalStateException.class</code>。
     */
    @Test
    void executeShouldThrowWhenNoExecutorRegistered() {
        assertThatThrownBy(() -> commandBus.execute(new UnregisteredCommand()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No executor found for command:");
    }

    /**
     * 验证<code>result</code>非空，<code>result.isOk()</code>为真，<code>result.getData()</code> 等于 <code>"executed: test"</code>。
     */
    @Test
    void executeVoidShouldDelegateToExecute() {
        R<?> result = commandBus.executeVoid(new TestCommand("test"));

        assertThat(result).isNotNull();
        assertThat(result.isOk()).isTrue();
        assertThat(result.getData()).isEqualTo("executed: test");
    }

    /**
     * 验证<code>result</code>非空，<code>result.isOk()</code>为假，<code>result.getMsg()</code> 等于 <code>"intentional failure"</code>。
     */
    @Test
    void executeShouldReturnFailureResultWhenExecutorReturnsFail() {
        discovered.add(new FailCommandExecutor());

        R<String> result = commandBus.execute(new FailCommand());

        assertThat(result).isNotNull();
        assertThat(result.isOk()).isFalse();
        assertThat(result.getMsg()).isEqualTo("intentional failure");
    }

    // --- 测试用命令和执行器 ---

    /**
     * 携带字符串负载的测试命令，由已注册的执行器处理，用于验证正常路由。
     */
    final static class TestCommand implements Command {

        /**
         * Java 序列化使用的版本标识。
         */
        private static final long serialVersionUID = 0L;

        /**
         * 当前事件或请求夹具携带的负载。
         */
        private final String payload;

        /**
         * 设置 <code>payload</code>，用于构造 <code>TestCommand</code> 测试实例。
         *
         * @param payload 当前事件或请求夹具携带的负载
         */
        @JsonCreator()
        TestCommand(@JsonProperty("payload") String payload) {
            this.payload = payload;
        }

        /**
         * 读取<code>payload</code> 字段。
         *
         * @return <code>payload</code> 字段
         */
        @JsonProperty("payload")
        public String payload() {
            return payload;
        }

        /**
         * 根据<code>payload</code>比较对象的值是否相等。
         *
         * <p>只比较运行时类型相同的对象。</p>
         *
         * @param obj 用来比较或转换的输入对象
         * @return 对象满足此实现的相等条件时为真
         */
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

        /**
         * 计算与对象相等条件一致的哈希值。
         *
         * @return 由参与相等比较的状态计算出的哈希值
         */
        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(payload);
            return result;
        }

        /**
         * 生成用于调试和日志的对象描述，包含<code>payload</code>。
         *
         * @return 表示当前对象状态的字符串
         */
        @Override
        public String toString() {
            return "TestCommand[payload=" + payload + "]";
        }
    }

    /**
     * 未注册执行器的测试命令，用于验证未知命令抛出异常。
     */
    final static class UnregisteredCommand implements Command {

        /**
         * Java 序列化使用的版本标识。
         */
        private static final long serialVersionUID = 0L;

        /**
         * 创建字段使用默认初始值的 <code>UnregisteredCommand</code> 测试对象。
         */
        @JsonCreator()
        UnregisteredCommand() {
        }

        /**
         * 比较两个对象的值是否相等。
         *
         * <p>只比较运行时类型相同的对象。</p>
         *
         * @param obj 用来比较或转换的输入对象
         * @return 对象满足此实现的相等条件时为真
         */
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

        /**
         * 计算与对象相等条件一致的哈希值。
         *
         * @return 由参与相等比较的状态计算出的哈希值
         */
        @Override
        public int hashCode() {
            int result = 0;
            return result;
        }

        /**
         * 生成用于调试和日志的对象描述。
         *
         * @return 表示当前对象状态的字符串
         */
        @Override
        public String toString() {
            return "UnregisteredCommand[]";
        }
    }

    /**
     * 会返回失败结果的测试命令，用于验证失败结果透传。
     */
    final static class FailCommand implements Command {

        /**
         * Java 序列化使用的版本标识。
         */
        private static final long serialVersionUID = 0L;

        /**
         * 创建字段使用默认初始值的 <code>FailCommand</code> 测试对象。
         */
        @JsonCreator()
        FailCommand() {
        }

        /**
         * 比较两个对象的值是否相等。
         *
         * <p>只比较运行时类型相同的对象。</p>
         *
         * @param obj 用来比较或转换的输入对象
         * @return 对象满足此实现的相等条件时为真
         */
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

        /**
         * 计算与对象相等条件一致的哈希值。
         *
         * @return 由参与相等比较的状态计算出的哈希值
         */
        @Override
        public int hashCode() {
            int result = 0;
            return result;
        }

        /**
         * 生成用于调试和日志的对象描述。
         *
         * @return 表示当前对象状态的字符串
         */
        @Override
        public String toString() {
            return "FailCommand[]";
        }
    }

    /**
     * {@link TestCommand} 的执行器，返回携带负载的成功结果。
     */
    static class TestCommandExecutor implements CommandExecutor<TestCommand> {

        /**
         * 用当前处理器执行传入命令或操作。
         *
         * <p>测试夹具会调用 <code>R.ok("executed: " + command.payload())</code>、<code>command.payload()</code>。</p>
         *
         * @param command 命令对象
         * @return 实际返回<code>R.ok("executed: " + command.payload())</code>
         */
        @Override
        public R<String> execute(TestCommand command) {
            return R.ok("executed: " + command.payload());
        }

        /**
         * 声明当前处理器可以接收的命令类型。
         *
         * <p>测试夹具会调用 <code>Collections.&lt;Class&lt;? extends Command&gt;&gt;singleton(TestCommand.class)</code>。</p>
         *
         * @return 实际返回<code>Collections.&lt;Class&lt;? extends Command&gt;&gt;singleton(TestCommand.class)</code>
         */
        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return Collections.<Class<? extends Command>>singleton(TestCommand.class);
        }
    }

    /**
     * {@link FailCommand} 的执行器，恒返回失败结果。
     */
    static class FailCommandExecutor implements CommandExecutor<FailCommand> {

        /**
         * 用当前处理器执行传入命令或操作。
         *
         * <p>测试夹具会调用 <code>R.fail("intentional failure")</code>。</p>
         *
         * @param command 命令对象
         * @return 实际返回<code>R.fail("intentional failure")</code>
         */
        @Override
        public R<String> execute(FailCommand command) {
            return R.fail("intentional failure");
        }

        /**
         * 声明当前处理器可以接收的命令类型。
         *
         * <p>测试夹具会调用 <code>Collections.&lt;Class&lt;? extends Command&gt;&gt;singleton(FailCommand.class)</code>。</p>
         *
         * @return 实际返回<code>Collections.&lt;Class&lt;? extends Command&gt;&gt;singleton(FailCommand.class)</code>
         */
        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return Collections.<Class<? extends Command>>singleton(FailCommand.class);
        }
    }
}
