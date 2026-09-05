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
package io.ddd4j.mq.delivery;

/**
 * 默认的无操作可靠消息观察器。
 *
 * <p>用于未接入可观测性扩展的场景，保证可靠消息流程不需要额外运行时依赖。
 */
public enum NoopMQDeliveryObserver implements MQDeliveryObserver {

    /** 单例实例。 */
    INSTANCE
}
