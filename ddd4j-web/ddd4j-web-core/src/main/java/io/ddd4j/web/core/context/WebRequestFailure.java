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

import java.util.Objects;
/**
 * 可由运行时事件总线观测的框架无关 HTTP 请求失败事件。
 */public final class WebRequestFailure {

    private final String method;
    private final String path;
    private final Throwable cause;

/**
 * 可由运行时事件总线观测的框架无关 HTTP 请求失败事件。
 */

    public WebRequestFailure(String method, String path, Throwable cause) {
        this.method = method;
        this.path = path;
        this.cause = cause;
    }

    public String method() { return method; }
    public String path() { return path; }
    public Throwable cause() { return cause; }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public Throwable getCause() {
        return cause;
    }
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WebRequestFailure)) {
            return false;
        }
        WebRequestFailure that = (WebRequestFailure) o;
        return Objects.equals(method, that.method)
                && Objects.equals(path, that.path)
                && Objects.equals(cause, that.cause);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(method);
        result = 31 * result + Objects.hashCode(path);
        result = 31 * result + Objects.hashCode(cause);
        return result;
    }

    @Override
    public String toString() {
        return "WebRequestFailure[method=" + method + ", path=" + path + ", cause=" + cause + "]";
    }
}
