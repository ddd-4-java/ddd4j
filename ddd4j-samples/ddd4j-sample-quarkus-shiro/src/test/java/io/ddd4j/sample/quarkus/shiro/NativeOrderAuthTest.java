package io.ddd4j.sample.quarkus.shiro;

import io.ddd4j.sample.order.adapter.http.CatalogApi;
import io.ddd4j.sample.order.testkit.AuthSampleDriver;
import io.ddd4j.sample.order.testkit.SampleContractAssertions;
import io.ddd4j.sample.quarkus.shiro.rbac.InMemoryRoleRepository;
import io.ddd4j.sample.quarkus.shiro.rbac.RbacConfig;
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

import static io.ddd4j.sample.quarkus.shiro.CatalogTradeFixture.*;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实 native 引擎、共享订单 HTTP 和服务器执行线程清理验收。
 */
@QuarkusTest
class NativeOrderAuthTest {
    /**
     * 协调测试线程在指定执行阶段等待或继续的门闩。
     */
    static final java.util.concurrent.CountDownLatch held = new java.util.concurrent.CountDownLatch(1);
    /**
     * 用来控制测试线程退出等待或观察操作完成的同步门闩。
     */
    static final java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
    /**
     * 协调测试线程在指定执行阶段等待或继续的门闩。
     */
    static final java.util.concurrent.CountDownLatch canceled = new java.util.concurrent.CountDownLatch(1);
    /**
     * 供测试线程安全保存当前观察结果的引用。
     */
    static final AtomicReference<Optional<String>> observed = new AtomicReference<>();
    /**
     * 当前示例用来解析角色编辑和权限分配的配置。
     */
    @Inject
    RbacConfig rbac;
    /**
     * 保存示例角色定义的内存仓储。
     */
    @Inject
    InMemoryRoleRepository roles;
    /**
     * 保存测试数据的内存存储。
     */
    @Inject
    CatalogApi store;
    /**
     * 本地测试服务的基础地址。
     */
    @TestHTTPResource
    URI base;

    /**
     * 通过 <code>java.util.Objects.requireNonNull()</code> 取得当前测试需要的结果。
     *
     * @return 当前测试使用的结果，取自 <code>java.util.Objects.requireNonNull(observed.get(), "server completion observer must execute")</code>
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

    /**
     * 测试执行前执行 <code>seed()</code> 完成资源准备。
     */
    @BeforeEach
    void prepareGoods() {
        seed(store);
    }

    /**
     * 验证<code>completed()</code> 等于 <code>Optional.empty()</code>。
     */
    @Test
    void anonymousOrderWriteMustBeRejected() {
        given().contentType("application/json").body(ORDER).post(ORDERS + "anonymous").then().statusCode(401);
        assertEquals(Optional.empty(), completed());
    }

