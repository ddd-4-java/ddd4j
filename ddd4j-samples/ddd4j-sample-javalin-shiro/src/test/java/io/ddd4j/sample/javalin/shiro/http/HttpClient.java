/* Copyright (c) 2024-2026 ddd4j project. Licensed under the Apache License, Version 2.0. */
package io.ddd4j.sample.javalin.shiro.http;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/** HttpURLConnection-backed JDK 8 facade for sample tests. */
public final class HttpClient {
    private final int timeoutMillis;
    private HttpClient(int timeoutMillis) { this.timeoutMillis = timeoutMillis; }
    public static Builder newBuilder() { return new Builder(); }

    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> ignored) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) request.uri().toURL().openConnection();
        connection.setRequestMethod(request.method());
        connection.setConnectTimeout(timeoutMillis); connection.setReadTimeout(timeoutMillis);
        for (Map.Entry<String, String> header : request.headers().entrySet()) {
            connection.setRequestProperty(header.getKey(), header.getValue());
        }
        if (Objects.nonNull(request.body())) {
            byte[] payload = request.body().getBytes(StandardCharsets.UTF_8);
            connection.setDoOutput(true); connection.setFixedLengthStreamingMode(payload.length);
            try (OutputStream output = connection.getOutputStream()) { output.write(payload); }
        }
        int statusCode = connection.getResponseCode();
        InputStream input = statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
        StringBuilder body = new StringBuilder();
        if (Objects.nonNull(input)) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line; while (Objects.nonNull(line = reader.readLine())) { body.append(line); }
            }
        }
        @SuppressWarnings("unchecked") T typedBody = (T) body.toString();
        return new HttpResponse<>(statusCode, typedBody);
    }
    public static final class Builder {
        private int timeoutMillis = 5000;
        public Builder connectTimeout(Duration duration) { timeoutMillis = (int) duration.toMillis(); return this; }
        public HttpClient build() { return new HttpClient(timeoutMillis); }
    }
}
