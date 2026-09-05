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

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Web 契约测试使用的最小响应快照。
 */
public final class WebContractResponse {
    private final int status;
    private final Map<String, List<String>> headers;
    private final String body;

/**
 * Web 契约测试使用的最小响应快照。
 */

    public WebContractResponse(int status, Map<String, List<String>> headers, String body) {
        this.status = status;
        this.headers = headers;
        this.body = body;
    }

    public int status() { return status; }
    public Map<String, List<String>> headers() { return headers; }
    public String body() { return body; }

    public Optional<String> firstHeader(String name) {
        Objects.requireNonNull(name, "name must not be null");
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .filter(values -> Objects.nonNull(values) && !values.isEmpty())
                .map(values -> values.get(0))
                .findFirst();
    }

    @Override public boolean equals(Object o) {
        return this == o || (o instanceof WebContractResponse
                && status == ((WebContractResponse)o).status
                && Objects.equals(headers, ((WebContractResponse)o).headers)
                && Objects.equals(body, ((WebContractResponse)o).body));
    }
    @Override public int hashCode() { return Objects.hash(status, headers, body); }
    @Override public String toString() { return "WebContractResponse{status=" + status + "}"; }

    public int getStatus() {
        return status;
    }

    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    public String getBody() {
        return body;
    }
}
