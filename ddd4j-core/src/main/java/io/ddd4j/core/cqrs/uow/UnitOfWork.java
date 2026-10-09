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

package io.ddd4j.core.cqrs.uow;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 工作单元事务边界（最小可用实现）。
 *
 * <p>对应规格：{@code complete-cqrs-es-base-capabilities/unit-of-work}。
 *
 * <h3>边界语义</h3>
 * <ul>
 *   <li><b>提交生效</b>：{@link #commit()} 执行已注册的提交回调，状态到达 {@code COMMITTED}</li>
 *   <li><b>失败回滚 + 异常传播</b>：{@link #execute(Supplier)} 模板内工作抛出异常时执行回滚
 *       （回滚回调生效），并将<b>原始异常原样传播</b>给调用方</li>
 *   <li><b>终态幂等保护</b>：终态之后重复 {@code commit}/{@code rollback} 抛
 *       {@link IllegalStateException}，回调不重复触发</li>
 * </ul>
 *
 * <p>本类只承载边界与回调语义，不绑定具体事务资源（JPA/JDBC 资源登记交运行时容器适配）。
 * 实例一次性使用，实现 {@link AutoCloseable} 以支持 try-with-resources（未提交边界自动回滚）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 1.0.x
 */
@Slf4j
public final class UnitOfWork implements AutoCloseable {

    private final List<Runnable> commitActions = new ArrayList<>();
    private final List<Runnable> rollbackActions = new ArrayList<>();
    private State state = State.ACTIVE;

    private UnitOfWork() {
    }

    /**
     * 开启一个新的活动工作单元边界。
     *
     * @return 新的工作单元
     */
    public static UnitOfWork begin() {
        return new UnitOfWork();
    }

    /**
     * 返回当前状态。
     *
     * @return 当前状态
     */
    public State state() {
        return state;
    }

    /**
     * 注册提交回调（提交成功时执行一次）。
     *
     * @param action 提交回调
     */
    public void registerCommit(Runnable action) {
        commitActions.add(Objects.requireNonNull(action, "action must not be null"));
    }

    /**
     * 注册回滚回调（回滚时执行一次）。
     *
     * @param action 回滚回调
     */
    public void registerRollback(Runnable action) {
        rollbackActions.add(Objects.requireNonNull(action, "action must not be null"));
    }

    /**
     * 提交边界：执行全部提交回调，状态到达 {@code COMMITTED}。
     *
     * @throws IllegalStateException 边界非 {@code ACTIVE}（终态不可重复提交）
     */
    public synchronized void commit() {
        requireActive();
        state = State.COMMITTED;
        runActions(commitActions);
    }

    /**
     * 回滚边界：执行全部回滚回调，状态到达 {@code ROLLED_BACK}。
     *
     * @throws IllegalStateException 边界非 {@code ACTIVE}（终态不可重复回滚）
     */
    public synchronized void rollback() {
        requireActive();
        state = State.ROLLED_BACK;
        runActions(rollbackActions);
    }

    /**
     * 执行工作模板：成功提交并返回结果；失败回滚并将原始异常原样传播。
     *
     * @param work 边界内工作
     * @param <T>  结果类型
     * @return 工作结果
     * @throws RuntimeException      工作抛出的原始异常（原样传播，不替换不吞掉）
     * @throws IllegalStateException 边界非 {@code ACTIVE}
     */
    public <T> T execute(Supplier<T> work) {
        Objects.requireNonNull(work, "work must not be null");
        requireActive();
        try {
            T result = work.get();
            commit();
            return result;
        } catch (RuntimeException failure) {
            rollbackFor(failure);
            throw failure;
        } catch (Error failure) {
            rollbackFor(failure);
            throw failure;
        }
    }

    /**
     * 边界卫生：try-with-resources 关闭时若仍处于活动状态则自动回滚。
     */
    @Override
    public synchronized void close() {
        if (state == State.ACTIVE) {
            rollback();
        }
    }

    /**
     * 失败路径回滚：回滚回调异常被记录降级，确保原始失败异常不被吞掉。
     *
     * @param failure 触发回滚的原始失败
     */
    private void rollbackFor(Throwable failure) {
        try {
            if (state == State.ACTIVE) {
                state = State.ROLLED_BACK;
                runActions(rollbackActions);
            }
        } catch (RuntimeException rollbackFailure) {
            log.warn("UnitOfWork rollback callback failed; original failure [{}] is rethrown as-is",
                    failure, rollbackFailure);
        }
    }

    private void runActions(List<Runnable> actions) {
        for (Runnable action : actions) {
            try {
                action.run();
            } catch (RuntimeException callbackFailure) {
                // 单个回调失败不阻断其余回调
                log.warn("UnitOfWork callback failed (continuing with remaining callbacks)", callbackFailure);
            }
        }
    }

    private void requireActive() {
        if (state != State.ACTIVE) {
            throw new IllegalStateException("UnitOfWork is already terminated: " + state);
        }
    }

    /**
     * 工作单元状态机：{@code ACTIVE} → {@code COMMITTED}／{@code ROLLED_BACK}（终态）。
     */
    public enum State {
        /**
         * 活动边界（可提交／可回滚）。
         */
        ACTIVE,
        /**
         * 终态：已提交，提交回调已生效。
         */
        COMMITTED,
        /**
         * 终态：已回滚，回滚回调已生效。
         */
        ROLLED_BACK
    }
}
