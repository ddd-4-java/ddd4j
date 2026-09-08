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
package io.ddd4j.sample.javalin.cqrs;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** JDK 8 HTTP client for CQRS sample integration tests. */
public final class TestHttpClient {

    public HttpResponse<String> get(String url) throws IOException {
        return execute("GET", url, null);
    }

    public HttpResponse<String> postJson(String url, String body) throws IOException {
        return execute("POST", url, body);
    }

    public HttpResponse<String> putJson(String url, String body) throws IOException {
        return execute("PUT", url, body);
    }

    public HttpResponse<String> put(String url) throws IOException {
        return execute("PUT", url, null);
    }

    public HttpResponse<String> delete(String url) throws IOException {
        return execute("DELETE", url, null);
    }

    private HttpResponse<String> execute(String method, String url, String body) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod(method);
        // 每个用例重启随机端口服务器，测试请求不跨服务器生命周期复用连接。
        connection.setRequestProperty("Connection", "close");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        if (Objects.nonNull(body)) {
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setFixedLengthStreamingMode(payload.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(payload);
            }
        }
        int statusCode = connection.getResponseCode();
        InputStream input = statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
        StringBuilder responseBody = new StringBuilder();
        if (Objects.nonNull(input)) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while (Objects.nonNull(line = reader.readLine())) {
                    responseBody.append(line);
                }
            }
        }
        return new HttpResponse<>(statusCode, responseBody.toString());
    }

    public static final class HttpResponse<T> {
        private final int statusCode;
        private final T body;

        private HttpResponse(int statusCode, T body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        public int statusCode() { return statusCode; }
        public T body() { return body; }
    }
}
