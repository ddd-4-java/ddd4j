package io.ddd4j.sample.dropwizard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.cache.CacheKit;
import io.ddd4j.core.cache.Cache;
import io.ddd4j.web.core.auth.AuthenticationMode;
import io.dropwizard.core.server.DefaultServerFactory;
import io.dropwizard.jetty.HttpConnectorFactory;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.DropwizardTestSupport;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.ServerSocket;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 使用 Dropwizard 2 原生服务器验证默认订单入口和本实例缓存生命周期。
 */
@Testcontainers
class NativeDefaultTest {

    /**
     * 为当前集成测试提供独立 MySQL 服务的容器。
     */
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");
    /**
     * 商品测试所使用的路由或业务编码。
     */
    private static final String GOODS = """
            {"spuName":"Lunch","skuCode":"S1","skuName":"Weekday","salePriceCent":1250,"shopId":7}
            """;
    /**
     * 测试订单的固定标识，用于组织和查询测试数据。
     */
    private static final String ORDER = """
            {"shopId":7,"items":[{"skuCode":"S1","quantity":2}]}
            """;

    /**
     * 负责转换测试或协议正文中 JSON 数据的 Jackson 映射器。
     */
    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * 读取 <code>name</code> 供当前测试使用。
     *
     * @return 当前测试使用的结果，取自 <code>name</code>
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    private static String runAndClose() throws Exception {
        DropwizardOrderConfiguration configuration = configuration(0);
        DropwizardTestSupport<DropwizardOrderConfiguration> app = application(configuration);
        try {
            app.before();
            assertNotNull(CacheKit.getCache(configuration.getDdd4jWeb().getIdempotencyCacheName()));
        } finally {
            app.after();
        }
        String name = configuration.getDdd4jWeb().getIdempotencyCacheName();
        assertNull(CacheKit.getCache(name));
        return name;
    }

    /**
     * 读取 <code>configuration</code> 供当前测试使用。
     *
     * @param port 测试服务监听的本地端口
     * @return 当前测试使用的结果，取自 <code>configuration</code>
     */
    private static DropwizardOrderConfiguration configuration(int port) {
        DropwizardOrderConfiguration configuration = new DropwizardOrderConfiguration();
        configuration.setCatalogJdbcUrl(MYSQL.getJdbcUrl());
        configuration.setCatalogDbUser(MYSQL.getUsername());
        configuration.setCatalogDbPassword(MYSQL.getPassword());
        DefaultServerFactory server = (DefaultServerFactory) configuration.getServerFactory();
        ((HttpConnectorFactory) server.getApplicationConnectors().get(0)).setPort(port);
        ((HttpConnectorFactory) server.getAdminConnectors().get(0)).setPort(0);
        return configuration;
    }

    /**
     * 创建 <code>new DropwizardTestSupport&lt;&gt;</code> 供当前测试路径调用。
     *
     * @param configuration 用于构造本次返回结果的 <code>DropwizardOrderConfiguration</code> 输入
     * @return 当前测试使用的结果，取自 <code>new DropwizardTestSupport&lt;&gt;(DropwizardOrderApplication.class, configuration)</code>
     */
    private static DropwizardTestSupport<DropwizardOrderConfiguration> application(DropwizardOrderConfiguration configuration) {
        return new DropwizardTestSupport<>(DropwizardOrderApplication.class, configuration);
    }

    /**
     * 通过 <code>JSON.readTree()</code> 取得当前测试需要的结果。
     *
     * @param client 用于发出测试请求的客户端
     * @param base   传给 <code>client.target()</code> 的 <code>String</code> 输入
     * @param method 发给被测服务的 HTTP 方法
     * @param path   测试请求的资源路径或待访问文件路径
     * @param token  从测试登录获得的会话凭据
     * @param key    用于在测试上下文或缓存中索引数据的键
     * @param body   当前请求或响应中要读取的正文
     * @param status 当前 HTTP 或业务流程的状态
     * @return 当前测试使用的结果，取自 <code>JSON.readTree(actual)</code>
     * @throws Exception 处理测试数据的序列化、发起测试请求时失败
     */
    private static JsonNode request(Client client, String base, String method, String path, String token,
                                    String key, String body, int status) throws Exception {
        Invocation.Builder request = client.target(base + path).request()
                .header("X-Tenant-Id", "dw-" + Integer.toUnsignedString(base.hashCode()));
        if (Objects.nonNull(token)) {
            request.header("Authorization", "Bearer " + token);
        }
        if (Objects.nonNull(key)) {
            request.header("Idempotency-Key", key);
        }
        try (Response response = "GET".equals(method) ? request.get()
                : request.post(Entity.entity(Objects.isNull(body) ? "" : body, MediaType.APPLICATION_JSON_TYPE))) {
            String actual = response.readEntity(String.class);
            assertEquals(status, response.getStatus(), actual);
            return JSON.readTree(actual);
        }
    }

