package io.ddd4j.sample.quarkus.shiro;

import java.util.NoSuchElementException;
import com.google.common.collect.ImmutableMap;
import io.ddd4j.sample.order.adapter.http.CatalogApi;
import io.ddd4j.sample.order.testkit.AuthSampleDriver;
import io.ddd4j.sample.order.testkit.SampleContractAssertions;
import io.ddd4j.sample.quarkus.shiro.rbac.InMemoryRoleRepository;
import io.ddd4j.sample.quarkus.shiro.rbac.RbacConfig;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import javax.annotation.Priority;
import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.ext.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static io.ddd4j.sample.quarkus.shiro.CatalogTradeFixture.*;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实 native 引擎、共享订单 HTTP 和服务器执行线程清理验收。
 */
@QuarkusTest
class NativeOrderAuthTest {
    static final java.util.concurrent.CountDownLatch held = new java.util.concurrent.CountDownLatch(1);
    static final java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
    static final java.util.concurrent.CountDownLatch canceled = new java.util.concurrent.CountDownLatch(1);
    static final AtomicReference<Optional<String>> observed = new AtomicReference<>();
    @Inject
    RbacConfig rbac;
    @Inject
    InMemoryRoleRepository roles;
    @Inject
    CatalogApi store;
    @TestHTTPResource
    URI base;

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

    @BeforeEach
    void prepareGoods() {
        seed(store);
    }

    @Test
    void anonymousOrderWriteMustBeRejected() {
        given().contentType("application/json").body(ORDER).post(ORDERS + "anonymous").then().statusCode(401);
        assertEquals(Optional.empty(), completed());
    }

    @Test
    void nativeAuthSharedBusinessDenialLogoutAndCleanup() {
        rbac.reset();
        roles.findByCode("user").orElseThrow(NoSuchElementException::new).getPermissionCodes().add("order:pay");
        given().get("/auth/status").then().statusCode(200);
        Driver driver = new Driver();
        SampleContractAssertions.authentication("shiro", driver, "user", "123456", "u-user",
                "order:pay", "order:cancel", "AWAITING_PAYMENT");
        given().contentType("application/json").body(ImmutableMap.of("username", "admin", "password", "wrong"))
                .post("/auth/login").then().statusCode(500);
        String admin = new Driver().login("admin", "123456");
        given().header("Authorization", "Bearer " + admin).contentType("application/json")
                .post(ORDERS + driver.orderId + "/cancel").then().statusCode(200);
        assertTrue(store.order(TENANT, driver.orderId).getBody().contains("CANCELLED"));
        String checkout = "native-" + UUID.randomUUID();
        given().header("Authorization", "Bearer " + admin).contentType("application/json").body(ORDER)
                .post(ORDERS + checkout).then().statusCode(201);
        given().header("Authorization", "Bearer " + admin).contentType("application/json")
                .body(ImmutableMap.of("paidAmountCent", 2500)).post(ORDERS + checkout + "/payment")
                .then().statusCode(200).body("orderState", equalTo("CONFIRMED"));
        verifyClientCancellation(admin, driver.orderId);
        assertEquals(Optional.empty(), completed());
    }

    private void verifyClientCancellation(String token, String id) {
        io.ddd4j.sample.order.testkit.http.HttpRequest request = io.ddd4j.sample.order.testkit.http.HttpRequest.newBuilder(base.resolve(ORDERS + id))
                .header("Authorization", "Bearer " + token).header("X-Tenant-Id", TENANT).header("X-S03-Hold", "true").GET().build();
        java.util.concurrent.CompletableFuture<io.ddd4j.sample.order.testkit.http.HttpResponse<java.lang.String>> pending = io.ddd4j.sample.order.testkit.http.HttpClient.newHttpClient().sendAsync(request, io.ddd4j.sample.order.testkit.http.HttpResponse.BodyHandlers.ofString());
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
    public static class CancellationGate implements javax.ws.rs.container.ContainerRequestFilter {
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
        @Override
        public void filter(ContainerRequestContext request, ContainerResponseContext response) {
            if (response.getStatus() == 401 || response.getStatus() == 403) {
                observed.set(java.util.Objects.nonNull(org.apache.shiro.util.ThreadContext.getSubject()) ? Optional.of("native-context-present") : Optional.empty());
            }
        }
    }

    /**
     * 只读 native 完成钩子，按注册顺序位于产品清理之后。
     */
    @Provider
    @Priority(999)
    public static class CleanupObserver implements ContainerResponseFilter {
        @Override
        public void filter(ContainerRequestContext request, ContainerResponseContext response) {
            String path = request.getUriInfo().getPath();
            String entered = Thread.currentThread().getName();
            boolean cancellation = "true".equals(request.getHeaderString("X-S03-Hold"));
                        boolean present = java.util.Objects.nonNull(org.apache.shiro.util.ThreadContext.getSubject());
                        org.slf4j.LoggerFactory.getLogger(CleanupObserver.class).info(
                                "S03 completion {} requestThread={} completionThread={} contextPresent={} failure={}",
                                path, entered, Thread.currentThread().getName(), present, response.getStatus() >= 500);
                        observed.set(present ? Optional.of("native-context-present") : Optional.empty());
                        if (cancellation) {
                            canceled.countDown();
                        }
        }
    }

    private final class Driver implements AuthSampleDriver {
        String orderId;

        private io.restassured.response.Response call(String method, String path, String token, Object body) {
            observed.set(null);
            io.restassured.specification.RequestSpecification request = given().contentType("application/json");
            if (java.util.Objects.nonNull(token)) {
                request.header("Authorization", "Bearer " + token);
            }
            if (java.util.Objects.nonNull(body)) {
                request.body(body);
            }
            io.restassured.response.Response response = request.request(method, path);
            completed();
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                throw new SecurityException(response.asString());
            }
            if (response.statusCode() >= 400) {
                throw new IllegalStateException(response.asString());
            }
            return response;
        }

        @Override
        public String login(String user, String password) {
            return call("POST", "/auth/login", null, ImmutableMap.of("username", user, "password", password)).path("token");
        }

        @Override
        public String authenticatedPrincipal(String token) {
            return call("GET", "/auth/me", token, null).path("data.userId");
        }

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
            io.restassured.response.Response response = call("POST", ORDERS + orderId, token, ORDER);
            assertEquals(200, store.order(TENANT, orderId).getStatus());
            return response.path("orderState");
        }

        @Override
        public void logout(String token) {
            call("POST", "/auth/logout", token, null);
        }

        @Override
        public void failingRequest(String token) {
            call("POST", ORDERS + orderId + "/payment", token, ImmutableMap.of("paidAmountCent", 1));
        }

        @Override
        public Optional<String> contextPrincipal() {
            return completed();
        }
    }
}
