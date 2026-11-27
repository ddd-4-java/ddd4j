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
package io.ddd4j.web.testkit;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Web 契约测试使用的最小响应快照。
 */
public final class WebContractResponse {

    /** Java \u5e8f\u5217\u5316\u7248\u672c\u6807\u8bc6\u3002 */
    private static final long serialVersionUID = 0L;

    /** \u8981\u5199\u5165 HTTP \u54cd\u5e94\u7684\u72b6\u6001\u7801\u3002 */
    private final int status;

    /** HTTP \u5408\u540c\u8bb0\u5f55\u7684\u54cd\u5e94\u5934\u6620\u5c04\u3002 */
    private final Map<String, List<String>> headers;

    /** HTTP \u5408\u540c\u8bb0\u5f55\u7684\u54cd\u5e94\u6b63\u6587\u3002 */
    private final String body;

    /**
     * 按名称查询首个响应头值，忽略名称大小写。
     *
     * @param name 响应头名称
     * @return 首个值；不存在时为空
     */
    public Optional<String> firstHeader(String name) {
        Objects.requireNonNull(name, "name must not be null");
        return headers.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(name)).map(Map.Entry::getValue).filter(values -> Objects.nonNull(values) && !values.isEmpty()).map(values -> values.get(0)).findFirst();
    }

    /** @return 响应体 */
    public String getBody() {
        return body;
    }

    /** @return 响应头 */
    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    /** @return HTTP 状态码 */
    public int getStatus() {
        return status;
    }

    /**
     * 创建 Web 契约响应快照。
     *
     * @param status HTTP 状态码
     * @param headers 响应头
     * @param body 响应体
     */
    @JsonCreator()
    public WebContractResponse(@JsonProperty("status") int status, @JsonProperty("headers") Map<String, List<String>> headers, @JsonProperty("body") String body) {
        this.status = status;
        this.headers = headers;
        this.body = body;
    }

    /** @return HTTP 状态码 */
    @JsonProperty("status")
    public int status() {
        return status;
    }

    /** @return 响应头 */
    @JsonProperty("headers")
    public Map<String, List<String>> headers() {
        return headers;
    }

    /** @return 响应体 */
    @JsonProperty("body")
    public String body() {
        return body;
    }

    /**
     * 根据<code>headers</code>、<code>body</code>、<code>status</code>比较对象的值是否相等。
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
        WebContractResponse other = (WebContractResponse) obj;
        return this.status == other.status && Objects.equals(this.headers, other.headers) && Objects.equals(this.body, other.body);
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
        result = 31 * result + Objects.hashCode(headers);
        result = 31 * result + Objects.hashCode(body);
        return result;
    }

    /**
     * 生成用于调试和日志的对象描述，包含<code>status</code>、<code>headers</code>、<code>body</code>。
     *
     * @return 表示当前对象状态的字符串
     */
    @Override
    public String toString() {
        return "WebContractResponse[status=" + status + ", headers=" + headers + ", body=" + body + "]";
    }
}
