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
package io.ddd4j.sample.javalin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/** JDK 8 compatible HTTP client used by the Javalin integration tests. */
public final class TestHttpClient {

    private static final int TIMEOUT_MILLIS = 5000;

    public HttpResponse<String> get(String url, Map<String, String> headers) throws IOException {
        return execute("GET", url, null, headers);
    }

    public HttpResponse<String> postJson(String url, String body, Map<String, String> headers) throws IOException {
        return execute("POST", url, body, headers);
    }

    public HttpResponse<String> putJson(String url, String body, Map<String, String> headers) throws IOException {
        return execute("PUT", url, body, headers);
    }

    public HttpResponse<String> put(String url, Map<String, String> headers) throws IOException {
        return execute("PUT", url, null, headers);
    }

    public HttpResponse<String> delete(String url, Map<String, String> headers) throws IOException {
        return execute("DELETE", url, null, headers);
    }

    private HttpResponse<String> execute(String method, String url, String body,
                                         Map<String, String> headers) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(TIMEOUT_MILLIS);
        connection.setReadTimeout(TIMEOUT_MILLIS);
        for (Map.Entry<String, String> header : headers.entrySet()) {
            connection.setRequestProperty(header.getKey(), header.getValue());
        }
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
        String responseBody = read(input);
        return new HttpResponse<>(statusCode, responseBody, connection);
    }

    private static String read(InputStream input) throws IOException {
        if (Objects.isNull(input)) {
            return "";
        }
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while (Objects.nonNull(line = reader.readLine())) {
                body.append(line);
            }
        }
        return body.toString();
    }

    public static final class HttpResponse<T> {

        private final int statusCode;
        private final T body;
        private final HttpURLConnection connection;

        private HttpResponse(int statusCode, T body, HttpURLConnection connection) {
            this.statusCode = statusCode;
            this.body = body;
            this.connection = connection;
        }

        public int statusCode() {
            return statusCode;
        }

        public T body() {
            return body;
        }

        public String header(String name) {
            return connection.getHeaderField(name);
        }
    }

    public static Map<String, String> noHeaders() {
        return Collections.emptyMap();
    }
}
