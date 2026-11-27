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
     * \u6784\u5efa\u7edf\u4e00\u54cd\u5e94\uff08code/reason \u53d6\u81ea\u5b9a\u4e49\u7801\uff09\u3002
     *
     * @param <T> \u54cd\u5e94\u6570\u636e\u7c7b\u578b
     * @return \u4f7f\u7528\u5f53\u524d\u9519\u8bef\u7801\u4e0e\u539f\u56e0\u7684\u54cd\u5e94
     */
    default <T> R<T> toResponse() {
        return R.of(this);
    }

    /**
     * \u6784\u5efa\u7edf\u4e00\u54cd\u5e94\uff0c\u8986\u76d6\u6d88\u606f\u3002
     *
     * @param <T> \u54cd\u5e94\u6570\u636e\u7c7b\u578b
     * @param message \u81ea\u5b9a\u4e49\u54cd\u5e94\u6d88\u606f
     * @return \u4f7f\u7528\u81ea\u5b9a\u4e49\u6d88\u606f\u7684\u54cd\u5e94
     */
    default <T> R<T> toResponse(String message) {
        return new R<>(getCode(), message, null);
    }

    /**
     * \u6784\u5efa\u7edf\u4e00\u54cd\u5e94\uff0c\u643a\u5e26\u6570\u636e\u3002
     *
     * @param <T> \u54cd\u5e94\u6570\u636e\u7c7b\u578b
     * @param data \u54cd\u5e94\u6570\u636e
     * @return \u643a\u5e26\u6570\u636e\u7684\u54cd\u5e94
     */
    default <T> R<T> toResponse(T data) {
        return R.of(this, data);
    }

    /**
     * \u6784\u5efa\u7edf\u4e00\u54cd\u5e94\uff0c\u8986\u76d6\u6d88\u606f\u5e76\u643a\u5e26\u6570\u636e\u3002
     *
     * @param <T> \u54cd\u5e94\u6570\u636e\u7c7b\u578b
     * @param message \u81ea\u5b9a\u4e49\u54cd\u5e94\u6d88\u606f
     * @param data \u54cd\u5e94\u6570\u636e
     * @return \u643a\u5e26\u6d88\u606f\u548c\u6570\u636e\u7684\u54cd\u5e94
     */
    default <T> R<T> toResponse(String message, T data) {
        return new R<>(getCode(), message, data);
    }

}
