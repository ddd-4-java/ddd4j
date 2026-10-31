package io.ddd4j.sample.javalin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.ddd4j.sample.javalin.order.infrastructure.JavalinOrderAdapters;
import io.ddd4j.sample.order.application.OutboxMessage;
import io.ddd4j.sample.order.domain.event.OrderPaidEvent;
import io.ddd4j.sample.order.jdbc.JdbcOrderSampleRuntime;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 使用真实 PostgreSQL 验证 HTTP 订单入口与可靠 Outbox 接线。 */
@Testcontainers(disabledWithoutDocker = true)
class JavalinJdbcOrderIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withTmpFs(Collections.singletonMap("/var/lib/postgresql/data", "rw,size=512m"));

    @Test
    void httpOrderPersistsAndPublishesOutbox() throws Exception {
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setUrl(POSTGRES.getJdbcUrl());
        source.setUser(POSTGRES.getUsername());
        source.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        List<OutboxMessage> published = new CopyOnWriteArrayList<>();
        JdbcOrderSampleRuntime runtime = JdbcOrderSampleRuntime.start(source,
                new ObjectMapper().registerModule(new JavaTimeModule()), new JavalinOrderAdapters(),
                published::add, "javalin-http-it", 100, Duration.ofMillis(50), Clock.systemUTC());
        try (JavalinSample.JavalinApplication application = JavalinSample.startJdbc(0, runtime)) {
            String base = "http://127.0.0.1:" + application.app().port();
            String token = application.token();
            String orderNo = "HTTP-" + UUID.randomUUID();
            Response created = request(base, token, "POST", "/api/orders",
                    "{\"orderNo\":\"" + orderNo + "\",\"buyerId\":\"buyer-http\",\"buyerName\":\"Alice\"}", null);
            assertEquals(201, created.status);
            String orderId = extract(created.body, "id");
            assertEquals(200, request(base, token, "POST", "/api/orders/" + orderId + "/lines",
                    "{\"goodsId\":\"sku-1\",\"goodsName\":\"Book\",\"quantity\":2,\"unitPrice\":19.90}", null).status);
            assertEquals(200, request(base, token, "POST", "/api/orders/" + orderId + "/pay",
                    "", "payment-" + orderId).status);
            assertEquals(200, request(base, token, "POST", "/api/orders/" + orderId + "/pay",
                    "", "payment-" + orderId).status);
            Response found = request(base, token, "GET", "/api/orders/" + orderId, null, null);
            assertEquals(200, found.status);
            assertTrue(found.body.contains("\"status\":\"PAID\""));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (published.stream().noneMatch(message -> OrderPaidEvent.class.getName().equals(message.eventType()))
                    && System.nanoTime() < deadline) {
                TimeUnit.MILLISECONDS.sleep(50);
            }
            assertTrue(published.stream().anyMatch(message -> OrderPaidEvent.class.getName().equals(message.eventType())));
            assertEquals(1, published.stream()
                    .filter(message -> OrderPaidEvent.class.getName().equals(message.eventType()))
                    .count(), "repeated payment must not publish a second paid event");
        }
    }

    private static Response request(String base, String token, String method, String path,
                                    String body, String idempotencyKey) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(base + path).openConnection();
        try {
            connection.setRequestMethod(method);
            connection.setRequestProperty("Authorization", "Bearer " + token);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            if (Objects.nonNull(idempotencyKey)) {
                connection.setRequestProperty("Idempotency-Key", idempotencyKey);
            }
            if (Objects.nonNull(body)) {
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);
                connection.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
            }
            int status = connection.getResponseCode();
            try (InputStream input = status < 400 ? connection.getInputStream() : connection.getErrorStream()) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    output.write(buffer, 0, count);
                }
                return new Response(status, new String(output.toByteArray(), StandardCharsets.UTF_8));
            }
        } finally {
            connection.disconnect();
        }
    }

    private static String extract(String json, String field) {
        String prefix = "\"" + field + "\":\"";
        int valueStart = json.indexOf(prefix) + prefix.length();
        return json.substring(valueStart, json.indexOf('"', valueStart));
    }

    private static final class Response {
        private final int status;
        private final String body;

        private Response(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }
}
