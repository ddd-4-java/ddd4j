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
package io.ddd4j.web.core.context;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

/**
 * 可由运行时事件总线观测的框架无关 HTTP 请求失败事件。
 */
public final class WebRequestFailure {

    private static final long serialVersionUID = 0L;

    private final String method;

    private final String path;

    private final Throwable cause;

    /**
     * 返回异常原因。
     *
     * @return 异常原因
     */

    public Throwable getCause() {
        return cause;
    }

    /**
     * 返回HTTP 方法。
     *
     * @return HTTP 方法
     */

    public String getMethod() {
        return method;
    }

    /**
     * 返回请求路径。
     *
     * @return 请求路径
     */

    public String getPath() {
        return path;
    }

    /**
     * 创建请求失败记录。
     *
     * @param method HTTP 方法
     * @param path 请求路径
     * @param cause 原始异常
     */
    @JsonCreator()
    public WebRequestFailure(@JsonProperty("method") String method, @JsonProperty("path") String path, @JsonProperty("cause") Throwable cause) {
        this.method = method;
        this.path = path;
        this.cause = cause;
    }

    /**
     * 返回HTTP 方法。
     *
     * @return HTTP 方法
     */
    @JsonProperty("method")
    public String method() {
        return method;
    }

    /**
     * 返回请求路径。
     *
     * @return 请求路径
     */
    @JsonProperty("path")
    public String path() {
        return path;
    }

    /**
     * 返回异常原因。
     *
     * @return 异常原因
     */
    @JsonProperty("cause")
    public Throwable cause() {
        return cause;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (Objects.isNull(obj) || getClass() != obj.getClass()) {
            return false;
        }
        WebRequestFailure other = (WebRequestFailure) obj;
        return Objects.equals(this.method, other.method) && Objects.equals(this.path, other.path) && Objects.equals(this.cause, other.cause);
    }

    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Objects.hashCode(method);
        result = 31 * result + Objects.hashCode(path);
        result = 31 * result + Objects.hashCode(cause);
        return result;
    }

    @Override
    public String toString() {
        return "WebRequestFailure[method=" + method + ", path=" + path + ", cause=" + cause + "]";
    }
}
