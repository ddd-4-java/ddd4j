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

import static org.junit.jupiter.api.Assertions.*;

/**
 * 三线太平洋网络 IP 适配器的本地 HTTP 与缓存契约。
 */
class PconlineRegionHttpContractTest {

    /**
     * 验证<code>first.isPresent()</code>为真，<code>second</code> 等于 <code>first</code>，<code>cache.ttl</code> 等于 <code>Duration.ofMinutes(30)</code>。
     *
     * @throws IOException 读取或写入测试数据时发生 I/O 错误
     */
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

    /**
     * 验证<code>template.getLocationByIp("127.0.0.1").isPresent()</code>为假，<code>requests.get()</code> 等于 <code>2</code>，<code>cache.writes</code> 等于 <code>0</code>。
     *
     * @throws IOException 读取或写入测试数据时发生 I/O 错误
     */
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

    /**
     * 验证<code>template.getLocationByIp(null)</code> 抛出 <code>NullPointerException.class</code>，<code>template.getLocationByIp("not-ip")</code> 抛出 <code>IllegalArgumentException.class</code>，<code>requests.get()</code> 等于 <code>0</code>。
     *
     * @throws IOException 读取或写入测试数据时发生 I/O 错误
     */
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

    /**
     * 验证<code>template.getRegionAddress("127.0.0.1").getCountry()</code> 等于 <code>"Fictionland"</code>，<code>requests.get()</code> 等于 <code>1</code>。
     *
     * @throws IOException 读取或写入测试数据时发生 I/O 错误
     */
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

    /**
     * 创建 <code>new PconlineRegionTemplate</code> 供当前测试路径调用。
     *
     * @param server 传给 <code>server.getAddress()</code> 的 <code>HttpServer</code> 输入
     * @param cache  用于构造本次返回结果的 <code>RegionCache</code> 输入
     * @return 当前测试使用的结果，取自 <code>new PconlineRegionTemplate(cache, "http://127.0.0.1:" + server.getAddress().getPort() + "/ip?json=true&amp;ip=%s")</code>
     */
    private PconlineRegionTemplate template(HttpServer server, RegionCache cache) {
        return new PconlineRegionTemplate(cache,
                "http://127.0.0.1:" + server.getAddress().getPort() + "/ip?json=true&ip=%s");
    }

    /**
     * 读取 <code>server</code> 供当前测试使用。
     *
     * @param status   当前 HTTP 或业务流程的状态
     * @param body     当前请求或响应中要读取的正文
     * @param requests 传给 <code>server.createContext()</code> 的 <code>AtomicInteger</code> 输入
     * @return 当前测试使用的结果，取自 <code>server</code>
     * @throws IOException 读取或写入测试数据时发生 I/O 错误
     */
    private HttpServer server(int status, String body, AtomicInteger requests) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ip", exchange -> reply(exchange, status, body, requests));
        server.start();
        return server;
    }

    /**
     * 调用 <code>requests.incrementAndGet()</code>、<code>body.getBytes()</code>、<code>exchange.getResponseHeaders()</code> 执行当前夹具操作。
     *
     * @param exchange 当前 HTTP 请求及响应交换对象
     * @param status   当前 HTTP 或业务流程的状态
     * @param body     当前请求或响应中要读取的正文
     * @param requests 传给 <code>requests.incrementAndGet()</code> 的 <code>AtomicInteger</code> 输入
     * @throws IOException 读取或写入测试数据时发生 I/O 错误
     */
    private void reply(HttpExchange exchange, int status, String body, AtomicInteger requests) throws IOException {
        requests.incrementAndGet();
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    /**
     * 记录缓存操作，供测试验证调用结果或顺序。
     */
    private static final class RecordingCache implements RegionCache {
        /**
         * 供测试线程安全保存当前观察结果的引用。
         */
        private final AtomicReference<String> value = new AtomicReference<>();
        /**
         * 用于验证实际写入动作或写入次数的记录。
         */
        private int writes;
        /**
         * 测试存储使用或记录的到期时长。
         */
        private Duration ttl;

        /**
         * 读取缓存字符串。
         *
         * <p>测试夹具会调用 <code>value.get()</code>。</p>
         *
         * @param key 缓存键
         * @return 实际返回<code>value.get()</code>
         */
        @Override
        public String getString(String key) {
            return value.get();
        }

        /**
         * 写入带有效期的缓存字符串。
         *
         * <p>测试夹具会调用 <code>value.set(stored)</code>。</p>
         *
         * @param key      缓存键
         * @param stored   传给 <code>value.set(stored)</code>
         * @param duration 用于此操作的 <code>duration</code>（<code>Duration</code>）
         */
        @Override
        public void set(String key, String stored, Duration duration) {
            value.set(stored);
            ttl = duration;
            writes++;
        }
    }
}
