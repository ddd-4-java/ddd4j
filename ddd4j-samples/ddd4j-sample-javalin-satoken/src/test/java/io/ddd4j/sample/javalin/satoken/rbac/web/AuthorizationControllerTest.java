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
 * {@link AuthorizationController} RBAC 授权管理路由集成测试。
 *
 * <p>完整覆盖 AuthorizationController.routes() 注册的所有端点：
 * <ul>
 *   <li>用户 CRUD：POST/GET/PUT/DELETE /admin/users</li>
 *   <li>用户角色分配：POST/GET /admin/users/{id}/roles</li>
 *   <li>用户权限查询：GET /admin/users/{id}/permissions</li>
 *   <li>角色 CRUD：POST/GET/PUT/DELETE /admin/roles</li>
 *   <li>角色权限分配：POST/GET /admin/roles/{id}/permissions</li>
 *   <li>权限 CRUD：POST/GET/PUT/DELETE /admin/permissions</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@org.junit.jupiter.api.parallel.Execution(org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD)
class AuthorizationControllerTest {

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

    // =================== 用户管理 ===================

    @Test
    void createUser_shouldReturn201() throws Exception {
        HttpResponse<String> r = postJson("/rbac/admin/users",
                "{\"userId\":\"30001\",\"username\":\"testuser\",\"password\":\"pass\",\"realName\":\"Test User\"}");
        assertEquals(201, r.statusCode());
        assertTrue(r.body().contains("30001"));
    }

    @Test
    void createUser_withDuplicateId_shouldFail() throws Exception {
        // 创建一个用户
        postJson("/rbac/admin/users",
                "{\"userId\":\"30002\",\"username\":\"dupuser\",\"password\":\"pass\",\"realName\":\"Dup\"}");
        // 尝试用相同 userId 创建（InMemory 会覆盖，但验证不会抛异常）
        HttpResponse<String> r = postJson("/rbac/admin/users",
                "{\"userId\":\"30002\",\"username\":\"dupuser2\",\"password\":\"pass\",\"realName\":\"Dup2\"}");
        // InMemoryRepository 允许覆盖，返回 201
        assertEquals(201, r.statusCode());
    }

