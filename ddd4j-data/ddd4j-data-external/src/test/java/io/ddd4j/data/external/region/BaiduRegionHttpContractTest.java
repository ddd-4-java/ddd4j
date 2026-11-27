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
 * 三线百度区域查询在各自 HTTP 客户端下的本地结果与缓存契约。
 */
class BaiduRegionHttpContractTest {

    /**
     * 验证<code>first.isPresent()</code>为真，<code>second</code> 等于 <code>first</code>，<code>cache.ttl.getSeconds() &gt; 0 &amp;&amp; cache.ttl.getSeconds() &lt;= Duration.ofDays(1).getSeconds()</code>为真。
     *
     * @throws IOException 读取或写入测试数据时发生 I/O 错误
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
     * 验证<code>template.getLocationByIp("127.0.0.1").isPresent()</code>为假，<code>requests.get()</code> 等于 <code>2</code>，<code>cache.writes</code> 等于 <code>0</code>。
     *
     * @throws IOException 读取或写入测试数据时发生 I/O 错误
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
     * 验证<code>template.getLocationByIp(null)</code> 抛出 <code>NullPointerException.class</code>，<code>template.getLocationByIp("not-ip")</code> 抛出 <code>IllegalArgumentException.class</code>，<code>requests.get()</code> 等于 <code>0</code>。
     *
     * @throws IOException 读取或写入测试数据时发生 I/O 错误
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
     * 创建 <code>new BaiduRegionTemplate</code> 供当前测试路径调用。
     *
     * @param server 传给 <code>server.getAddress()</code> 的 <code>HttpServer</code> 输入
     * @param cache  用于构造本次返回结果的 <code>RegionCache</code> 输入
     * @return 当前测试使用的结果，取自 <code>new BaiduRegionTemplate("test-ak", cache, "http://127.0.0.1:" + server.getAddress().getPort() + "/location?ak=%s&amp;ip=%s")</code>
     */
    private BaiduRegionTemplate template(HttpServer server, RegionCache cache) {
        return new BaiduRegionTemplate("test-ak", cache,
                "http://127.0.0.1:" + server.getAddress().getPort() + "/location?ak=%s&ip=%s");
    }

    /**
     * 读取 <code>server</code> 供当前测试使用。
     *
     * @param status   当前 HTTP 或业务流程的状态
     * @param body     当前请求或响应中要读取的正文
     * @param requests 传给 <code>server.createContext()</code> 的 <code>AtomicInteger</code> 输入
     * @param request  待交给被测服务处理的请求
     * @return 当前测试使用的结果，取自 <code>server</code>
     * @throws IOException 读取或写入测试数据时发生 I/O 错误
     */
    private HttpServer server(int status, String body, AtomicInteger requests,
                              AtomicReference<String> request) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/location", exchange -> reply(exchange, status, body, requests, request));
        server.start();
        return server;
    }

    /**
     * 调用 <code>requests.incrementAndGet()</code>、<code>request.set()</code>、<code>exchange.getRequestURI()</code> 执行当前夹具操作。
     *
     * @param exchange 当前 HTTP 请求及响应交换对象
     * @param status   当前 HTTP 或业务流程的状态
     * @param body     当前请求或响应中要读取的正文
     * @param requests 传给 <code>requests.incrementAndGet()</code> 的 <code>AtomicInteger</code> 输入
     * @param request  待交给被测服务处理的请求
     * @throws IOException 读取或写入测试数据时发生 I/O 错误
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
