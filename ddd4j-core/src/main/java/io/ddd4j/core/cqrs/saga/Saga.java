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

package io.ddd4j.core.cqrs.saga;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 进程内 Saga 编排器（最小可用实现）。
 *
 * <p>对应规格：{@code complete-cqrs-es-base-capabilities/saga-orchestration}。
 *
 * <h3>编排语义</h3>
 * <ul>
 *   <li><b>前向执行</b>：步骤按注册顺序依次执行，全部成功收敛 {@link SagaState#COMPLETED}</li>
 *   <li><b>逆序补偿</b>：任一步骤失败即停止前向执行，已执行步骤的补偿按注册逆序执行，
 *       失败点之后的步骤不执行、不补偿</li>
 *   <li><b>终态收敛</b>：补偿全部成功收敛 {@link SagaState#COMPENSATED}；
 *       任一补偿失败收敛 {@link SagaState#FAILED}，且不阻断其余补偿</li>
 * </ul>
 *
 * <p>实例一次性使用：{@link #execute()} 之后的实例进入终态，重复编排请新建实例。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 1.0.x
 */
public class Saga {

    private final List<SagaStep> steps = new ArrayList<>();
    private final List<String> executedCompensations = new ArrayList<>();
    private SagaState state = SagaState.RUNNING;
    private RuntimeException failure;
    private boolean started;
    private boolean compensationFailed;

    /**
     * 注册编排步骤。
     *
     * @param step Saga 步骤
     * @return 本编排器（链式注册）
     */
    public Saga step(SagaStep step) {
        requireNotStarted();
        steps.add(Objects.requireNonNull(step, "step must not be null"));
        return this;
    }

    /**
     * 返回当前编排状态。
     *
     * @return 编排状态
     */
    public SagaState state() {
        return state;
    }

    /**
     * 返回已执行（含执行失败）的补偿步骤名称清单，按补偿执行顺序排列。
     *
     * @return 不可变补偿清单
     */
    public List<String> executedCompensations() {
        return Collections.unmodifiableList(executedCompensations);
    }

    /**
     * 返回触发补偿的前向失败异常。
     *
     * @return 前向失败异常；全部成功时为 {@code null}
     */
    public RuntimeException failure() {
        return failure;
    }

    /**
     * 执行编排：前向执行所有步骤，失败时逆序补偿并收敛终态。
     *
     * <p>本方法不向前向失败抛出——失败由 {@link #state()} 与 {@link #failure()} 表达。
     *
     * @return 终态（{@code COMPLETED}／{@code COMPENSATED}／{@code FAILED}）
     * @throws IllegalStateException 编排器已被执行过（实例一次性使用）
     */
    public synchronized SagaState execute() {
        requireNotStarted();
        started = true;
        List<SagaStep> executed = new ArrayList<>(steps.size());
        for (SagaStep step : steps) {
            try {
                step.action().run();
                executed.add(step);
            } catch (RuntimeException forwardFailure) {
                // 前向失败：停止执行，转入逆序补偿并收敛终态
                failure = forwardFailure;
                compensateInReverse(executed);
                state = compensationFailed ? SagaState.FAILED : SagaState.COMPENSATED;
                return state;
            }
        }
        state = SagaState.COMPLETED;
        return state;
    }

    /**
     * 按注册逆序执行已执行步骤的补偿；单个补偿失败被捕获记录，不阻断其余补偿。
     *
     * @param executed 已成功执行前向动作的步骤（按执行顺序）
     */
    private void compensateInReverse(List<SagaStep> executed) {
        compensationFailed = false;
        for (int i = executed.size() - 1; i >= 0; i--) {
            SagaStep step = executed.get(i);
            executedCompensations.add(step.name());
            try {
                step.compensation().run();
            } catch (RuntimeException compensationFailure) {
                // 补偿失败不阻断收敛：记录并继续其余补偿
                compensationFailed = true;
            }
        }
    }

    private void requireNotStarted() {
        if (started) {
            throw new IllegalStateException("Saga instance is one-shot; create a new Saga to execute again");
        }
    }
}
