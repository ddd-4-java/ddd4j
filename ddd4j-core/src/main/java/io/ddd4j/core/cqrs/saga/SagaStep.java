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

import java.util.Objects;

/**
 * Saga 单个步骤：业务动作与对应的补偿动作。
 *
 * <p>对应规格：{@code complete-cqrs-es-base-capabilities/saga-orchestration}。
 * 步骤按注册顺序前向执行；失败时已执行步骤的补偿按注册逆序执行。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 1.0.x
 */
public final class SagaStep {

    private final String name;
    private final Runnable action;
    private final Runnable compensation;

    /**
     * 创建 Saga 步骤。
     *
     * @param name         步骤名称（用于补偿执行清单追踪）
     * @param action       业务动作
     * @param compensation 补偿动作（用于撤销已成功的业务动作）
     */
    public SagaStep(String name, Runnable action, Runnable compensation) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.action = Objects.requireNonNull(action, "action must not be null");
        this.compensation = Objects.requireNonNull(compensation, "compensation must not be null");
    }

    /**
     * 返回步骤名称。
     *
     * @return 步骤名称
     */
    public String name() {
        return name;
    }

    /**
     * 返回业务动作。
     *
     * @return 业务动作
     */
    public Runnable action() {
        return action;
    }

    /**
     * 返回补偿动作。
     *
     * @return 补偿动作
     */
    public Runnable compensation() {
        return compensation;
    }
}
