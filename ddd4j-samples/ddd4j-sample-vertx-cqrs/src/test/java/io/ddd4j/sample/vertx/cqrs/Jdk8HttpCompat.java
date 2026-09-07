/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.ddd4j.sample.vertx.cqrs;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Minimal JDK 8 compatibility facade for the JDK 11 HTTP API used by the tests. */
final class HttpClient {
    static HttpClient newHttpClient() { return new HttpClient(); }

    HttpResponse<String> send(HttpRequest request, HttpResponse.BodyHandler<String> ignored) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) request.uri().toURL().openConnection();
        connection.setRequestMethod(request.method());
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        for (Map.Entry<String, String> header : request.headers().entrySet()) {
            connection.setRequestProperty(header.getKey(), header.getValue());
        }
        if (Objects.nonNull(request.body())) {
            byte[] payload = request.body().getBytes(StandardCharsets.UTF_8);
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(payload.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(payload);
            }
        }
        int statusCode = connection.getResponseCode();
        InputStream input = statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
        StringBuilder body = new StringBuilder();
        if (Objects.nonNull(input)) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while (Objects.nonNull(line = reader.readLine())) {
                    body.append(line);
                }
            }
        }
        return new HttpResponse<>(statusCode, body.toString());
    }
}

final class HttpRequest {
    private final URI uri;
    private final String method;
    private final String body;
    private final Map<String, String> headers;

    private HttpRequest(URI uri, String method, String body, Map<String, String> headers) {
        this.uri = uri;
        this.method = method;
        this.body = body;
        this.headers = headers;
    }

    static Builder newBuilder() { return new Builder(); }
    URI uri() { return uri; }
    String method() { return method; }
    String body() { return body; }
    Map<String, String> headers() { return headers; }

    static final class BodyPublishers {
        static String ofString(String body) { return body; }
    }

    static final class Builder {
        private URI uri;
        private String method = "GET";
        private String body;
        private final Map<String, String> headers = new LinkedHashMap<>();

        Builder uri(URI uri) { this.uri = uri; return this; }
        Builder header(String name, String value) { headers.put(name, value); return this; }
        Builder POST(String body) { this.method = "POST"; this.body = body; return this; }
        Builder GET() { this.method = "GET"; return this; }
        HttpRequest build() { return new HttpRequest(uri, method, body, new LinkedHashMap<>(headers)); }
    }
}

final class HttpResponse<T> {
    private final int statusCode;
    private final T body;

    HttpResponse(int statusCode, T body) { this.statusCode = statusCode; this.body = body; }
    int statusCode() { return statusCode; }
    T body() { return body; }

    interface BodyHandler<T> { }
    static final class BodyHandlers {
        static BodyHandler<String> ofString() { return new BodyHandler<String>() { }; }
    }
}
