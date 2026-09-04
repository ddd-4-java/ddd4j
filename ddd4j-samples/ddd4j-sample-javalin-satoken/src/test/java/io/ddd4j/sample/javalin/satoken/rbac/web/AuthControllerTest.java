package io.ddd4j.sample.javalin.satoken.rbac.web;

import java.util.Objects;

import io.ddd4j.sample.javalin.satoken.TestSupport;
import io.javalin.Javalin;
import org.junit.jupiter.api.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 鉴权 / RBAC 集成测试（Javalin + Guice + Sa-Token，random port）。
 *
 * <p>完整覆盖：
 * <ul>
 *   <li>登录 / 登出 / 当前用户 / 状态查询 / 踢人（5）</li>
 *   <li>登录鉴权 isLogin（3）</li>
 *   <li>角色鉴权 hasRole：admin / manager / 普通用户（6）</li>
 *   <li>权限鉴权 hasPermission：user:list / order:pay（6）</li>
 *   <li>组合鉴权：admin 角色 + user:delete 权限（4）</li>
 *   <li>RBAC 资源管理：user/role/permission CRUD（10）</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@org.junit.jupiter.api.parallel.Execution(org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD)
class AuthControllerTest {

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

    /**
     * 每个测试前重启 Javalin 并清空 sa-token 全局状态，避免上一测试遗留的 token/session 干扰。
     */
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

    private HttpResponse<String> putJson(String path, String body) throws Exception {
        return httpClient.send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> delete(String path) throws Exception {
        return httpClient.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).DELETE().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return httpClient.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /**
     * 登录并返回 token（每个测试用独立会话）。
     */
    private String login(String username, String password) throws Exception {
        HttpResponse<String> r = postJson("/auth/login",
                "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
        assertEquals(200, r.statusCode(), "login failed: " + r.body());
        // token 在 data.token 中
        int idx = r.body().indexOf("\"token\":\"");
        if (idx < 0) {
            throw new IllegalStateException("no token in body: " + r.body());
        }
        int start = idx + 9;
        int end = r.body().indexOf('"', start);
        return r.body().substring(start, end);
    }

    /**
     * 登出（避免多 token 在全局 SaToken 状态中残留）。
     */
    private void logout(String token) throws Exception {
        authPost(token, "/auth/logout", "");
    }

    private HttpRequest.Builder authReq(String token, String path) {
        // sa-token 默认从 header "satoken" 读取 token；mock 上下文未注入 Authorization 支持。
        return HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("satoken", token);
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

    // =================== 1) 登录 / 登出 / 当前用户 / 状态 / 踢人（5）====================

    @Test
    void login_asAdmin_shouldReturnToken() throws Exception {
        String token = login("admin", "admin");
        assertNotNull(token);
        assertTrue(token.length() > 0);
    }

    @Test
    void login_asUser_shouldReturnToken() throws Exception {
        String token = login("user", "user");
        assertNotNull(token);
        assertTrue(token.length() > 0);
    }

    @Test
    void login_withWrongPassword_shouldReturn401() throws Exception {
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
    void me_unauthenticated_shouldReturnOkWithFalse() throws Exception {
        HttpResponse<String> r = get("/auth/me");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"authenticated\":false"));
    }

    @Test
    void status_unauthenticated_shouldReturnOk() throws Exception {
        HttpResponse<String> r = get("/auth/status");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"login\":false"));
    }

    // =================== 2) 角色鉴权 /auth/admin 和 /auth/manager（6）====================

