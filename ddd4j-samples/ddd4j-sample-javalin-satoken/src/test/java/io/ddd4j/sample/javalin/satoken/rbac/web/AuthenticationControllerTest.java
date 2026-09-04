package io.ddd4j.sample.javalin.satoken.rbac.web;

import io.ddd4j.sample.javalin.satoken.TestSupport;
import io.javalin.Javalin;
import org.junit.jupiter.api.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link AuthenticationController} 认证路由集成测试（Javalin + Sa-Token，random port）。
 *
 * <p>完整覆盖 AuthenticationController.routes() 注册的所有端点：
 * <ul>
 *   <li>登录/登出：POST /auth/login, POST /auth/logout</li>
 *   <li>当前用户：GET /auth/me</li>
 *   <li>登录状态：GET /auth/status</li>
 *   <li>踢人下线：POST /auth/kickout</li>
 *   <li>角色鉴权检查：POST /auth/check/role</li>
 *   <li>权限鉴权检查：POST /auth/check/permission</li>
 *   <li>角色保护端点：GET /auth/admin, GET /auth/manager</li>
 *   <li>权限保护端点：GET /auth/users, POST /auth/orders/{id}/pay</li>
 *   <li>组合鉴权：DELETE /auth/users/{id}</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@org.junit.jupiter.api.parallel.Execution(org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD)
class AuthenticationControllerTest {

    private static Javalin app;
    private static HttpClient httpClient;
    private static String baseUrl;

    @BeforeAll
    void startServer() {
        app = TestSupport.start();
        baseUrl = "http://localhost:" + app.port();
        httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @AfterAll
    void stopServer() {
        if (Objects.nonNull(app)) {
            app.stop();
        }
    }

    @BeforeEach
    void resetServerState() {
        app.stop();
        app = TestSupport.start();
        baseUrl = "http://localhost:" + app.port();
    }

    // ------------------- HTTP 助手 -------------------

    private HttpResponse<String> postJson(String path, String body) throws Exception {
        return httpClient.send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return httpClient.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> delete(String path) throws Exception {
        return httpClient.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).DELETE().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String login(String username, String password) throws Exception {
        HttpResponse<String> r = postJson("/auth/login",
                "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
        assertEquals(200, r.statusCode(), "login failed: " + r.body());
        int idx = r.body().indexOf("\"token\":\"");
        if (idx < 0) throw new IllegalStateException("no token in body: " + r.body());
        int start = idx + 9;
        int end = r.body().indexOf('"', start);
        return r.body().substring(start, end);
    }

    private HttpRequest.Builder authReq(String token, String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path)).header("satoken", token);
    }

    private HttpResponse<String> authGet(String token, String path) throws Exception {
        return httpClient.send(authReq(token, path).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> authPost(String token, String path, String body) throws Exception {
        return httpClient.send(authReq(token, path)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> authDelete(String token, String path) throws Exception {
        return httpClient.send(authReq(token, path).DELETE().build(), HttpResponse.BodyHandlers.ofString());
    }

    // =================== 登录 ===================

    @Test
    void login_validCredentials_shouldReturnToken() throws Exception {
        String token = login("admin", "admin");
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void login_wrongPassword_shouldReturn401() throws Exception {
        HttpResponse<String> r = postJson("/auth/login",
                "{\"username\":\"admin\",\"password\":\"wrong\"}");
        assertEquals(401, r.statusCode());
    }

    @Test
    void login_disabledUser_shouldReturn401() throws Exception {
        HttpResponse<String> r = postJson("/auth/login",
                "{\"username\":\"disabled\",\"password\":\"disabled\"}");
        assertEquals(401, r.statusCode());
    }

    @Test
    void login_nonExistentUser_shouldReturn401() throws Exception {
        HttpResponse<String> r = postJson("/auth/login",
                "{\"username\":\"ghost\",\"password\":\"ghost\"}");
        assertEquals(401, r.statusCode());
    }

    // =================== 登出 ===================

    @Test
    void logout_authenticated_shouldReturn200() throws Exception {
        String token = login("admin", "admin");
        HttpResponse<String> r = authPost(token, "/auth/logout", "");
        assertEquals(200, r.statusCode());
    }

    @Test
    void logout_unauthenticated_shouldReturn401() throws Exception {
        HttpResponse<String> r = postJson("/auth/logout", "");
        assertEquals(401, r.statusCode());
    }

    // =================== 当前用户 ===================

    @Test
    void me_authenticated_shouldReturnUserInfo() throws Exception {
        String token = login("admin", "admin");
        HttpResponse<String> r = authGet(token, "/auth/me");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"authenticated\":true"));
        assertTrue(r.body().contains("admin"));
    }

    @Test
    void me_unauthenticated_shouldReturnNotAuthenticated() throws Exception {
        HttpResponse<String> r = get("/auth/me");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"authenticated\":false"));
    }

    // =================== 登录状态 ===================

    @Test
    void status_authenticated_shouldReturnLoginTrue() throws Exception {
        String token = login("admin", "admin");
        HttpResponse<String> r = authGet(token, "/auth/status");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"login\":true"));
    }

