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

    private final int status;
    private final Serializable code;
    private final Object data;

    public WebStatusException(int status, String message) {
        this(status, status, message, null);
    }

    public WebStatusException(int status, Serializable code, String message, Object data) {
        super(message);
        this.status = status;
        this.code = code;
        this.data = data;
    }

    public int getStatus() {
        return status;
    }

    public Serializable getCode() {
        return code;
    }

    public Object getData() {
        return data;
    }
}
