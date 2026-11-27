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

    /**
     * \u9a8c\u8bc1<code>first.isPresent()</code>\u4e3a\u771f\uff0c<code>second</code> \u7b49\u4e8e <code>first</code>\uff0c<code>cache.ttl.getSeconds() &gt; 0 &amp;&amp; cache.ttl.getSeconds() &lt;= Duration.ofDays(1).getSeconds()</code>\u4e3a\u771f\u3002
     * @throws IOException \u8bfb\u53d6\u6216\u5199\u5165\u6d4b\u8bd5\u6570\u636e\u65f6\u53d1\u751f I/O \u9519\u8bef
     */
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

    /**
     * \u9a8c\u8bc1<code>template.getLocationByIp("127.0.0.1").isPresent()</code>\u4e3a\u5047\uff0c<code>requests.get()</code> \u7b49\u4e8e <code>2</code>\uff0c<code>cache.writes</code> \u7b49\u4e8e <code>0</code>\u3002
     * @throws IOException \u8bfb\u53d6\u6216\u5199\u5165\u6d4b\u8bd5\u6570\u636e\u65f6\u53d1\u751f I/O \u9519\u8bef
     */
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

    /**
     * \u9a8c\u8bc1<code>template.getLocationByIp(null)</code> \u629b\u51fa <code>NullPointerException.class</code>\uff0c<code>template.getLocationByIp("not-ip")</code> \u629b\u51fa <code>IllegalArgumentException.class</code>\uff0c<code>requests.get()</code> \u7b49\u4e8e <code>0</code>\u3002
     * @throws IOException \u8bfb\u53d6\u6216\u5199\u5165\u6d4b\u8bd5\u6570\u636e\u65f6\u53d1\u751f I/O \u9519\u8bef
     */
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

    /**
     * \u521b\u5efa <code>new BaiduRegionTemplate</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @param server \u4f20\u7ed9 <code>server.getAddress()</code> \u7684 <code>HttpServer</code> \u8f93\u5165
     * @param cache \u7528\u4e8e\u6784\u9020\u672c\u6b21\u8fd4\u56de\u7ed3\u679c\u7684 <code>RegionCache</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new BaiduRegionTemplate("test-ak", cache, "http://127.0.0.1:" + server.getAddress().getPort() + "/location?ak=%s&amp;ip=%s")</code>
     */
    private BaiduRegionTemplate template(HttpServer server, RegionCache cache) {
        return new BaiduRegionTemplate("test-ak", cache,
                "http://127.0.0.1:" + server.getAddress().getPort() + "/location?ak=%s&ip=%s");
    }

    /**
     * \u8bfb\u53d6 <code>server</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @param status \u5f53\u524d HTTP \u6216\u4e1a\u52a1\u6d41\u7a0b\u7684\u72b6\u6001
     * @param body \u5f53\u524d\u8bf7\u6c42\u6216\u54cd\u5e94\u4e2d\u8981\u8bfb\u53d6\u7684\u6b63\u6587
     * @param requests \u4f20\u7ed9 <code>server.createContext()</code> \u7684 <code>AtomicInteger</code> \u8f93\u5165
     * @param request \u5f85\u4ea4\u7ed9\u88ab\u6d4b\u670d\u52a1\u5904\u7406\u7684\u8bf7\u6c42
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>server</code>
     * @throws IOException \u8bfb\u53d6\u6216\u5199\u5165\u6d4b\u8bd5\u6570\u636e\u65f6\u53d1\u751f I/O \u9519\u8bef
     */
    private HttpServer server(int status, String body, AtomicInteger requests,
                              AtomicReference<String> request) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/location", exchange -> reply(exchange, status, body, requests, request));
        server.start();
        return server;
    }

    /**
     * \u8c03\u7528 <code>requests.incrementAndGet()</code>\u3001<code>request.set()</code>\u3001<code>exchange.getRequestURI()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param exchange \u5f53\u524d HTTP \u8bf7\u6c42\u53ca\u54cd\u5e94\u4ea4\u6362\u5bf9\u8c61
     * @param status \u5f53\u524d HTTP \u6216\u4e1a\u52a1\u6d41\u7a0b\u7684\u72b6\u6001
     * @param body \u5f53\u524d\u8bf7\u6c42\u6216\u54cd\u5e94\u4e2d\u8981\u8bfb\u53d6\u7684\u6b63\u6587
     * @param requests \u4f20\u7ed9 <code>requests.incrementAndGet()</code> \u7684 <code>AtomicInteger</code> \u8f93\u5165
     * @param request \u5f85\u4ea4\u7ed9\u88ab\u6d4b\u670d\u52a1\u5904\u7406\u7684\u8bf7\u6c42
     * @throws IOException \u8bfb\u53d6\u6216\u5199\u5165\u6d4b\u8bd5\u6570\u636e\u65f6\u53d1\u751f I/O \u9519\u8bef
     */
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

    /** \u8bb0\u5f55\u7f13\u5b58\u64cd\u4f5c\uff0c\u4f9b\u6d4b\u8bd5\u9a8c\u8bc1\u8c03\u7528\u7ed3\u679c\u6216\u987a\u5e8f\u3002 */
    private static final class RecordingCache implements RegionCache {
        /** \u4f9b\u6d4b\u8bd5\u7ebf\u7a0b\u5b89\u5168\u4fdd\u5b58\u5f53\u524d\u89c2\u5bdf\u7ed3\u679c\u7684\u5f15\u7528\u3002 */
        private final AtomicReference<String> value = new AtomicReference<>();
        /** \u7528\u4e8e\u9a8c\u8bc1\u5b9e\u9645\u5199\u5165\u52a8\u4f5c\u6216\u5199\u5165\u6b21\u6570\u7684\u8bb0\u5f55\u3002 */
        private int writes;
        /** \u6d4b\u8bd5\u5b58\u50a8\u4f7f\u7528\u6216\u8bb0\u5f55\u7684\u5230\u671f\u65f6\u957f\u3002 */
        private Duration ttl;

        /** {@inheritDoc} */
        @Override
        public String getString(String key) {
            return value.get();
        }

        /** {@inheritDoc} */
        @Override
        public void set(String key, String stored, Duration duration) {
            value.set(stored);
            ttl = duration;
            writes++;
        }
    }
}
