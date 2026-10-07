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

/**
 * Saga 编排状态机。
 *
 * <p>对应规格：{@code complete-cqrs-es-base-capabilities/saga-orchestration}。
 * 部分失败后 MUST 收敛到终态（{@link #COMPLETED}／{@link #COMPENSATED}／{@link #FAILED}），
 * 不允许残留运行态。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 1.0.x
 */
public enum SagaState {

    /**
     * 运行中（尚未执行或正在执行）。
     */
    RUNNING,

    /**
     * 终态：全部步骤前向执行成功，未触发任何补偿。
     */
    COMPLETED,

    /**
     * 终态：某步骤前向失败，已执行步骤的补偿全部成功。
     */
    COMPENSATED,

    /**
     * 终态：某步骤前向失败，且至少一个补偿自身也失败
     * （补偿失败不阻断其余补偿，最终仍以此终态收敛）。
     */
    FAILED
}
