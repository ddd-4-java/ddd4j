package io.ddd4j.sample.quarkus.satoken;

import io.ddd4j.sample.order.adapter.http.CatalogApi;
import io.ddd4j.sample.order.testkit.AuthSampleDriver;
import io.ddd4j.sample.order.testkit.SampleContractAssertions;
import io.ddd4j.sample.quarkus.satoken.rbac.InMemoryRoleRepository;
import io.ddd4j.sample.quarkus.satoken.rbac.RbacConfig;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static io.ddd4j.sample.quarkus.satoken.CatalogTradeFixture.*;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实 native 引擎、共享订单 HTTP 和服务器执行线程清理验收。
 */
@QuarkusTest
class NativeOrderAuthTest {
    /** \u534f\u8c03\u6d4b\u8bd5\u7ebf\u7a0b\u5728\u6307\u5b9a\u6267\u884c\u9636\u6bb5\u7b49\u5f85\u6216\u7ee7\u7eed\u7684\u95e8\u95e9\u3002 */
    static final java.util.concurrent.CountDownLatch held = new java.util.concurrent.CountDownLatch(1);
    /** \u7528\u6765\u63a7\u5236\u6d4b\u8bd5\u7ebf\u7a0b\u9000\u51fa\u7b49\u5f85\u6216\u89c2\u5bdf\u64cd\u4f5c\u5b8c\u6210\u7684\u540c\u6b65\u95e8\u95e9\u3002 */
    static final java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
    /** \u534f\u8c03\u6d4b\u8bd5\u7ebf\u7a0b\u5728\u6307\u5b9a\u6267\u884c\u9636\u6bb5\u7b49\u5f85\u6216\u7ee7\u7eed\u7684\u95e8\u95e9\u3002 */
    static final java.util.concurrent.CountDownLatch canceled = new java.util.concurrent.CountDownLatch(1);
    /** \u4f9b\u6d4b\u8bd5\u7ebf\u7a0b\u5b89\u5168\u4fdd\u5b58\u5f53\u524d\u89c2\u5bdf\u7ed3\u679c\u7684\u5f15\u7528\u3002 */
    static final AtomicReference<Optional<String>> observed = new AtomicReference<>();
    /** \u5f53\u524d\u793a\u4f8b\u7528\u6765\u89e3\u6790\u89d2\u8272\u7f16\u8f91\u548c\u6743\u9650\u5206\u914d\u7684\u914d\u7f6e\u3002 */
    @Inject
    RbacConfig rbac;
    /** \u4fdd\u5b58\u793a\u4f8b\u89d2\u8272\u5b9a\u4e49\u7684\u5185\u5b58\u4ed3\u50a8\u3002 */
    @Inject
    InMemoryRoleRepository roles;
    /** \u4fdd\u5b58\u6d4b\u8bd5\u6570\u636e\u7684\u5185\u5b58\u5b58\u50a8\u3002 */
    @Inject
    CatalogApi store;
    /** \u672c\u5730\u6d4b\u8bd5\u670d\u52a1\u7684\u57fa\u7840\u5730\u5740\u3002 */
    @TestHTTPResource
    URI base;

    /**
     * \u901a\u8fc7 <code>java.util.Objects.requireNonNull()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>java.util.Objects.requireNonNull(observed.get(), "server completion observer must execute")</code>
     */
    private static Optional<String> completed() {
        for (int i = 0; i < 200 && java.util.Objects.isNull(observed.get()); i++) {
            try {
                Thread.sleep(5);
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(failure);
            }
        }
        return java.util.Objects.requireNonNull(observed.get(), "server completion observer must execute");
    }

    /** \u6d4b\u8bd5\u6267\u884c\u524d\u6267\u884c <code>seed()</code> \u5b8c\u6210\u8d44\u6e90\u51c6\u5907\u3002 */
    @BeforeEach
    void prepareGoods() {
        seed(store);
    }

    /** \u9a8c\u8bc1<code>completed()</code> \u7b49\u4e8e <code>Optional.empty()</code>\u3002 */
    @Test
    void anonymousOrderWriteMustBeRejected() {
        given().contentType("application/json").body(ORDER).post(ORDERS + "anonymous").then().statusCode(401);
        assertEquals(Optional.empty(), completed());
    }

