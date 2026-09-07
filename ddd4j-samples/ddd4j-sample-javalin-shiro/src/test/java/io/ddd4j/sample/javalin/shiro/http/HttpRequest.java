/* Copyright (c) 2024-2026 ddd4j project. Licensed under the Apache License, Version 2.0. */
package io.ddd4j.sample.javalin.shiro.http;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/** JDK 8 facade for the subset of java.net.http.HttpRequest used by tests. */
public final class HttpRequest {
    private final URI uri; private final String method; private final String body;
    private final Map<String, String> headers;

    private HttpRequest(URI uri, String method, String body, Map<String, String> headers) {
        this.uri = uri; this.method = method; this.body = body; this.headers = headers;
    }
    public static Builder newBuilder(URI uri) { return new Builder().uri(uri); }
    URI uri() { return uri; } String method() { return method; } String body() { return body; }
    Map<String, String> headers() { return headers; }

    public static final class BodyPublishers {
        private BodyPublishers() { }
        public static String ofString(String body) { return body; }
        public static String noBody() { return null; }
    }
    public static final class Builder {
        private URI uri; private String method = "GET"; private String body;
        private final Map<String, String> headers = new LinkedHashMap<>();
        public Builder uri(URI uri) { this.uri = uri; return this; }
        public Builder header(String name, String value) { headers.put(name, value); return this; }
        public Builder GET() { method = "GET"; body = null; return this; }
        public Builder POST(String body) { method = "POST"; this.body = body; return this; }
        public Builder PUT(String body) { method = "PUT"; this.body = body; return this; }
        public Builder DELETE() { method = "DELETE"; body = null; return this; }
        public HttpRequest build() { return new HttpRequest(uri, method, body, new LinkedHashMap<>(headers)); }
    }
}
