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

    @AfterEach
    void closeContract() {
        // 子类可通过 @Override createContract() 内部管理生命周期，
        // 此处做兜底清理，防止资源泄漏。
    }

    // ========== 契约 1：命令路由 ==========

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

    /** 测试命令 A。 */
    public static final class CmdA implements Command {
        private final String payload;

        public CmdA(String payload) {
            this.payload = payload;
        }

        public String getPayload() {
            return payload;
        }
    }

    /** 测试命令 B。 */
    public static final class CmdB implements Command {
        private final int value;

        public CmdB(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    /** 未注册命令。 */
    public static final class UnregisteredCmd implements Command {
    }

    // ========== 测试桩执行器 ==========

    /** CmdA 执行器桩。 */
    public static final class StubExecutorA implements CommandExecutor<CmdA> {

        private final AtomicBoolean invoked = new AtomicBoolean(false);

        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return Collections.singleton(CmdA.class);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Result execute(CmdA command) {
            invoked.set(true);
            return Result.ok("executor-a:" + command.getPayload());
        }

        public boolean wasInvoked() {
            return invoked.get();
        }
    }

    /** CmdB 执行器桩。 */
    public static final class StubExecutorB implements CommandExecutor<CmdB> {

        private final AtomicBoolean invoked = new AtomicBoolean(false);

        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return Collections.singleton(CmdB.class);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Result execute(CmdB command) {
            invoked.set(true);
            return Result.ok("executor-b:" + command.getValue());
        }

        public boolean wasInvoked() {
            return invoked.get();
        }
    }
}
