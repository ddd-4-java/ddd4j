/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.sample.micronaut;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MicronautOrderControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldRunSharedOrderUseCasesThroughMicronautHttp() throws Exception {
        Map<String, Object> properties = new HashMap<>();
        properties.put("micronaut.server.port", -1);
        properties.put("ddd4j.web.public-paths", Collections.singletonList("/api/auth/**"));
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, properties)) {
            String baseUrl = "http://127.0.0.1:" + server.getPort();
            String token = issueToken(baseUrl);
            Response create = request(baseUrl, "POST", "/api/orders", token,
                    "{\"orderNo\":\"MICRONAUT-001\",\"buyerId\":\"buyer-1\",\"buyerName\":\"Alice\"}", null);
            assertEquals(201, create.status, create.body);
            String orderId = objectMapper.readTree(create.body).path("data").path("id").asText();

            Response line = request(baseUrl, "POST", "/api/orders/" + orderId + "/lines", token,
                    "{\"goodsId\":\"goods-1\",\"goodsName\":\"DDD Book\",\"quantity\":2,\"unitPrice\":59.90}", null);
            assertEquals(200, line.status, line.body);
            assertEquals(0, objectMapper.readTree(line.body).path("data").path("totalAmount")
                    .decimalValue().compareTo(new java.math.BigDecimal("119.80")));

            Response missingKey = request(baseUrl, "POST", "/api/orders/" + orderId + "/pay", token, null, null);
            assertEquals(400, missingKey.status, missingKey.body);

            Response paid = request(baseUrl, "POST", "/api/orders/" + orderId + "/pay", token,
                    null, "micronaut-payment-001");
            assertEquals(200, paid.status, paid.body);
            assertEquals("PAID", objectMapper.readTree(paid.body).path("data").path("status").asText());
        }
    }

    private String issueToken(String baseUrl) throws Exception {
        Response response = request(baseUrl, "POST", "/api/auth/tokens/micronaut-user", null, null, null);
        JsonNode body = objectMapper.readTree(response.body);
        assertEquals(200, response.status, response.body);
        String token = body.path("data").path("token").asText();
        assertFalse(token.isEmpty());
        return token;
    }

    private Response request(String baseUrl, String method, String path, String token,
                             String body, String idempotencyKey) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
        try {
            connection.setRequestMethod(method);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            if (Objects.nonNull(token)) {
                connection.setRequestProperty("Authorization", "Bearer " + token);
            }
            if (Objects.nonNull(idempotencyKey)) {
                connection.setRequestProperty("Idempotency-Key", idempotencyKey);
            }
            if ("POST".equals(method)) {
                connection.setRequestProperty("Content-Type", "application/json");
            }
            if (Objects.nonNull(body)) {
                connection.setDoOutput(true);
                connection.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
            } else if ("POST".equals(method)) {
                connection.setDoOutput(true);
                connection.getOutputStream().close();
            }
            int status = connection.getResponseCode();
            InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (Objects.isNull(input)) {
                return new Response(status, "");
            }
            try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = stream.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }
                return new Response(status, new String(output.toByteArray(), StandardCharsets.UTF_8));
            }
        } finally {
            connection.disconnect();
        }
    }

    private static class Response {
        private final int status;
        private final String body;

        private Response(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }
}
