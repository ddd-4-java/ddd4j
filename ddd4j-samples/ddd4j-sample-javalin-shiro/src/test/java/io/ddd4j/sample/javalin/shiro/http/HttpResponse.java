/* Copyright (c) 2024-2026 ddd4j project. Licensed under the Apache License, Version 2.0. */
package io.ddd4j.sample.javalin.shiro.http;

/** JDK 8 facade for the subset of java.net.http.HttpResponse used by tests. */
public final class HttpResponse<T> {
    private final int statusCode; private final T body;
    HttpResponse(int statusCode, T body) { this.statusCode = statusCode; this.body = body; }
    public int statusCode() { return statusCode; } public T body() { return body; }
    public interface BodyHandler<T> { }
    public static final class BodyHandlers {
        private BodyHandlers() { }
        public static BodyHandler<String> ofString() { return new BodyHandler<String>() { }; }
    }
}
