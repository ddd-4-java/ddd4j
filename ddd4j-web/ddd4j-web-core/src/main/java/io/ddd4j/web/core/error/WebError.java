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

import java.util.Objects;
import io.ddd4j.core.api.R;

import java.io.Serializable;

/**
 * HTTP 状态与 ddd4j 响应体之间的统一错误表示。
 */public final class WebError {

    private final int status;
    private final Serializable code;
    private final String message;
    private final Object data;

/**
 * HTTP 状态与 ddd4j 响应体之间的统一错误表示。
 */

    public WebError(int status, Serializable code, String message, Object data) {
        this.status = status;
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public R<Object> toResponse() {
        return R.fail(code, message, data);
    }

    public int status() { return status; }
    public Serializable code() { return code; }
    public String message() { return message; }
    public Object data() { return data; }

    public int getStatus() {
        return status;
    }

    public Serializable getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public Object getData() {
        return data;
    }
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WebError)) {
            return false;
        }
        WebError that = (WebError) o;
        return status == that.status
                && Objects.equals(code, that.code)
                && Objects.equals(message, that.message)
                && Objects.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        int result = status;
        result = 31 * result + Objects.hashCode(code);
        result = 31 * result + Objects.hashCode(message);
        result = 31 * result + Objects.hashCode(data);
        return result;
    }

    @Override
    public String toString() {
        return "WebError[status=" + status + ", code=" + code + ", message=" + message + ", data=" + data + "]";
    }
}