    /** \u9a8c\u8bc1<code>store.order(TENANT, driver.orderId).getBody().contains("CANCELLED")</code>\u4e3a\u771f\uff0c<code>completed()</code> \u7b49\u4e8e <code>Optional.empty()</code>\u3002 */
    @Test
    void nativeAuthSharedBusinessDenialLogoutAndCleanup() {
        rbac.reset();
        roles.findByCode("user").orElseThrow().getPermissionCodes().add("order:pay");
        given().get("/auth/status").then().statusCode(200);
        Driver driver = new Driver();
        SampleContractAssertions.authentication("satoken", driver, "user", "123456", "u-user",
                "order:pay", "order:cancel", "AWAITING_PAYMENT");
        given().contentType("application/json").body(Map.of("username", "admin", "password", "wrong"))
                .post("/auth/login").then().statusCode(500);
        String admin = new Driver().login("admin", "123456");
        given().header("Authorization", "Bearer " + admin).contentType("application/json")
                .post(ORDERS + driver.orderId + "/cancel").then().statusCode(200);
        assertTrue(store.order(TENANT, driver.orderId).getBody().contains("CANCELLED"));
        String checkout = "native-" + UUID.randomUUID();
        given().header("Authorization", "Bearer " + admin).contentType("application/json").body(ORDER)
                .post(ORDERS + checkout).then().statusCode(201);
        given().header("Authorization", "Bearer " + admin).contentType("application/json")
                .body(Map.of("paidAmountCent", 2500)).post(ORDERS + checkout + "/payment")
                .then().statusCode(200).body("orderState", equalTo("CONFIRMED"));
        verifyClientCancellation(admin, driver.orderId);
        assertEquals(Optional.empty(), completed());
    }

