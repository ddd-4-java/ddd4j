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

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

import io.ddd4j.core.api.R;

import java.io.Serializable;

/**
 * HTTP 状态与 ddd4j 响应体之间的统一错误表示。
 */
public final class WebError {

    private static final long serialVersionUID = 0L;

    private final int status;

    private final Serializable code;

    private final String message;

    private final Object data;

    public R<Object> toResponse() {
        return R.of(code, message, data);
    }

    public Serializable getCode() {
        return code;
    }

    public Object getData() {
        return data;
    }

    public String getMessage() {
        return message;
    }

    public int getStatus() {
        return status;
    }

    @JsonCreator()
    public WebError(@JsonProperty("status") int status, @JsonProperty("code") Serializable code, @JsonProperty("message") String message, @JsonProperty("data") Object data) {
        this.status = status;
        this.code = code;
        this.message = message;
        this.data = data;
    }

    @JsonProperty("status")
    public int status() {
        return status;
    }

    @JsonProperty("code")
    public Serializable code() {
        return code;
    }

    @JsonProperty("message")
    public String message() {
        return message;
    }

    @JsonProperty("data")
    public Object data() {
        return data;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (Objects.isNull(obj) || getClass() != obj.getClass()) {
            return false;
        }
        WebError other = (WebError) obj;
        return this.status == other.status && Objects.equals(this.code, other.code) && Objects.equals(this.message, other.message) && Objects.equals(this.data, other.data);
    }

    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Integer.hashCode(status);
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
