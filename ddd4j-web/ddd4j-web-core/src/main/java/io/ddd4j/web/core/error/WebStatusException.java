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

import java.io.Serializable;

/**
 * Web 适配层可移植的 HTTP 状态异常。
 */
public class WebStatusException extends RuntimeException {

    /** HTTP 状态码。 */
    private final int status;
    /** 业务错误码。 */
    private final Serializable code;
    /** 错误数据。 */
    private final Object data;

    /**
     * 创建带 HTTP 状态码的 Web 异常。
     *
     * @param status HTTP 状态码
     * @param message 错误消息
     */
    public WebStatusException(int status, String message) {
        this(status, status, message, null);
    }

    /**
     * 创建带业务错误信息的 Web 异常。
     *
     * @param status HTTP 状态码
     * @param code 业务错误码
     * @param message 错误消息
     * @param data 错误数据
     */
    public WebStatusException(int status, Serializable code, String message, Object data) {
        super(message);
        this.status = status;
        this.code = code;
        this.data = data;
    }

    /**
     * 返回HTTP 状态码。
     *
     * @return HTTP 状态码
     */

    public int getStatus() {
        return status;
    }

    /**
     * 返回错误码。
     *
     * @return 错误码
     */

    public Serializable getCode() {
        return code;
    }

    /**
     * 返回错误数据。
     *
     * @return 错误数据
     */

    public Object getData() {
        return data;
    }
}
