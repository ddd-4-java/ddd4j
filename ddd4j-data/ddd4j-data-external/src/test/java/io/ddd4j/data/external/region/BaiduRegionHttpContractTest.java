package io.ddd4j.data.external.region;

import com.alibaba.fastjson2.JSONObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 三线百度区域查询在各自 HTTP 客户端下的本地结果与缓存契约。 */
class BaiduRegionHttpContractTest {

    @Test
    void successfulLookupCachesUntilNextDay() throws IOException {
        AtomicInteger requests = new AtomicInteger();
        AtomicReference<String> request = new AtomicReference<>();
        HttpServer server = server(200, "{\"status\":0,\"address\":\"CN|浙江省|杭州市|None|CHINANET|1|None\"}",
                requests, request);
        try {
            RecordingCache cache = new RecordingCache();
            BaiduRegionTemplate template = template(server, cache);

            Optional<JSONObject> first = template.getLocationByIp("127.0.0.1");
            Optional<JSONObject> second = template.getLocationByIp("127.0.0.1");

            assertTrue(first.isPresent());
            assertEquals(first, second);
            assertEquals(1, requests.get());
            assertTrue(request.get().contains("ak=test-ak&ip=127.0.0.1"));
            assertEquals(1, cache.writes);
            assertTrue(cache.ttl.getSeconds() > 0 && cache.ttl.getSeconds() <= Duration.ofDays(1).getSeconds());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void providerErrorAndHttpFailureAreNotCached() throws IOException {
        for (int status : new int[]{200, 503}) {
            AtomicInteger requests = new AtomicInteger();
            String body = status == 200 ? "{\"status\":220,\"message\":\"invalid-ak\"}" : "unavailable";
            HttpServer server = server(status, body, requests, new AtomicReference<String>());
            try {
                RecordingCache cache = new RecordingCache();
                BaiduRegionTemplate template = template(server, cache);

                assertFalse(template.getLocationByIp("127.0.0.1").isPresent());
                assertFalse(template.getLocationByIp("127.0.0.1").isPresent());
                assertEquals(2, requests.get());
                assertEquals(0, cache.writes);
            } finally {
                server.stop(0);
            }
        }
    }

    @Test
    void invalidIpFailsBeforeHttpRequest() throws IOException {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = server(200, "{\"status\":0}", requests, new AtomicReference<String>());
        try {
            BaiduRegionTemplate template = template(server, RegionCache.none());
            assertThrows(NullPointerException.class, () -> template.getLocationByIp(null));
            assertThrows(IllegalArgumentException.class, () -> template.getLocationByIp("not-ip"));
            assertEquals(0, requests.get());
        } finally {
            server.stop(0);
        }
    }

    private BaiduRegionTemplate template(HttpServer server, RegionCache cache) {
        return new BaiduRegionTemplate("test-ak", cache,
                "http://127.0.0.1:" + server.getAddress().getPort() + "/location?ak=%s&ip=%s");
    }

    private HttpServer server(int status, String body, AtomicInteger requests,
                              AtomicReference<String> request) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/location", exchange -> reply(exchange, status, body, requests, request));
        server.start();
        return server;
    }

    private void reply(HttpExchange exchange, int status, String body, AtomicInteger requests,
                       AtomicReference<String> request) throws IOException {
        requests.incrementAndGet();
        request.set(exchange.getRequestURI().toString());
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static final class RecordingCache implements RegionCache {
        private final AtomicReference<String> value = new AtomicReference<>();
        private int writes;
        private Duration ttl;

        @Override
        public String getString(String key) {
            return value.get();
        }

        @Override
        public void set(String key, String stored, Duration duration) {
            value.set(stored);
            ttl = duration;
            writes++;
        }
    }
}