    @Test
    void status_unauthenticated_shouldReturnLoginFalse() throws Exception {
        HttpResponse<String> r = get("/auth/status");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"login\":false"));
    }

    // =================== 踢人下线 ===================

    @Test
    void kickout_shouldReturn200() throws Exception {
        HttpResponse<String> r = postJson("/auth/kickout", "{\"userId\":\"10002\"}");
        assertEquals(200, r.statusCode());
    }

    // =================== 角色鉴权 ===================

    @Test
    void checkRole_adminHasAdminRole_shouldReturnTrue() throws Exception {
        String token = login("admin", "admin");
        HttpResponse<String> r = authPost(token, "/auth/check/role", "{\"role\":\"admin\"}");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"has\":true"));
    }

    @Test
    void checkRole_userLacksAdminRole_shouldReturnFalse() throws Exception {
        String token = login("user", "user");
        HttpResponse<String> r = authPost(token, "/auth/check/role", "{\"role\":\"admin\"}");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"has\":false"));
    }

    @Test
    void checkRole_unauthenticated_shouldReturn401() throws Exception {
        HttpResponse<String> r = postJson("/auth/check/role", "{\"role\":\"admin\"}");
        assertEquals(401, r.statusCode());
    }

    @Test
    void adminArea_asAdmin_shouldReturn200() throws Exception {
        String token = login("admin", "admin");
        HttpResponse<String> r = authGet(token, "/auth/admin");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("admin area accessed"));
    }

    @Test
    void adminArea_asUser_shouldReturn403() throws Exception {
        String token = login("user", "user");
        HttpResponse<String> r = authGet(token, "/auth/admin");
        assertEquals(403, r.statusCode());
    }

    @Test
    void adminArea_unauthenticated_shouldReturn401() throws Exception {
        HttpResponse<String> r = get("/auth/admin");
        assertEquals(401, r.statusCode());
    }

    @Test
    void managerArea_asAdmin_shouldReturn403() throws Exception {
        String token = login("admin", "admin");
        HttpResponse<String> r = authGet(token, "/auth/manager");
        assertEquals(403, r.statusCode());
    }

    @Test
    void managerArea_unauthenticated_shouldReturn401() throws Exception {
        HttpResponse<String> r = get("/auth/manager");
        assertEquals(401, r.statusCode());
    }

    // =================== 权限鉴权 ===================

    @Test
    void checkPermission_adminHasOrderPay_shouldReturnTrue() throws Exception {
        String token = login("admin", "admin");
        HttpResponse<String> r = authPost(token, "/auth/check/permission", "{\"permission\":\"order:pay\"}");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"has\":true"));
    }

    @Test
    void checkPermission_userLacksOrderPay_shouldReturnFalse() throws Exception {
        String token = login("user", "user");
        HttpResponse<String> r = authPost(token, "/auth/check/permission", "{\"permission\":\"order:pay\"}");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"has\":false"));
    }

    @Test
    void checkPermission_unauthenticated_shouldReturn401() throws Exception {
        HttpResponse<String> r = postJson("/auth/check/permission", "{\"permission\":\"order:pay\"}");
        assertEquals(401, r.statusCode());
    }

    @Test
    void usersList_asAdmin_shouldReturn200() throws Exception {
        String token = login("admin", "admin");
        HttpResponse<String> r = authGet(token, "/auth/users");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("user list accessed"));
    }

    @Test
    void usersList_asUser_shouldReturn200() throws Exception {
        String token = login("user", "user");
        HttpResponse<String> r = authGet(token, "/auth/users");
        assertEquals(200, r.statusCode());
    }

    @Test
    void usersList_unauthenticated_shouldReturn401() throws Exception {
        HttpResponse<String> r = get("/auth/users");
        assertEquals(401, r.statusCode());
    }

    @Test
    void orderPay_asAdmin_shouldReturn200() throws Exception {
        String token = login("admin", "admin");
        HttpResponse<String> r = authPost(token, "/auth/orders/ORD-001/pay", "");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("order paid"));
        assertTrue(r.body().contains("ORD-001"));
    }

    @Test
    void orderPay_asUser_shouldReturn403() throws Exception {
        String token = login("user", "user");
        HttpResponse<String> r = authPost(token, "/auth/orders/ORD-001/pay", "");
        assertEquals(403, r.statusCode());
    }

    @Test
    void orderPay_unauthenticated_shouldReturn401() throws Exception {
        HttpResponse<String> r = postJson("/auth/orders/ORD-001/pay", "");
        assertEquals(401, r.statusCode());
    }

    // =================== 组合鉴权 ===================

    @Test
    void deleteUser_asAdmin_shouldReturn200() throws Exception {
        String token = login("admin", "admin");
        HttpResponse<String> r = authDelete(token, "/auth/users/10002");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"success\":true"));
    }

    @Test
    void deleteUser_asUser_shouldReturn403() throws Exception {
        String token = login("user", "user");
        HttpResponse<String> r = authDelete(token, "/auth/users/10001");
        assertEquals(403, r.statusCode());
    }

    @Test
    void deleteUser_unauthenticated_shouldReturn401() throws Exception {
        HttpResponse<String> r = delete("/auth/users/10001");
        assertEquals(401, r.statusCode());
    }
}
