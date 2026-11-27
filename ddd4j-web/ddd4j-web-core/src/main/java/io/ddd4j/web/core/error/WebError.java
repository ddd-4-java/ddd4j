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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.ddd4j.core.api.R;

import java.io.Serializable;
import java.util.Objects;

/**
 * HTTP 状态与 ddd4j 响应体之间的统一错误表示。
 */
public final class WebError {

    /**
     * Java 序列化版本标识。
     */
    private static final long serialVersionUID = 0L;

    /**
     * 对应 HTTP 错误响应的状态码。
     */
    private final int status;

    /**
     * 响应体中报告的业务错误码。
     */
    private final Serializable code;

    /**
     * 响应体中供调用方读取的错误说明。
     */
    private final String message;

    /**
     * 错误响应中随状态返回的可选业务数据。
     */
    private final Object data;

    /**
     * 创建 Web 错误值对象。
     *
     * @param status  HTTP 状态码
     * @param code    业务错误码
     * @param message 错误消息
     * @param data    错误数据
     */
    @JsonCreator()
    public WebError(@JsonProperty("status") int status, @JsonProperty("code") Serializable code, @JsonProperty("message") String message, @JsonProperty("data") Object data) {
        this.status = status;
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 将错误转换为统一响应对象。
     *
     * @return 统一错误响应
     */
    public R<Object> toResponse() {
        return R.of(code, message, data);
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

    /**
     * 返回错误消息。
     *
     * @return 错误消息
     */

    public String getMessage() {
        return message;
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
     * 返回HTTP 状态码。
     *
     * @return HTTP 状态码
     */
    @JsonProperty("status")
    public int status() {
        return status;
    }

    /**
     * 返回错误码。
     *
     * @return 错误码
     */
    @JsonProperty("code")
    public Serializable code() {
        return code;
    }

    /**
     * 返回错误消息。
     *
     * @return 错误消息
     */
    @JsonProperty("message")
    public String message() {
        return message;
    }

    /**
     * 返回错误数据。
     *
     * @return 错误数据
     */
    @JsonProperty("data")
    public Object data() {
        return data;
    }

    /**
     * 根据<code>code</code>、<code>message</code>、<code>data</code>、<code>status</code>比较对象的值是否相等。
     *
     * <p>只比较运行时类型相同的对象。</p>
     *
     * @param obj 用来比较或转换的输入对象
     * @return 对象满足此实现的相等条件时为真
     */
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

    /**
     * 计算与对象相等条件一致的哈希值。
     *
     * @return 由参与相等比较的状态计算出的哈希值
     */
    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Integer.hashCode(status);
        result = 31 * result + Objects.hashCode(code);
        result = 31 * result + Objects.hashCode(message);
        result = 31 * result + Objects.hashCode(data);
        return result;
    }

    /**
     * 生成用于调试和日志的对象描述，包含<code>status</code>、<code>code</code>、<code>message</code>、<code>data</code>。
     *
     * @return 表示当前对象状态的字符串
     */
    @Override
    public String toString() {
        return "WebError[status=" + status + ", code=" + code + ", message=" + message + ", data=" + data + "]";
    }
}
