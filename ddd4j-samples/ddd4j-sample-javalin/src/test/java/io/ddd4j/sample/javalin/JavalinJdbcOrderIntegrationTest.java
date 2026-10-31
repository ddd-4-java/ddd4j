package io.ddd4j.sample.javalin;

import io.ddd4j.sample.javalin.order.infrastructure.JavalinOrderAdapters;
import io.ddd4j.sample.order.application.OutboxMessage;
import io.ddd4j.sample.order.jdbc.JdbcOrderSampleRuntime;
import io.ddd4j.sample.order.domain.event.OrderPaidEvent;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
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
            .withTmpFs(Map.of("/var/lib/postgresql/data", "rw,size=512m"));

    @Test
    void httpOrderPersistsAndPublishesOutbox() throws Exception {
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setUrl(POSTGRES.getJdbcUrl());
        source.setUser(POSTGRES.getUsername());
        source.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        List<OutboxMessage> published = new CopyOnWriteArrayList<>();
        JdbcOrderSampleRuntime runtime = JdbcOrderSampleRuntime.start(source, new ObjectMapper(),
                new JavalinOrderAdapters(), published::add, "javalin-http-it", 100,
                Duration.ofMillis(50), Clock.systemUTC());
        try (JavalinSample.JavalinApplication application = JavalinSample.startJdbc(0, runtime)) {
            String base = "http://127.0.0.1:" + application.app().port();
            HttpClient client = HttpClient.newHttpClient();
            String orderNo = "HTTP-" + UUID.randomUUID();
            HttpResponse<String> created = post(client, base, application.token(), "/api/orders",
                    "{\"orderNo\":\"" + orderNo + "\",\"buyerId\":\"buyer-http\",\"buyerName\":\"Alice\"}", null);
            assertEquals(201, created.statusCode(), created.body());
            String orderId = extract(created.body(), "id");
            assertEquals(200, post(client, base, application.token(), "/api/orders/" + orderId + "/lines",
                    "{\"goodsId\":\"sku-1\",\"goodsName\":\"Book\",\"quantity\":2,\"unitPrice\":19.90}", null).statusCode());
            assertEquals(200, post(client, base, application.token(), "/api/orders/" + orderId + "/pay",
                    "", "payment-" + orderId).statusCode());
            HttpRequest query = HttpRequest.newBuilder(URI.create(base + "/api/orders/" + orderId))
                    .header("Authorization", "Bearer " + application.token()).GET().build();
            HttpResponse<String> found = client.send(query, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, found.statusCode(), found.body());
            assertTrue(found.body().contains("\"status\":\"PAID\""));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (published.stream().noneMatch(message -> OrderPaidEvent.class.getName().equals(message.eventType()))
                    && System.nanoTime() < deadline) {
                TimeUnit.MILLISECONDS.sleep(50);
            }
            assertTrue(published.stream().anyMatch(message -> OrderPaidEvent.class.getName().equals(message.eventType())));
        }
    }

    private static HttpResponse<String> post(HttpClient client, String base, String token,
                                             String path, String body, String idempotencyKey) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "application/json").header("Authorization", "Bearer " + token);
        if (Objects.nonNull(idempotencyKey)) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return client.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static String extract(String json, String field) {
        String prefix = "\"" + field + "\":\"";
        int valueStart = json.indexOf(prefix) + prefix.length();
        return json.substring(valueStart, json.indexOf('"', valueStart));
    }
}
