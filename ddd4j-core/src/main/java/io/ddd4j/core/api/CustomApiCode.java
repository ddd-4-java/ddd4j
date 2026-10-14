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
package io.ddd4j.core.api;

/**
 * 自定义 API 错误码接口（业务扩展点）。
 * <p>
 * 专门预留给下游业务使用：业务方可通过实现此接口定义自己的业务错误码枚举，
 * 与 {@link ApiCode} 标准错误码共同构成统一的错误码体系。
 * <p>
 * 实现方只需提供两个属性：
 * <ul>
 *   <li>{@code code} — 数字错误码</li>
 *   <li>{@code reason} — 错误原因描述</li>
 * </ul>
 * 即默认获得全部集成能力：
 * <ul>
 *   <li>{@code toResponse()} 系列 — 直接构建统一响应 {@link R}</li>
 *   <li>可直接传入 BizRuntimeException 等核心异常的构造器</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface CustomApiCode {

    /**
     * 获取错误码。
     *
     * @return 数字错误码
     */
    Integer getCode();

    /**
     * 获取错误原因描述。
     *
     * @return 错误原因
     */
    String getReason();

    /**
     * 构建统一响应（code/reason 取自定义码）。
     */
    default <T> R<T> toResponse() {
        return R.of(this);
    }

    /**
     * 构建统一响应，覆盖消息。
     */
    default <T> R<T> toResponse(String message) {
        return new R<>(getCode(), message, null);
    }

    /**
     * 构建统一响应，携带数据。
     */
    default <T> R<T> toResponse(T data) {
        return R.of(this, data);
    }

    /**
     * 构建统一响应，覆盖消息并携带数据。
     */
    default <T> R<T> toResponse(String message, T data) {
        return new R<>(getCode(), message, data);
    }

}