    /**
     * \u8c03\u7528 <code>java.net.http.HttpRequest.newBuilder()</code>\u3001<code>base.resolve()</code>\u3001<code>java.net.http.HttpClient.newHttpClient()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param token \u4ece\u6d4b\u8bd5\u767b\u5f55\u83b7\u5f97\u7684\u4f1a\u8bdd\u51ed\u636e
     * @param id \u5f53\u524d\u8981\u67e5\u627e\u7684\u6d4b\u8bd5\u8bb0\u5f55\u6807\u8bc6
     */
    private void verifyClientCancellation(String token, String id) {
        var request = java.net.http.HttpRequest.newBuilder(base.resolve(ORDERS + id))
                .header("Authorization", "Bearer " + token).header("X-Tenant-Id", TENANT).header("X-S03-Hold", "true").GET().build();
        var pending = java.net.http.HttpClient.newHttpClient().sendAsync(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        try {
            assertTrue(held.await(10, java.util.concurrent.TimeUnit.SECONDS));
            pending.cancel(true);
            observed.set(null);
            release.countDown();
            assertTrue(canceled.await(10, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(Optional.empty(), completed());
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(failure);
        } finally {
            release.countDown();
        }
    }

    /**
     * 测试在真实业务请求已建立原生上下文后暂停，客户端断开后放行；不参与清理。
     */
    @Provider
    @Priority(1002)
    public static class CancellationGate implements jakarta.ws.rs.container.ContainerRequestFilter {
        /**
         * \u8c03\u7528 <code>request.getHeaderString()</code>\u3001<code>held.countDown()</code>\u3001<code>release.await()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
         * @param request \u5f85\u4ea4\u7ed9\u88ab\u6d4b\u670d\u52a1\u5904\u7406\u7684\u8bf7\u6c42
         */
        @Override
        public void filter(ContainerRequestContext request) {
            if ("true".equals(request.getHeaderString("X-S03-Hold"))) {
                held.countDown();
                try {
                    assertTrue(release.await(10, java.util.concurrent.TimeUnit.SECONDS));
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(failure);
                }
            }
        }
    }

    /**
     * 被认证过滤器提前拒绝的请求也执行此只读响应观察器。
     */
    @Provider
    @Priority(999)
    public static class RejectedResponseObserver implements ContainerResponseFilter {
        /**
         * \u8c03\u7528 <code>response.getStatus()</code>\u3001<code>observed.set()</code>\u3001<code>cn.dev33.satoken.SaManager.getSaTokenContext()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
         * @param request \u5f85\u4ea4\u7ed9\u88ab\u6d4b\u670d\u52a1\u5904\u7406\u7684\u8bf7\u6c42
         * @param response \u5f53\u524d\u8bf7\u6c42\u5df2\u6536\u5230\u7684\u54cd\u5e94
         */
        @Override
        public void filter(ContainerRequestContext request, ContainerResponseContext response) {
            if (response.getStatus() == 401 || response.getStatus() == 403) {
                observed.set(cn.dev33.satoken.SaManager.getSaTokenContext().isValid() ? Optional.of("native-context-present") : Optional.empty());
            }
        }
    }

    /**
     * 只读 native 完成钩子，按注册顺序位于产品清理之后。
     */
    @Provider
    @Priority(1001)
    public static class CleanupObserver implements jakarta.ws.rs.container.ContainerRequestFilter {
        /**
         * \u8c03\u7528 <code>request.getUriInfo()</code>\u3001<code>Thread.currentThread()</code>\u3001<code>request.getHeaderString()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
         * @param request \u5f85\u4ea4\u7ed9\u88ab\u6d4b\u670d\u52a1\u5904\u7406\u7684\u8bf7\u6c42
         */
        @Override
        public void filter(ContainerRequestContext request) {
            String path = request.getUriInfo().getPath();
            String entered = Thread.currentThread().getName();
            boolean cancellation = "true".equals(request.getHeaderString("X-S03-Hold"));
            ((org.jboss.resteasy.reactive.server.spi.ResteasyReactiveContainerRequestContext) request)
                    .getServerRequestContext().registerCompletionCallback(failure -> {
                        boolean present = cn.dev33.satoken.SaManager.getSaTokenContext().isValid();
                        org.slf4j.LoggerFactory.getLogger(CleanupObserver.class).info(
                                "S03 completion {} requestThread={} completionThread={} contextPresent={} failure={}",
                                path, entered, Thread.currentThread().getName(), present, java.util.Objects.nonNull(failure));
                        observed.set(present ? Optional.of("native-context-present") : Optional.empty());
                        if (cancellation) {
                            canceled.countDown();
                        }
                    });
        }
    }

    /** \u628a\u5171\u4eab\u9274\u6743\u65ad\u8a00\u8f6c\u53d1\u5230\u793a\u4f8b\u5e94\u7528\u7684\u8c03\u7528\u9a71\u52a8\u3002 */
    private final class Driver implements AuthSampleDriver {
        /** \u8ba2\u5355\u6807\u8bc6\u3002 */
        String orderId;

        /**
         * \u8bfb\u53d6 <code>response</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
         * @param method \u53d1\u7ed9\u88ab\u6d4b\u670d\u52a1\u7684 HTTP \u65b9\u6cd5
         * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
         * @param token \u4ece\u6d4b\u8bd5\u767b\u5f55\u83b7\u5f97\u7684\u4f1a\u8bdd\u51ed\u636e
         * @param body \u5f53\u524d\u8bf7\u6c42\u6216\u54cd\u5e94\u4e2d\u8981\u8bfb\u53d6\u7684\u6b63\u6587
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>response</code>
         */
        private io.restassured.response.Response call(String method, String path, String token, Object body) {
            observed.set(null);
            var request = given().contentType("application/json");
            if (java.util.Objects.nonNull(token)) {
                request.header("Authorization", "Bearer " + token);
            }
            if (java.util.Objects.nonNull(body)) {
                request.body(body);
            }
            var response = request.request(method, path);
            completed();
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                throw new SecurityException(response.asString());
            }
            if (response.statusCode() >= 400) {
                throw new IllegalStateException(response.asString());
            }
            return response;
        }

        /** {@inheritDoc} */
        @Override
        public String login(String user, String password) {
            return call("POST", "/auth/login", null, Map.of("username", user, "password", password)).path("token");
        }

        /** {@inheritDoc} */
        @Override
        public String authenticatedPrincipal(String token) {
            return call("GET", "/auth/me", token, null).path("data.userId");
        }

        /** {@inheritDoc} */
        @Override
        public String authorizedResource(String token, String permission) {
            if ("order:cancel".equals(permission)) {
                try {
                    call("POST", ORDERS + orderId + "/cancel", token, null);
                } finally {
                    assertTrue(store.order(TENANT, orderId).getBody().contains("AWAITING_PAYMENT"));
                }
                return "unexpected";
            }
            orderId = "auth-" + UUID.randomUUID();
            var response = call("POST", ORDERS + orderId, token, ORDER);
            assertEquals(200, store.order(TENANT, orderId).getStatus());
            return response.path("orderState");
        }

        /** {@inheritDoc} */
        @Override
        public void logout(String token) {
            call("POST", "/auth/logout", token, null);
        }

        /** {@inheritDoc} */
        @Override
        public void failingRequest(String token) {
            call("POST", ORDERS + orderId + "/payment", token, Map.of("paidAmountCent", 1));
        }

        /** {@inheritDoc} */
        @Override
        public Optional<String> contextPrincipal() {
            return completed();
        }
    }
}
