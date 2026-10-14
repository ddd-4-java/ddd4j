package io.ddd4j.data.external.weather;

import com.alibaba.fastjson2.JSONObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 三线天气查询的本地 HTTP、JSON 与缓存契约。 */
class WeatherTemplateHttpContractTest {

    @Test
    void successfulResponseIsParsedAndCached() throws IOException {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = server(200, "{\"city\":\"ok\"}", requests);
        try {
            WeatherTemplate template = new WeatherTemplate(urlTemplate(server));
            String cityCode = UUID.randomUUID().toString();

            JSONObject first = template.getWeather(cityCode);
            JSONObject second = template.getWeather(cityCode);

            assertNotNull(first);
            assertEquals("ok", first.getString("city"));
            assertEquals(first, second);
            assertEquals(1, requests.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void failedResponseIsNotCached() throws IOException {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = server(503, "unavailable", requests);
        try {
            WeatherTemplate template = new WeatherTemplate(urlTemplate(server));
            String cityCode = UUID.randomUUID().toString();

            assertNull(template.getWeather(cityCode));
            assertNull(template.getWeather(cityCode));
            assertEquals(2, requests.get());
        } finally {
            server.stop(0);
        }
    }

    private HttpServer server(int status, String body, AtomicInteger requests) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/weather/city/", exchange -> reply(exchange, status, body, requests));
        server.start();
        return server;
    }

    private String urlTemplate(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/weather/city/%s";
    }

    private void reply(HttpExchange exchange, int status, String body, AtomicInteger requests) throws IOException {
        requests.incrementAndGet();
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