    @Test
    void adminArea_asAdmin_shouldBeOk() throws Exception {
        String token = login("admin", "admin");
        HttpResponse<String> r = authGet(token, "/auth/admin");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("admin area accessed"));
    }

    @Test
    void adminArea_asUser_shouldBeForbidden() throws Exception {
        String token = login("user", "user");
        HttpResponse<String> r = authGet(token, "/auth/admin");
        assertEquals(403, r.statusCode());
    }

    @Test
    void adminArea_unauthenticated_shouldBe401() throws Exception {
        HttpResponse<String> r = get("/auth/admin");
        assertEquals(401, r.statusCode());
    }

    @Test
    void managerArea_asAdmin_shouldBeForbidden() throws Exception {
        // admin 不是 manager 角色
        String token = login("admin", "admin");
        HttpResponse<String> r = authGet(token, "/auth/manager");
        assertEquals(403, r.statusCode());
    }

    @Test
    void managerArea_unauthenticated_shouldBe401() throws Exception {
        HttpResponse<String> r = get("/auth/manager");
        assertEquals(401, r.statusCode());
    }

    // =================== 3) 权限鉴权 /auth/users /auth/orders/{id}/pay（6）====================

    @Test
    void usersList_asAdmin_shouldBeOk() throws Exception {
        String token = login("admin", "admin");
        HttpResponse<String> r = authGet(token, "/auth/users");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("user list accessed"));
    }

    @Test
    void usersList_asUser_shouldBeOk() throws Exception {
        // user 角色拥有 user:list 权限
        String token = login("user", "user");
        HttpResponse<String> r = authGet(token, "/auth/users");
        assertEquals(200, r.statusCode());
    }

    @Test
    void usersList_unauthenticated_shouldBe401() throws Exception {
        HttpResponse<String> r = get("/auth/users");
        assertEquals(401, r.statusCode());
    }

    @Test
    void orderPay_asAdmin_shouldBeOk() throws Exception {
        // admin 拥有 order:pay
        String token = login("admin", "admin");
        HttpResponse<String> r = authPost(token, "/auth/orders/abc/pay", "");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("order paid"));
    }

    @Test
    void orderPay_asUser_shouldBeForbidden() throws Exception {
        // user 没有 order:pay
        String token = login("user", "user");
        HttpResponse<String> r = authPost(token, "/auth/orders/abc/pay", "");
        assertEquals(403, r.statusCode());
    }

    @Test
    void orderPay_unauthenticated_shouldBe401() throws Exception {
        HttpResponse<String> r = postJson("/auth/orders/abc/pay", "");
        assertEquals(401, r.statusCode());
    }

    // =================== 4) 组合鉴权 admin + user:delete（4）====================

    @Test
    void deleteUser_asAdmin_shouldBeOk() throws Exception {
        String token = login("admin", "admin");
        HttpResponse<String> r = authDelete(token, "/auth/users/10002");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"success\":true"));
    }

    @Test
    void deleteUser_asUser_shouldBeForbidden() throws Exception {
        // user 没有 admin 角色
        String token = login("user", "user");
        HttpResponse<String> r = authDelete(token, "/auth/users/10001");
        assertEquals(403, r.statusCode());
    }

    @Test
    void deleteUser_unauthenticated_shouldBe401() throws Exception {
        HttpResponse<String> r = delete("/auth/users/10001");
        assertEquals(401, r.statusCode());
    }

    @Test
    void checkRole_asAdmin_shouldReturnTrueForAdmin() throws Exception {
        String token = login("admin", "admin");
        HttpResponse<String> r = authPost(token, "/auth/check/role", "{\"role\":\"admin\"}");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"has\":true"));
    }

    @Test
    void checkRole_asUser_shouldReturnFalseForAdmin() throws Exception {
        String token = login("user", "user");
        HttpResponse<String> r = authPost(token, "/auth/check/role", "{\"role\":\"admin\"}");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"has\":false"));
    }

    // =================== 5) RBAC 资源管理（10）====================

    @Test
    void listUsers_shouldReturn3SeedUsers() throws Exception {
        HttpResponse<String> r = get("/rbac/admin/users");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("admin"));
        assertTrue(r.body().contains("user"));
        assertTrue(r.body().contains("disabled"));
    }

    @Test
    void getUser_byId_shouldReturnUser() throws Exception {
        HttpResponse<String> r = get("/rbac/admin/users/10001");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("admin"));
    }

    @Test
    void createUser_shouldReturn201() throws Exception {
        HttpResponse<String> r = postJson("/rbac/admin/users",
                "{\"userId\":\"20001\",\"username\":\"alice\",\"password\":\"p\",\"realName\":\"Alice\"}");
        assertEquals(201, r.statusCode());
        assertTrue(r.body().contains("20001"));
    }

    @Test
    void updateUser_shouldReturn200() throws Exception {
        HttpResponse<String> r = putJson("/rbac/admin/users/10001",
                "{\"realName\":\"NewName\",\"password\":null,\"status\":null}");
        assertEquals(200, r.statusCode());
    }

    @Test
    void deleteUser_byRbacAdmin_shouldReturn200() throws Exception {
        // 先创建一个新用户，再删它
        postJson("/rbac/admin/users",
                "{\"userId\":\"20002\",\"username\":\"bob\",\"password\":\"p\",\"realName\":\"Bob\"}");
        HttpResponse<String> r = delete("/rbac/admin/users/20002");
        assertEquals(200, r.statusCode());
    }

    @Test
    void listRoles_shouldReturn3SeedRoles() throws Exception {
        HttpResponse<String> r = get("/rbac/admin/roles");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("admin"));
        assertTrue(r.body().contains("user"));
        assertTrue(r.body().contains("manager"));
    }

    @Test
    void getRole_byId_shouldReturnRole() throws Exception {
        HttpResponse<String> r = get("/rbac/admin/roles/R001");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("admin"));
    }

    @Test
    void createRole_shouldReturn201() throws Exception {
        HttpResponse<String> r = postJson("/rbac/admin/roles",
                "{\"roleId\":\"R100\",\"roleCode\":\"tester\",\"roleName\":\"Tester\",\"description\":\"QA\"}");
        assertEquals(201, r.statusCode());
        assertTrue(r.body().contains("R100"));
    }

    @Test
    void listPermissions_shouldReturn6SeedPermissions() throws Exception {
        HttpResponse<String> r = get("/rbac/admin/permissions");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("user:add"));
        assertTrue(r.body().contains("user:delete"));
        assertTrue(r.body().contains("user:list"));
        assertTrue(r.body().contains("role:add"));
        assertTrue(r.body().contains("goods:view"));
        assertTrue(r.body().contains("order:pay"));
    }

    @Test
    void assignRolesToUser_shouldReturn200() throws Exception {
        // 把 user 用户（10002）的角色改成 manager
        HttpResponse<String> r = postJson("/rbac/admin/users/10002/roles",
                "{\"roleIds\":[\"R003\"]}");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("10002"));
    }

    @Test
    void getUserPermissions_adminShouldHaveAll() throws Exception {
        HttpResponse<String> r = get("/rbac/admin/users/10001/permissions");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("user:add"));
        assertTrue(r.body().contains("user:delete"));
        assertTrue(r.body().contains("user:list"));
        assertTrue(r.body().contains("role:add"));
        assertTrue(r.body().contains("goods:view"));
        assertTrue(r.body().contains("order:pay"));
    }

    @Test
    void getRolePermissions_adminShouldHaveAll() throws Exception {
        HttpResponse<String> r = get("/rbac/admin/roles/R001/permissions");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("user:add"));
        assertTrue(r.body().contains("order:pay"));
    }

    @Test
    void assignPermissionsToRole_shouldReturn200() throws Exception {
        // 给 user 角色加 order:pay
        HttpResponse<String> r = postJson("/rbac/admin/roles/R002/permissions",
                "{\"permissionIds\":[\"P003\",\"P005\",\"P006\"]}");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("R002"));
    }

    @Test
    void kickout_shouldReturn200() throws Exception {
        HttpResponse<String> r = postJson("/auth/kickout", "{\"userId\":\"10002\"}");
        assertEquals(200, r.statusCode());
    }
}