    /**
     * 在测试框架中执行 <code>ConfigOverride.config()</code>、<code>MYSQL.getJdbcUrl()</code>、<code>MYSQL.getUsername()</code>，验证该场景。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void stockYamlRunsAnonymousOrderThroughNativeHttp() throws Exception {
        DropwizardTestSupport<DropwizardOrderConfiguration> app = new DropwizardTestSupport<>(
                DropwizardOrderApplication.class, "src/main/resources/dropwizard-order.yml",
                ConfigOverride.config("catalogJdbcUrl", MYSQL.getJdbcUrl()),
                ConfigOverride.config("catalogDbUser", MYSQL.getUsername()),
                ConfigOverride.config("catalogDbPassword", MYSQL.getPassword()),
                ConfigOverride.config("server.applicationConnectors[0].port", "0"),
                ConfigOverride.config("server.adminConnectors[0].port", "0"));
        try {
            app.before();
            Client client = ClientBuilder.newClient();
            try {
                request(client, "http://127.0.0.1:" + app.getLocalPort(), "POST", "/api/goods/LUNCH", null,
                        "yaml-create", GOODS, 201);
            } finally {
                client.close();
            }
        } finally {
            app.after();
        }
    }

    /**
     * 验证<code>created.path("grossAmountCent").asLong()</code> 等于 <code>2500</code>，<code>CacheKit.getCache(configuration.getDdd4jWeb().getIdempotencyCacheName())</code>为空。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void anonymousDefaultRunsCreateAddPayAndFind() throws Exception {
        DropwizardOrderConfiguration configuration = configuration(0);
        DropwizardTestSupport<DropwizardOrderConfiguration> app = application(configuration);
        try {
            app.before();
            Client client = ClientBuilder.newClient();
            try {
                String base = "http://127.0.0.1:" + app.getLocalPort();
                request(client, base, "POST", "/api/goods/LUNCH", null, "goods", GOODS, 201);
                JsonNode created = request(client, base, "POST", "/api/orders/T001", null, "order", ORDER, 201);
                assertEquals(2500, created.path("grossAmountCent").asLong());
                request(client, base, "POST", "/api/orders/T001/payment", null, "invalid",
                        "{\"paidAmountCent\":2400}", 409);
                JsonNode paid = request(client, base, "POST", "/api/orders/T001/payment", null, "paid",
                        "{\"paidAmountCent\":2500}", 200);
                assertEquals("CONFIRMED", paid.path("orderState").asText());
                JsonNode found = request(client, base, "GET", "/api/orders/T001", null, null, null, 200);
                assertEquals(1250, found.at("/items/0/unitPriceCent").asLong());
                assertEquals(2500, found.path("paidAmountCent").asLong());
            } finally {
                client.close();
            }
            assertNotNull(CacheKit.getCache(configuration.getDdd4jWeb().getIdempotencyCacheName()));
        } finally {
            app.after();
        }
        assertNull(CacheKit.getCache(configuration.getDdd4jWeb().getIdempotencyCacheName()));
    }

    /**
     * 在测试框架中执行 <code>configuration()</code>、<code>configuration.getDdd4jWeb()</code>、<code>application()</code>，验证该场景。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void explicitLegacyAuthenticationStillRequiresAndAcceptsBearer() throws Exception {
        DropwizardOrderConfiguration configuration = configuration(0);
        configuration.getDdd4jWeb().setDefaultAuthenticationMode(AuthenticationMode.REQUIRED);
        DropwizardTestSupport<DropwizardOrderConfiguration> app = application(configuration);
        try {
            app.before();
            Client client = ClientBuilder.newClient();
            try {
                String base = "http://127.0.0.1:" + app.getLocalPort();
                String body = GOODS;
                request(client, base, "POST", "/api/goods/LUNCH", null, "legacy-denied", body, 401);
                String token = request(client, base, "POST", "/api/auth/tokens/legacy-user", null, null, null, 200)
                        .path("data").path("token").asText();
                request(client, base, "POST", "/api/goods/LUNCH", token, "legacy-allowed", body, 201);
            } finally {
                client.close();
            }
        } finally {
            app.after();
        }
    }

    /**
     * 验证<code>second</code> 不等于 <code>first</code>，<code>CacheKit.getCache(existingName)</code> 与…为同一实例 <code>existing</code>。
     *
     * @throws Exception 装配运行测试所需的组件时失败
     */
    @Test
    void managedCachesAreUniqueAndPreserveAnExistingCache() throws Exception {
        String existingName = "ddd4j-web-idempotency";
        Object previous = CacheKit.getCache(existingName);
        if (Objects.isNull(previous)) {
            CacheKit.build(existingName, 300);
        }
        Object existing = CacheKit.getCache(existingName);
        try {
            String first = runAndClose();
            String second = runAndClose();
            assertNotEquals(first, second);
            assertNotEquals(existingName, first);
            assertSame(existing, CacheKit.getCache(existingName));
        } finally {
            if (Objects.isNull(previous)) {
                CacheKit.unregister(existingName);
            }
        }
    }

