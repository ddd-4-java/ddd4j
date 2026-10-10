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
package io.ddd4j.web.core.error;

import io.ddd4j.core.api.R;

import java.io.Serializable;

/**
 * HTTP 状态与 ddd4j 响应体之间的统一错误表示。
 */
public record WebError(int status, Serializable code, String message, Object data) {

    public R<Object> toResponse() {
        return R.fail(code, message, data);
    }

    /**
     * 获取值。
     * @return 方法执行结果。
     */
    public Serializable getCode() {
            return code;
        }

    /**
     * 获取值。
     * @return 结果对象。
     */
    public Object getData() {
            return data;
        }

    /**
     * 获取值。
     * @return 结果字符串。
     */
    public String getMessage() {
            return message;
        }

    /**
     * 获取值。
     * @return 结果数值。
     */
    public int getStatus() {
            return status;
        }
}
