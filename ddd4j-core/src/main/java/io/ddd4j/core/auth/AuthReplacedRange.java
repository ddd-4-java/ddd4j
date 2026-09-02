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
package io.ddd4j.core.auth;

/**
 * 当挤占发生时，被挤占会话的范围。
 *
 * <p>对应 Sa-Token 的 {@code SaReplacedRange}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public enum AuthReplacedRange {

    /**
     * 仅当前设备类型被挤占下线（默认）。
     */
    CURRENT_DEVICE_TYPE,

    /**
     * 所有设备类型均被挤占下线。
     */
    ALL_DEVICE_TYPE
}