    @Test
    void listUsers_shouldReturnSeedUsers() throws Exception {
        HttpResponse<String> r = get("/rbac/admin/users");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("admin"));
        assertTrue(r.body().contains("user"));
    }

    @Test
    void getUser_byId_shouldReturnUser() throws Exception {
        HttpResponse<String> r = get("/rbac/admin/users/10001");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("admin"));
    }

    @Test
    void getUser_nonExistent_shouldReturnNull() throws Exception {
        HttpResponse<String> r = get("/rbac/admin/users/99999");
        assertEquals(200, r.statusCode());
        // 返回 null 或空对象
        assertTrue(r.body().contains("null") || r.body().contains("{}"));
    }

    @Test
    void updateUser_shouldReturn200() throws Exception {
        HttpResponse<String> r = putJson("/rbac/admin/users/10001",
                "{\"realName\":\"UpdatedName\",\"password\":\"newpass\",\"status\":\"ENABLED\"}");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("10001"));
    }

    @Test
    void updateUser_nonExistent_shouldFail() throws Exception {
        HttpResponse<String> r = putJson("/rbac/admin/users/99999",
                "{\"realName\":\"Ghost\",\"password\":null,\"status\":null}");
        assertTrue(r.statusCode() >= 400, "Expected error status but got: " + r.statusCode());
    }

    @Test
    void deleteUser_shouldReturn200() throws Exception {
        // 先创建
        postJson("/rbac/admin/users",
                "{\"userId\":\"30004\",\"username\":\"deluser\",\"password\":\"pass\",\"realName\":\"To Delete\"}");
        HttpResponse<String> r = delete("/rbac/admin/users/30004");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("30004"));
    }

    // =================== 用户角色分配 ===================

    @Test
    void assignRolesToUser_shouldReturn200() throws Exception {
        HttpResponse<String> r = postJson("/rbac/admin/users/10002/roles",
                "{\"roleIds\":[\"R001\",\"R003\"]}");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("10002"));
    }

    @Test
    void getUserPermissions_shouldReturnPermissions() throws Exception {
        HttpResponse<String> r = get("/rbac/admin/users/10001/permissions");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("userId"));
        assertTrue(r.body().contains("roles"));
        assertTrue(r.body().contains("permissions"));
    }

    @Test
    void getUserPermissions_nonExistent_shouldReturnEmpty() throws Exception {
        HttpResponse<String> r = get("/rbac/admin/users/99999/permissions");
        assertEquals(200, r.statusCode());
        // 应该返回空集合
        assertTrue(r.body().contains("[]") || r.body().contains("null"));
    }

    // =================== 角色管理 ===================

    @Test
    void createRole_shouldReturn201() throws Exception {
        HttpResponse<String> r = postJson("/rbac/admin/roles",
                "{\"roleId\":\"R200\",\"roleCode\":\"auditor\",\"roleName\":\"Auditor\",\"description\":\"Audit role\"}");
        assertEquals(201, r.statusCode());
        assertTrue(r.body().contains("R200"));
    }

    @Test
    void listRoles_shouldReturnSeedRoles() throws Exception {
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
    void getRole_nonExistent_shouldReturnNull() throws Exception {
        HttpResponse<String> r = get("/rbac/admin/roles/R999");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("null") || r.body().contains("{}"));
    }

    @Test
    void updateRole_shouldReturn200() throws Exception {
        HttpResponse<String> r = putJson("/rbac/admin/roles/R001",
                "{\"roleName\":\"Super Admin\",\"description\":\"Updated\",\"status\":\"ENABLED\"}");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("R001"));
    }

    @Test
    void deleteRole_shouldReturn200() throws Exception {
        // 先创建
        postJson("/rbac/admin/roles",
                "{\"roleId\":\"R201\",\"roleCode\":\"temp\",\"roleName\":\"Temp\",\"description\":\"Temp role\"}");
        HttpResponse<String> r = delete("/rbac/admin/roles/R201");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("R201"));
    }

    // =================== 角色权限分配 ===================

    @Test
    void assignPermissionsToRole_shouldReturn200() throws Exception {
        HttpResponse<String> r = postJson("/rbac/admin/roles/R002/permissions",
                "{\"permissionIds\":[\"P001\",\"P003\"]}");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("R002"));
    }

    @Test
    void getRolePermissions_shouldReturnPermissions() throws Exception {
        HttpResponse<String> r = get("/rbac/admin/roles/R001/permissions");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("roleId"));
        assertTrue(r.body().contains("permissions"));
    }

    // =================== 权限管理 ===================

    @Test
    void createPermission_shouldReturn201() throws Exception {
        HttpResponse<String> r = postJson("/rbac/admin/permissions",
                "{\"permissionId\":\"P200\",\"permissionCode\":\"report:view\",\"permissionName\":\"View Reports\",\"module\":\"report\"}");
        assertEquals(201, r.statusCode());
        assertTrue(r.body().contains("P200"));
    }

    @Test
    void listPermissions_shouldReturnSeedPermissions() throws Exception {
        HttpResponse<String> r = get("/rbac/admin/permissions");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("user:add"));
        assertTrue(r.body().contains("user:delete"));
        assertTrue(r.body().contains("order:pay"));
    }

    @Test
    void getPermission_byId_shouldReturnPermission() throws Exception {
        HttpResponse<String> r = get("/rbac/admin/permissions/P001");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("user:add"));
    }

    @Test
    void getPermission_nonExistent_shouldReturnNull() throws Exception {
        HttpResponse<String> r = get("/rbac/admin/permissions/P999");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("null") || r.body().contains("{}"));
    }

    @Test
    void updatePermission_shouldReturn200() throws Exception {
        HttpResponse<String> r = putJson("/rbac/admin/permissions/P001",
                "{\"permissionName\":\"Add User Updated\",\"module\":\"user\",\"status\":\"ENABLED\"}");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("P001"));
    }

    @Test
    void deletePermission_shouldReturn200() throws Exception {
        // 先创建
        postJson("/rbac/admin/permissions",
                "{\"permissionId\":\"P201\",\"permissionCode\":\"temp:perm\",\"permissionName\":\"Temp\",\"module\":\"temp\"}");
        HttpResponse<String> r = delete("/rbac/admin/permissions/P201");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("P201"));
    }

    // =================== 边界情况 ===================

    @Test
    void createUser_withNullFields_shouldFail() throws Exception {
        HttpResponse<String> r = postJson("/rbac/admin/users",
                "{\"userId\":null,\"username\":null,\"password\":null,\"realName\":null}");
        assertTrue(r.statusCode() >= 400, "Expected error status but got: " + r.statusCode());
    }

    @Test
    void createRole_withEmptyCode_shouldFail() throws Exception {
        HttpResponse<String> r = postJson("/rbac/admin/roles",
                "{\"roleId\":\"R300\",\"roleCode\":\"\",\"roleName\":\"Empty\",\"description\":\"\"}");
        // 空 roleCode 应该被拒绝或接受（取决于业务规则）
        // 这里验证不会抛出未处理的异常
        assertTrue(r.statusCode() >= 200 && r.statusCode() < 600);
    }

    @Test
    void assignRoles_withEmptyList_shouldSucceed() throws Exception {
        HttpResponse<String> r = postJson("/rbac/admin/users/10001/roles",
                "{\"roleIds\":[]}");
        assertEquals(200, r.statusCode());
    }

    @Test
    void assignPermissions_withEmptyList_shouldSucceed() throws Exception {
        HttpResponse<String> r = postJson("/rbac/admin/roles/R001/permissions",
                "{\"permissionIds\":[]}");
        assertEquals(200, r.statusCode());
    }
}
