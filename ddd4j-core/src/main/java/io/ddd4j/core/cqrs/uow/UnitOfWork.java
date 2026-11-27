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
 * <h2>边界语义</h2>
 * <ul>
 *   <li><b>提交生效</b>：{@link #commit()} 执行已注册的提交回调，状态到达 {@code COMMITTED}</li>
 *   <li><b>失败回滚 + 异常传播</b>：{@link #execute(Supplier)} 模板内工作抛出异常时执行回滚
 *       （回滚回调生效），并将<b>原始异常原样传播</b>给调用方</li>
 *   <li><b>终态幂等保护</b>：终态之后重复 {@code commit}/{@code rollback} 抛
 *       {@link IllegalStateException}，回调不重复触发</li>
 * </ul>
 *
 * <p>本类只承载边界与回调语义，不绑定具体事务资源（JPA/JDBC 资源登记交运行时容器适配）。
 * 实例一次性使用；每次 {@link #begin()} 返回独立边界，嵌套调用不会自动共享或联动状态。
 * 实现 {@link AutoCloseable} 以支持 try-with-resources（未提交边界自动回滚）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 1.0.x
 */
@Slf4j
public final class UnitOfWork implements AutoCloseable {

    /** \u5de5\u4f5c\u5355\u5143\u63d0\u4ea4\u65f6\u6309\u6ce8\u518c\u987a\u5e8f\u6267\u884c\u7684\u56de\u8c03\u3002 */
    private final List<Runnable> commitActions = new ArrayList<>();
    /** \u5de5\u4f5c\u5355\u5143\u56de\u6eda\u65f6\u6267\u884c\u7684\u56de\u8c03\u3002 */
    private final List<Runnable> rollbackActions = new ArrayList<>();
    /** \u5f53\u524d\u5de5\u4f5c\u5355\u5143\u6240\u5904\u7684\u4e8b\u52a1\u9636\u6bb5\u3002 */
    private State state = State.ACTIVE;

    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>UnitOfWork</code> \u5b9e\u4f8b\u3002 */
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
     * 失败路径回滚：回滚回调失败被记录降级，确保原始失败原样传播。
     *
     * @param failure 触发回滚的原始失败
     */
    private void rollbackFor(Throwable failure) {
        if (state == State.ACTIVE) {
            state = State.ROLLED_BACK;
            for (Runnable action : rollbackActions) {
                try {
                    action.run();
                } catch (RuntimeException | Error rollbackFailure) {
                    log.warn("UnitOfWork rollback callback failed; original failure [{}] is rethrown as-is",
                            failure, rollbackFailure);
                }
            }
        }
    }

    /**
     * \u6309\u5217\u8868\u987a\u5e8f\u8fd0\u884c\u56de\u8c03\u5e76\u8bb0\u5f55\u5931\u8d25\uff0c\u540e\u7eed\u56de\u8c03\u4ecd\u7ee7\u7eed\u6267\u884c\u3002
     * @param actions \u5df2\u767b\u8bb0\u7684\u4e8b\u52a1\u5b8c\u6210\u56de\u8c03\u5217\u8868
     */
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

    /**
     * \u53ea\u5141\u8bb8\u5c1a\u672a\u5b8c\u6210\u7684\u5de5\u4f5c\u5355\u5143\u767b\u8bb0\u6216\u7ec8\u7ed3\u64cd\u4f5c\u3002
     * @throws IllegalStateException \u5de5\u4f5c\u5355\u5143\u5df2\u7ecf\u7ec8\u6b62
     */
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
