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

package io.ddd4j.core.cqrs.readmodel;

/**
 * 空实现的投影指标（no-op 单例）。
 *
 * <p>所有回调方法均为空操作，适用于不需要指标采集的场景。
 * 通过 {@link #INSTANCE} 获取全局单例。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */
public final class NoopProjectionMetrics implements ProjectionMetrics {
    public static final NoopProjectionMetrics INSTANCE = new NoopProjectionMetrics();
    private NoopProjectionMetrics() { }
}
