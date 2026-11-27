package io.ddd4j.sample.dropwizard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.cache.CacheKit;
import io.ddd4j.core.cache.Cache;
import io.ddd4j.web.core.auth.AuthenticationMode;
import io.dropwizard.server.DefaultServerFactory;
import io.dropwizard.jetty.HttpConnectorFactory;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.DropwizardTestSupport;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
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

    /** \u4e3a\u5f53\u524d\u96c6\u6210\u6d4b\u8bd5\u63d0\u4f9b\u72ec\u7acb MySQL \u670d\u52a1\u7684\u5bb9\u5668\u3002 */
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");
    /** \u5546\u54c1\u6d4b\u8bd5\u6240\u4f7f\u7528\u7684\u8def\u7531\u6216\u4e1a\u52a1\u7f16\u7801\u3002 */
    private static final String GOODS = "{\"spuName\":\"Lunch\",\"skuCode\":\"S1\",\"skuName\":\"Weekday\",\"salePriceCent\":1250,\"shopId\":7}\n";
    /** \u6d4b\u8bd5\u8ba2\u5355\u7684\u56fa\u5b9a\u6807\u8bc6\uff0c\u7528\u4e8e\u7ec4\u7ec7\u548c\u67e5\u8be2\u6d4b\u8bd5\u6570\u636e\u3002 */
    private static final String ORDER = "{\"shopId\":7,\"items\":[{\"skuCode\":\"S1\",\"quantity\":2}]}\n";

    /** \u8d1f\u8d23\u8f6c\u6362\u6d4b\u8bd5\u6216\u534f\u8bae\u6b63\u6587\u4e2d JSON \u6570\u636e\u7684 Jackson \u6620\u5c04\u5668\u3002 */
    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * \u8bfb\u53d6 <code>name</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>name</code>
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
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
     * \u8bfb\u53d6 <code>configuration</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @param port \u6d4b\u8bd5\u670d\u52a1\u76d1\u542c\u7684\u672c\u5730\u7aef\u53e3
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>configuration</code>
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
     * \u521b\u5efa <code>new DropwizardTestSupport&lt;&gt;</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @param configuration \u7528\u4e8e\u6784\u9020\u672c\u6b21\u8fd4\u56de\u7ed3\u679c\u7684 <code>DropwizardOrderConfiguration</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new DropwizardTestSupport&lt;&gt;(DropwizardOrderApplication.class, configuration)</code>
     */
    private static DropwizardTestSupport<DropwizardOrderConfiguration> application(DropwizardOrderConfiguration configuration) {
        return new DropwizardTestSupport<>(DropwizardOrderApplication.class, configuration);
    }

    /**
     * \u901a\u8fc7 <code>JSON.readTree()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param client \u7528\u4e8e\u53d1\u51fa\u6d4b\u8bd5\u8bf7\u6c42\u7684\u5ba2\u6237\u7aef
     * @param base \u4f20\u7ed9 <code>client.target()</code> \u7684 <code>String</code> \u8f93\u5165
     * @param method \u53d1\u7ed9\u88ab\u6d4b\u670d\u52a1\u7684 HTTP \u65b9\u6cd5
     * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
     * @param token \u4ece\u6d4b\u8bd5\u767b\u5f55\u83b7\u5f97\u7684\u4f1a\u8bdd\u51ed\u636e
     * @param key \u7528\u4e8e\u5728\u6d4b\u8bd5\u4e0a\u4e0b\u6587\u6216\u7f13\u5b58\u4e2d\u7d22\u5f15\u6570\u636e\u7684\u952e
     * @param body \u5f53\u524d\u8bf7\u6c42\u6216\u54cd\u5e94\u4e2d\u8981\u8bfb\u53d6\u7684\u6b63\u6587
     * @param status \u5f53\u524d HTTP \u6216\u4e1a\u52a1\u6d41\u7a0b\u7684\u72b6\u6001
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>JSON.readTree(actual)</code>
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u3001\u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
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
     * \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>ConfigOverride.config()</code>\u3001<code>MYSQL.getJdbcUrl()</code>\u3001<code>MYSQL.getUsername()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
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
     * \u9a8c\u8bc1<code>created.path("grossAmountCent").asLong()</code> \u7b49\u4e8e <code>2500</code>\uff0c<code>CacheKit.getCache(configuration.getDdd4jWeb().getIdempotencyCacheName())</code>\u4e3a\u7a7a\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
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
     * \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>configuration()</code>\u3001<code>configuration.getDdd4jWeb()</code>\u3001<code>application()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
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
     * \u9a8c\u8bc1<code>second</code> \u4e0d\u7b49\u4e8e <code>first</code>\uff0c<code>CacheKit.getCache(existingName)</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>existing</code>\u3002
     * @throws Exception \u88c5\u914d\u8fd0\u884c\u6d4b\u8bd5\u6240\u9700\u7684\u7ec4\u4ef6\u65f6\u5931\u8d25
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
     * \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>Exception.class</code>\uff0c<code>CacheKit.getCache(configuration.getDdd4jWeb().getIdempotencyCacheName())</code>\u4e3a\u7a7a\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
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
     * \u9a8c\u8bc1<code>CacheKit.getCache(name)</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>callerCache</code>\uff0c<code>CacheKit.get(name, "caller-entry")</code> \u7b49\u4e8e <code>"caller-loaded"</code>\u3002
     * @throws Exception \u88c5\u914d\u8fd0\u884c\u6d4b\u8bd5\u6240\u9700\u7684\u7ec4\u4ef6\u65f6\u5931\u8d25
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
     * \u9a8c\u8bc1<code>CacheKit.getCache(configuration.getDdd4jWeb().getIdempotencyCacheName())</code>\u4e3a\u7a7a\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
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
