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
package io.ddd4j.web.core.version;

import io.ddd4j.web.core.error.WebStatusException;

/**
 * 严格模式下的 API 版本拒绝信号：携带 4xx 状态的可移植 Web 异常。
 *
 * <p>由 {@link ApiVersionResolver} 在 {@link ApiVersionMode#STRICT} 遇到非法版本值时抛出，
 * 经既有异常翻译器（{@code DefaultWebExceptionTranslator}）翻译为 4xx 响应，
 * 错误信息中指明被拒绝的非法版本值。
 *
 * @see ApiVersionMode#STRICT
 */
public class ApiVersionRejectedException extends WebStatusException {

    /** 客户端错误状态码（400 Bad Request）。 */
    public static final int STATUS = 400;

    /**
     * 以错误描述创建拒绝异常，状态码固定为 {@link #STATUS}。
     *
     * @param message 错误信息，须指明非法版本值
     */
    public ApiVersionRejectedException(String message) {
        super(STATUS, message);
    }
}