    /**
     * 验证<code>store.order(TENANT, driver.orderId).getBody().contains("CANCELLED")</code>为真，<code>completed()</code> 等于 <code>Optional.empty()</code>。
     */
    @Test
    void nativeAuthSharedBusinessDenialLogoutAndCleanup() {
        rbac.reset();
        roles.findByCode("user").orElseThrow().getPermissionCodes().add("order:pay");
        given().get("/auth/status").then().statusCode(200);
        Driver driver = new Driver();
        SampleContractAssertions.authentication("shiro", driver, "user", "123456", "u-user",
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
     * 调用 <code>java.net.http.HttpRequest.newBuilder()</code>、<code>base.resolve()</code>、<code>java.net.http.HttpClient.newHttpClient()</code> 执行当前夹具操作。
     *
     * @param token 从测试登录获得的会话凭据
     * @param id    当前要查找的测试记录标识
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
         * 调用 <code>request.getHeaderString()</code>、<code>held.countDown()</code>、<code>release.await()</code> 执行当前夹具操作。
         *
         * @param request 待交给被测服务处理的请求
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
         * 调用 <code>response.getStatus()</code>、<code>observed.set()</code>、<code>org.apache.shiro.util.ThreadContext.getSubject()</code> 执行当前夹具操作。
         *
         * @param request  待交给被测服务处理的请求
         * @param response 当前请求已收到的响应
         */
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
    @Priority(1001)
    public static class CleanupObserver implements jakarta.ws.rs.container.ContainerRequestFilter {
        /**
         * 调用 <code>request.getUriInfo()</code>、<code>Thread.currentThread()</code>、<code>request.getHeaderString()</code> 执行当前夹具操作。
         *
         * @param request 待交给被测服务处理的请求
         */
        @Override
        public void filter(ContainerRequestContext request) {
            String path = request.getUriInfo().getPath();
            String entered = Thread.currentThread().getName();
            boolean cancellation = "true".equals(request.getHeaderString("X-S03-Hold"));
            ((org.jboss.resteasy.reactive.server.spi.ResteasyReactiveContainerRequestContext) request)
                    .getServerRequestContext().registerCompletionCallback(failure -> {
                        boolean present = java.util.Objects.nonNull(org.apache.shiro.util.ThreadContext.getSubject());
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

    /**
     * 把共享鉴权断言转发到示例应用的调用驱动。
     */
    private final class Driver implements AuthSampleDriver {
        /**
         * 订单标识。
         */
        String orderId;

        /**
         * 读取 <code>response</code> 供当前测试使用。
         *
         * @param method 发给被测服务的 HTTP 方法
         * @param path   测试请求的资源路径或待访问文件路径
         * @param token  从测试登录获得的会话凭据
         * @param body   当前请求或响应中要读取的正文
         * @return 当前测试使用的结果，取自 <code>response</code>
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

        /**
         * 以传入的凭据启动认证会话。
         *
         * <p>测试夹具会调用 <code>Map.of("username", user, "password", password)</code>。</p>
         *
         * @param user     传给 <code>call("POST", "/auth/login", null, Map.of("username", user, "password", password)).path("token")</code>
         * @param password 已配置密码
         * @return 实际登录响应中的令牌
         */
        @Override
        public String login(String user, String password) {
            return call("POST", "/auth/login", null, Map.of("username", user, "password", password)).path("token");
        }

        /**
         * 通过已认证请求探测服务端识别出的登录主体。
         *
         * @param token 令牌
         * @return 实际受保护入口返回的主体；未登录/失效令牌抛 SecurityException
         */
        @Override
        public String authenticatedPrincipal(String token) {
            return call("GET", "/auth/me", token, null).path("data.userId");
        }

        /**
         * 访问当前鉴权契约中受权限约束的资源。
         *
         * <p>测试夹具会调用 <code>"order:cancel".equals(permission)</code>、<code>call("POST", ORDERS + orderId + "/cancel", token, null)</code>。</p>
         *
         * @param token      令牌
         * @param permission 权限
         * @return 根据执行路径返回<code>"unexpected"</code>、<code>response.path("orderState")</code>
         */
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

        /**
         * 结束由当前调用指定的认证会话。
         *
         * <p>测试夹具会调用 <code>call("POST", "/auth/logout", token, null)</code>。</p>
         *
         * @param token 令牌；实际退出入口必须使该令牌失效
         */
        @Override
        public void logout(String token) {
            call("POST", "/auth/logout", token, null);
        }

        /**
         * 访问测试中指定会失败的请求路径。
         *
         * <p>测试夹具会调用 <code>Map.of("paidAmountCent", 1)</code>。</p>
         *
         * @param token 令牌；调用已注入业务异常的受保护入口并保留 IllegalStateException
         */
        @Override
        public void failingRequest(String token) {
            call("POST", ORDERS + orderId + "/payment", token, Map.of("paidAmountCent", 1));
        }

        /**
         * 探测当前请求上下文里可读取的登录主体。
         *
         * <p>测试夹具会调用 <code>completed()</code>。</p>
         *
         * @return 实际返回<code>completed()</code>
         */
        @Override
        public Optional<String> contextPrincipal() {
            return completed();
        }
    }
}