    /**
     * 验证被测调用 抛出 <code>Exception.class</code>，<code>CacheKit.getCache(configuration.getDdd4jWeb().getIdempotencyCacheName())</code>为空。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void failedNativeBootReleasesItsManagedCache() throws Exception {
        try (ServerSocket occupied = new ServerSocket(0)) {
            DropwizardOrderConfiguration configuration = configuration(occupied.getLocalPort());
            DropwizardTestSupport<DropwizardOrderConfiguration> app = application(configuration);
            try {
                assertThrows(Exception.class, app::before);
                assertNull(CacheKit.getCache(configuration.getDdd4jWeb().getIdempotencyCacheName()),
                        "failed server bind must stop its started managed cache");
            } finally {
                app.after();
            }
        }
    }

    /**
     * 验证<code>CacheKit.getCache(name)</code> 与…为同一实例 <code>callerCache</code>，<code>CacheKit.get(name, "caller-entry")</code> 等于 <code>"caller-loaded"</code>。
     *
     * @throws Exception 装配运行测试所需的组件时失败
     */
    @Test
    void shutdownPreservesCallerReplacementAndLoadingCache() throws Exception {
        DropwizardOrderConfiguration configuration = configuration(0);
        DropwizardTestSupport<DropwizardOrderConfiguration> app = application(configuration);
        String name;
        Cache<String, Object> callerCache;
        try {
            app.before();
            name = configuration.getDdd4jWeb().getIdempotencyCacheName();
            CacheKit.build(name, 300L);
            callerCache = CacheKit.getCache(name);
            CacheKit.put(name, "caller-entry", "untouched");
            CacheKit.buildWithLoader(name, key -> "caller-loaded", builder -> builder);
        } finally {
            app.after();
        }
        try {
            assertSame(callerCache, CacheKit.getCache(name));
            assertEquals("untouched", CacheKit.get(name, "caller-entry"));
            CacheKit.unregisterLocalIfOwned(name, callerCache);
            assertEquals("caller-loaded", CacheKit.get(name, "caller-entry"));
        } finally {
            CacheKit.unregister(name);
        }
    }

    /**
     * 验证<code>CacheKit.getCache(configuration.getDdd4jWeb().getIdempotencyCacheName())</code>为空。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void disabledIdempotencyDoesNotAllocateItsManagedCache() throws Exception {
        DropwizardOrderConfiguration configuration = configuration(0);
        configuration.getDdd4jWeb().setIdempotencyEnabled(false);
        DropwizardTestSupport<DropwizardOrderConfiguration> app = application(configuration);
        try {
            app.before();
            assertNull(CacheKit.getCache(configuration.getDdd4jWeb().getIdempotencyCacheName()));
            Client client = ClientBuilder.newClient();
            try {
                request(client, "http://127.0.0.1:" + app.getLocalPort(), "POST", "/api/goods/LUNCH", null,
                        "disabled-cache", GOODS, 201);
            } finally {
                client.close();
            }
        } finally {
            app.after();
        }
    }
}
