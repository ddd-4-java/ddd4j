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
 * 在多人登录同一账号并发生挤占时，决定由哪一端放弃会话。
 *
 * <p>对应 Sa-Token 的 {@code SaReplacedLoginExitMode}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public enum AuthReplacedLoginExitMode {

    /**
     * 新登录设备挤掉旧登录设备（默认）。
     */
    NEW_DEVICE,

    /**
     * 旧登录设备保留，新登录失败。
     */
    OLD_DEVICE
}