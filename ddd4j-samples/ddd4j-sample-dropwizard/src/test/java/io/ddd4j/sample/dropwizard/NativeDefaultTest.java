package io.ddd4j.sample.dropwizard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.cache.CacheKit;
import io.ddd4j.core.cache.Cache;
import io.ddd4j.web.core.auth.AuthenticationMode;
import io.dropwizard.core.server.DefaultServerFactory;
import io.dropwizard.jetty.HttpConnectorFactory;
import io.dropwizard.testing.DropwizardTestSupport;
import io.dropwizard.testing.ConfigOverride;
import org.junit.jupiter.api.Test;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.ServerSocket;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 使用 Dropwizard 2 原生服务器验证默认订单入口和本实例缓存生命周期。 */
class NativeDefaultTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void stockYamlRunsAnonymousOrderThroughNativeHttp() throws Exception {
        DropwizardTestSupport<DropwizardOrderConfiguration> app = new DropwizardTestSupport<>(
                DropwizardOrderApplication.class, "src/main/resources/dropwizard-order.yml",
                ConfigOverride.config("server.applicationConnectors[0].port", "0"),
                ConfigOverride.config("server.adminConnectors[0].port", "0"));
        try {
            app.before();
            Client client = ClientBuilder.newClient();
            try {
                request(client, "http://127.0.0.1:" + app.getLocalPort(), "POST", "/api/orders", null,
                        "yaml-create", "{\"orderNo\":\"YAML-" + UUID.randomUUID()
                                + "\",\"buyerId\":\"b\",\"buyerName\":\"Alice\"}", 201);
            } finally {
                client.close();
            }
        } finally {
            app.after();
        }
    }

    @Test
    void anonymousDefaultRunsCreateAddPayAndFind() throws Exception {
        DropwizardOrderConfiguration configuration = configuration(0);
        DropwizardTestSupport<DropwizardOrderConfiguration> app = application(configuration);
        try {
            app.before();
            Client client = ClientBuilder.newClient();
            try {
                String base = "http://127.0.0.1:" + app.getLocalPort();
                String id = request(client, base, "POST", "/api/orders", null, "create",
                        "{\"orderNo\":\"NATIVE-" + UUID.randomUUID() + "\",\"buyerId\":\"b\",\"buyerName\":\"Alice\"}", 201)
                        .path("data").path("id").asText();
                JsonNode line = request(client, base, "POST", "/api/orders/" + id + "/lines", null, "line",
                        "{\"goodsId\":\"g\",\"goodsName\":\"Book\",\"quantity\":2,\"unitPrice\":12.5}", 200);
                assertEquals(0, new java.math.BigDecimal("25.00").compareTo(line.path("data").path("totalAmount").decimalValue()));
                request(client, base, "POST", "/api/orders/" + id + "/pay", null, null, null, 400);
                JsonNode paid = request(client, base, "POST", "/api/orders/" + id + "/pay", null, "pay", null, 200);
                assertEquals("PAID", paid.path("data").path("status").asText());
                JsonNode found = request(client, base, "GET", "/api/orders/" + id, null, null, null, 200);
                assertEquals(id, found.path("data").path("id").asText());
                assertEquals("PAID", found.path("data").path("status").asText());
            } finally {
                client.close();
            }
            assertNotNull(CacheKit.getCache(configuration.getDdd4jWeb().getIdempotencyCacheName()));
        } finally {
            app.after();
        }
        assertNull(CacheKit.getCache(configuration.getDdd4jWeb().getIdempotencyCacheName()));
    }

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
                String body = "{\"orderNo\":\"LEGACY\",\"buyerId\":\"b\",\"buyerName\":\"Alice\"}";
                request(client, base, "POST", "/api/orders", null, "legacy-denied", body, 401);
                String token = request(client, base, "POST", "/api/auth/tokens/legacy-user", null, null, null, 200)
                        .path("data").path("token").asText();
                request(client, base, "POST", "/api/orders", token, "legacy-allowed", body, 201);
            } finally {
                client.close();
            }
        } finally {
            app.after();
        }
    }

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
                request(client, "http://127.0.0.1:" + app.getLocalPort(), "POST", "/api/orders", null,
                        "disabled-cache", "{\"orderNo\":\"NO-CACHE\",\"buyerId\":\"b\",\"buyerName\":\"Alice\"}", 201);
            } finally {
                client.close();
            }
        } finally {
            app.after();
        }
    }

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

    private static DropwizardOrderConfiguration configuration(int port) {
        DropwizardOrderConfiguration configuration = new DropwizardOrderConfiguration();
        DefaultServerFactory server = (DefaultServerFactory) configuration.getServerFactory();
        ((HttpConnectorFactory) server.getApplicationConnectors().get(0)).setPort(port);
        ((HttpConnectorFactory) server.getAdminConnectors().get(0)).setPort(0);
        return configuration;
    }

    private static DropwizardTestSupport<DropwizardOrderConfiguration> application(DropwizardOrderConfiguration configuration) {
        return new DropwizardTestSupport<>(DropwizardOrderApplication.class, configuration);
    }

    private static JsonNode request(Client client, String base, String method, String path, String token,
                                    String key, String body, int status) throws Exception {
        Invocation.Builder request = client.target(base + path).request();
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
}
