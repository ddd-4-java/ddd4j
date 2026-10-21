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

/** 三线太平洋网络 IP 适配器的本地 HTTP 与缓存契约。 */
class PconlineRegionHttpContractTest {

    @Test
    void successfulLookupCachesForThirtyMinutes() throws IOException {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = server(200, "{\"addr\":\" 浙江 杭州 \",\"pro\":\"浙江\",\"city\":\"杭州\"}", requests);
        try {
            RecordingCache cache = new RecordingCache();
            PconlineRegionTemplate template = template(server, cache);

            Optional<JSONObject> first = template.getLocationByIp("127.0.0.1");
            Optional<JSONObject> second = template.getLocationByIp("127.0.0.1");

            assertTrue(first.isPresent());
            assertEquals(first, second);
            assertEquals(1, requests.get());
            assertEquals(1, cache.writes);
            assertEquals(Duration.ofMinutes(30), cache.ttl);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void blankAddressAndHttpFailureAreNotCached() throws IOException {
        for (int status : new int[]{200, 503}) {
            AtomicInteger requests = new AtomicInteger();
            String body = status == 200 ? "{\"addr\":\"   \"}" : "unavailable";
            HttpServer server = server(status, body, requests);
            try {
                RecordingCache cache = new RecordingCache();
                PconlineRegionTemplate template = template(server, cache);

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
        HttpServer server = server(200, "{\"addr\":\"杭州\"}", requests);
        try {
            PconlineRegionTemplate template = template(server, RegionCache.none());
            assertThrows(NullPointerException.class, () -> template.getLocationByIp(null));
            assertThrows(IllegalArgumentException.class, () -> template.getLocationByIp("not-ip"));
            assertEquals(0, requests.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void regionAddressStripsUnicodeWhitespace() throws IOException {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = server(200, "{\"addr\":\"\\u2003Fictionland\\u2003\"}", requests);
        try {
            PconlineRegionTemplate template = template(server, RegionCache.none());

            assertEquals("Fictionland", template.getRegionAddress("127.0.0.1").getCountry());
            assertEquals(1, requests.get());
        } finally {
            server.stop(0);
        }
    }

    private PconlineRegionTemplate template(HttpServer server, RegionCache cache) {
        return new PconlineRegionTemplate(cache,
                "http://127.0.0.1:" + server.getAddress().getPort() + "/ip?json=true&ip=%s");
    }

    private HttpServer server(int status, String body, AtomicInteger requests) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ip", exchange -> reply(exchange, status, body, requests));
        server.start();
        return server;
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
