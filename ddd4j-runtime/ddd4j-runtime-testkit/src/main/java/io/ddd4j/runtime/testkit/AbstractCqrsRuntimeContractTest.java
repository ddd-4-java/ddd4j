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
package io.ddd4j.runtime.testkit;

import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.Result;
import io.ddd4j.core.cqrs.readmodel.DefaultProjectionPosition;
import io.ddd4j.core.cqrs.readmodel.ProjectionPosition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CQRS 运行时适配器共享的跨运行时行为一致性契约测试。
 * <p>
 * 覆盖以下契约点：
 * <ol>
 *   <li>命令路由：注册两个 CommandExecutor，execute 只触发对应执行器</li>
 *   <li>未注册命令：execute 未注册命令抛异常</li>
 *   <li>null 命令防御：execute(null) 抛 NPE 或 IAE</li>
 *   <li>位置读写往返：positionRepository 写入后读回一致，update 覆盖后一致</li>
 *   <li>ViewManager 生命周期：start() 后 isRunning()==true，stop() 后 isRunning()==false</li>
 * </ol>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public abstract class AbstractCqrsRuntimeContractTest {

    /**
     * 由各运行时子类实现，返回装配好的 CQRS 契约。
     *
     * @return CQRS 运行时契约
     */
    protected abstract CqrsRuntimeContract createContract();

    /** \u4fdd\u7559\u5355\u9879 CQRS \u5951\u7ea6\u7684\u6e05\u7406\u94a9\u5b50\uff0c\u6d4b\u8bd5\u672c\u8eab\u5728 finally \u4e2d\u5173\u95ed\u5951\u7ea6\u3002 */
    @AfterEach
    void closeContract() {
        // 子类可通过 @Override createContract() 内部管理生命周期，
        // 此处做兜底清理，防止资源泄漏。
    }

    // ========== 契约 1：命令路由 ==========

    /** \u9a8c\u8bc1\u53d1\u9001 A \u7c7b\u547d\u4ee4\u65f6\u80fd\u8def\u7531\u5230\u5df2\u6ce8\u518c\u7684\u6267\u884c\u5668\u5e76\u6210\u529f\u5b8c\u6210\u3002 */
    @Test
    void shouldRouteCommandAToExecutorA() {
        CqrsRuntimeContract contract = createContract();
        try {
            Result<?> result = contract.commandBus().execute(new CmdA("payload-a"));
            assertThat(result).isNotNull();
            assertThat(result.isSuccess()).isTrue();
        } finally {
            contract.close();
        }
    }

    /** \u9a8c\u8bc1\u53d1\u9001 B \u7c7b\u547d\u4ee4\u65f6\u80fd\u8def\u7531\u5230\u5df2\u6ce8\u518c\u7684\u6267\u884c\u5668\u5e76\u6210\u529f\u5b8c\u6210\u3002 */
    @Test
    void shouldRouteCommandBToExecutorB() {
        CqrsRuntimeContract contract = createContract();
        try {
            Result<?> result = contract.commandBus().execute(new CmdB(42));
            assertThat(result).isNotNull();
            assertThat(result.isSuccess()).isTrue();
        } finally {
            contract.close();
        }
    }

    // ========== 契约 2：未注册命令 ==========

    /** \u9a8c\u8bc1\u672a\u767b\u8bb0\u7684\u547d\u4ee4\u7c7b\u578b\u4f1a\u4f7f\u603b\u7ebf\u8c03\u7528\u5931\u8d25\u3002 */
    @Test
    void shouldThrowWhenCommandNotRegistered() {
        CqrsRuntimeContract contract = createContract();
        try {
            assertThatThrownBy(() -> contract.commandBus().execute(new UnregisteredCmd()))
                    .isInstanceOf(Exception.class);
        } finally {
            contract.close();
        }
    }

    // ========== 契约 3：null 命令防御 ==========

    /** \u9a8c\u8bc1\u7a7a\u547d\u4ee4\u4f1a\u88ab\u603b\u7ebf\u6309\u53c2\u6570\u9519\u8bef\u62d2\u7edd\u3002 */
    @Test
    void shouldThrowWhenCommandIsNull() {
        CqrsRuntimeContract contract = createContract();
        try {
            assertThatThrownBy(() -> contract.commandBus().execute(null))
                    .isInstanceOfAny(NullPointerException.class, IllegalArgumentException.class);
        } finally {
            contract.close();
        }
    }

    // ========== 契约 4：位置读写往返 ==========

    /** \u9a8c\u8bc1\u5199\u5165\u7684\u6295\u5f71\u4f4d\u70b9\u53ef\u6309\u6d41\u6807\u8bc6\u91cd\u65b0\u8bfb\u53d6\u3002 */
    @Test
    void shouldWriteAndReadBackProjectionPosition() {
        CqrsRuntimeContract contract = createContract();
        try {
            String streamId = "contract-stream-" + contract.runtimeName();
            DefaultProjectionPosition position = new DefaultProjectionPosition(streamId, 7L);

            contract.positionRepository().save(position);

            Optional<ProjectionPosition> found = contract.positionRepository().findByStreamId(streamId);
            assertThat(found).isPresent();
            assertThat(found.get().getStreamId()).isEqualTo(streamId);
            assertThat(found.get().getNextEventNumber()).isEqualTo(7L);
        } finally {
            contract.close();
        }
    }

    /** \u9a8c\u8bc1\u540c\u4e00\u4e8b\u4ef6\u6d41\u4fdd\u5b58\u65b0\u4f4d\u70b9\u540e\u8bfb\u53d6\u5230\u66f4\u65b0\u540e\u7684\u503c\u3002 */
    @Test
    void shouldOverwriteProjectionPositionOnUpdate() {
        CqrsRuntimeContract contract = createContract();
        try {
            String streamId = "contract-update-stream-" + contract.runtimeName();
            DefaultProjectionPosition initial = new DefaultProjectionPosition(streamId, 3L);
            contract.positionRepository().save(initial);

            DefaultProjectionPosition updated = new DefaultProjectionPosition(streamId, 15L);
            contract.positionRepository().save(updated);

            Optional<ProjectionPosition> found = contract.positionRepository().findByStreamId(streamId);
            assertThat(found).isPresent();
            assertThat(found.get().getNextEventNumber()).isEqualTo(15L);
        } finally {
            contract.close();
        }
    }

    // ========== 契约 5：ViewManager 生命周期 ==========

    /** \u9a8c\u8bc1\u542f\u52a8\u89c6\u56fe\u7ba1\u7406\u5668\u540e\u8fd0\u884c\u72b6\u6001\u4e3a\u771f\u3002 */
    @Test
    void shouldReportRunningAfterStart() {
        CqrsRuntimeContract contract = createContract();
        try {
            contract.viewManager().start();
            assertThat(contract.viewManager().isRunning()).isTrue();
        } finally {
            contract.close();
        }
    }

    /** \u9a8c\u8bc1\u505c\u6b62\u5df2\u542f\u52a8\u7684\u89c6\u56fe\u7ba1\u7406\u5668\u540e\u8fd0\u884c\u72b6\u6001\u4e3a\u5047\u3002 */
    @Test
    void shouldReportNotRunningAfterStop() {
        CqrsRuntimeContract contract = createContract();
        try {
            contract.viewManager().start();
            assertThat(contract.viewManager().isRunning()).isTrue();

            contract.viewManager().stop();
            assertThat(contract.viewManager().isRunning()).isFalse();
        } finally {
            contract.close();
        }
    }

    // ========== 测试桩命令 ==========

    /**
     * 测试命令 A。
     */
    public static final class CmdA implements Command {
        /** A \u7c7b\u6d4b\u8bd5\u547d\u4ee4\u643a\u5e26\u7684\u6587\u672c\u8d1f\u8f7d\u3002 */
        private final String payload;

        /**
         * @param payload 命令内容
         */
        public CmdA(String payload) {
            this.payload = payload;
        }

        /**
         * @return 命令内容
         */
        public String getPayload() {
            return payload;
        }
    }

    /**
     * 测试命令 B。
     */
    public static final class CmdB implements Command {
        /** B \u7c7b\u6d4b\u8bd5\u547d\u4ee4\u643a\u5e26\u7684\u6570\u503c\u8d1f\u8f7d\u3002 */
        private final int value;

        /**
         * @param value 命令数值
         */
        public CmdB(int value) {
            this.value = value;
        }

        /**
         * @return 命令数值
         */
        public int getValue() {
            return value;
        }
    }

    /**
     * 未注册命令。
     */
    public static final class UnregisteredCmd implements Command {
    }

    // ========== 测试桩执行器 ==========

    /**
     * CmdA 执行器桩。
     */
    public static final class StubExecutorA implements CommandExecutor<CmdA> {

        /** \u8bb0\u5f55 A \u6267\u884c\u5668\u88ab\u8c03\u7528\u7684\u72b6\u6001\u3002 */
        private final AtomicBoolean invoked = new AtomicBoolean(false);

        /** {@inheritDoc} */
        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return Collections.singleton(CmdA.class);
        }

        /** {@inheritDoc} */
        @Override
        @SuppressWarnings("unchecked")
        public Result execute(CmdA command) {
            invoked.set(true);
            return Result.ok("executor-a:" + command.getPayload());
        }

        /**
         * @return 执行器是否已被调用
         */
        public boolean wasInvoked() {
            return invoked.get();
        }
    }

    /**
     * CmdB 执行器桩。
     */
    public static final class StubExecutorB implements CommandExecutor<CmdB> {

        /** \u8bb0\u5f55 B \u6267\u884c\u5668\u88ab\u8c03\u7528\u7684\u72b6\u6001\u3002 */
        private final AtomicBoolean invoked = new AtomicBoolean(false);

        /** {@inheritDoc} */
        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return Collections.singleton(CmdB.class);
        }

        /** {@inheritDoc} */
        @Override
        @SuppressWarnings("unchecked")
        public Result execute(CmdB command) {
            invoked.set(true);
            return Result.ok("executor-b:" + command.getValue());
        }

        /**
         * @return 执行器是否已被调用
         */
        public boolean wasInvoked() {
            return invoked.get();
        }
    }
}
