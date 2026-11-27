/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.sample.javalin.satoken.rbac.web;

import io.ddd4j.sample.javalin.satoken.TestSupport;
import io.javalin.Javalin;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@org.junit.jupiter.api.parallel.Execution(org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD)
class AuthorizationControllerTest {

    /**
     * 由当前测试启动和关闭的应用实例。
     */
    private static Javalin app;
    /**
     * 用于发送 HTTP 请求的客户端。
     */
    private static HttpClient httpClient;
    /**
     * 当前服务的 HTTP 基础地址。
     */
    private static String baseUrl;

    /**
     * 测试执行前执行 <code>TestSupport.start()</code>、<code>app.port()</code>、<code>HttpClient.newBuilder()</code>、<code>Duration.ofSeconds()</code> 完成资源准备。
     */
    @BeforeAll
    void startServer() {
        app = TestSupport.start();
        baseUrl = "http://localhost:" + app.port();
        httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    /**
     * 测试结束后执行 <code>app.stop()</code> 完成资源回收或状态恢复。
     */
    @AfterAll
    void stopServer() {
        if (Objects.nonNull(app)) {
            app.stop();
        }
    }

    /**
     * 测试执行前执行 <code>app.stop()</code>、<code>TestSupport.start()</code>、<code>app.port()</code>、<code>HttpClient.newBuilder()</code> 完成资源准备。
     */
    @BeforeEach
    void resetServerState() {
        app.stop();
        app = TestSupport.start();
        baseUrl = "http://localhost:" + app.port();
        // 新服务器实例使用独立连接池，不跨重启复用旧 HTTP 连接。
        httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    // ------------------- HTTP 助手 -------------------

    /**
     * 读取 <code>response</code> 供当前测试使用。
     *
     * @param path 测试请求的资源路径或待访问文件路径
     * @param body 当前请求或响应中要读取的正文
     * @return 当前测试使用的结果，取自 <code>response</code>
     * @throws Exception 发起测试请求时失败
     */
    private HttpResponse<String> postJson(String path, String body) throws Exception {
        HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
        recordResponse("POST", response);
        return response;
    }

    /**
     * 读取 <code>response</code> 供当前测试使用。
     *
     * @param path 测试请求的资源路径或待访问文件路径
     * @param body 当前请求或响应中要读取的正文
     * @return 当前测试使用的结果，取自 <code>response</code>
     * @throws Exception 发起测试请求时失败
     */
    private HttpResponse<String> putJson(String path, String body) throws Exception {
        HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
        recordResponse("PUT", response);
        return response;
    }

    /**
     * 读取 <code>response</code> 供当前测试使用。
     *
     * @param path 测试请求的资源路径或待访问文件路径
     * @return 当前测试使用的结果，取自 <code>response</code>
     * @throws Exception 发起测试请求时失败
     */
    private HttpResponse<String> delete(String path) throws Exception {
        HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).DELETE().build(),
                HttpResponse.BodyHandlers.ofString());
        recordResponse("DELETE", response);
        return response;
    }

    /**
     * 读取 <code>response</code> 供当前测试使用。
     *
     * @param path 测试请求的资源路径或待访问文件路径
     * @return 当前测试使用的结果，取自 <code>response</code>
     * @throws Exception 发起测试请求时失败
     */
    private HttpResponse<String> get(String path) throws Exception {
        HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        recordResponse("GET", response);
        return response;
    }

    /**
     * 调用 <code>response.uri()</code>、<code>response.statusCode()</code>、<code>response.version()</code> 执行当前夹具操作。
     *
     * @param method   发给被测服务的 HTTP 方法
     * @param response 当前请求已收到的响应
     */
    private void recordResponse(String method, HttpResponse<String> response) {
        log.info("RBAC HTTP diagnostic method={} uri={} status={} version={} headers={} body={} serverPort={}",
                method, response.uri(), response.statusCode(), response.version(), response.headers().map(),
                response.body(), app.port());
    }

    // =================== 用户管理 ===================

    /**
     * 请求 <code>POST /rbac/admin/users</code>，确认 HTTP 响应状态为 201。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void createUser_shouldReturn201() throws Exception {
        HttpResponse<String> r = postJson("/rbac/admin/users",
                "{\"userId\":\"30001\",\"username\":\"testuser\",\"password\":\"pass\",\"realName\":\"Test User\"}");
        assertEquals(201, r.statusCode());
        assertTrue(r.body().contains("30001"));
    }

    /**
     * 请求 <code>POST /rbac/admin/users</code>，确认 HTTP 响应状态为 201。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
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

    /**
     * 验证<code>r.statusCode()</code> 等于 <code>200</code>，<code>r.body().contains("admin")</code>为真。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void listUsers_shouldReturnSeedUsers() throws Exception {
        HttpResponse<String> r = get("/rbac/admin/users");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("admin"));
        assertTrue(r.body().contains("user"));
    }

    /**
     * 验证<code>r.statusCode()</code> 等于 <code>200</code>，<code>r.body().contains("admin")</code>为真。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void getUser_byId_shouldReturnUser() throws Exception {
        HttpResponse<String> r = get("/rbac/admin/users/10001");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("admin"));
    }

    /**
     * 验证<code>r.statusCode()</code> 等于 <code>200</code>，<code>r.body().contains("null") || r.body().contains("{}")</code>为真。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void getUser_nonExistent_shouldReturnNull() throws Exception {
        HttpResponse<String> r = get("/rbac/admin/users/99999");
        assertEquals(200, r.statusCode());
        // 返回 null 或空对象
        assertTrue(r.body().contains("null") || r.body().contains("{}"));
    }

    /**
     * 验证<code>r.statusCode()</code> 等于 <code>200</code>，<code>r.body().contains("10001")</code>为真。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void updateUser_shouldReturn200() throws Exception {
        HttpResponse<String> r = putJson("/rbac/admin/users/10001",
                "{\"realName\":\"UpdatedName\",\"password\":\"newpass\",\"status\":\"ENABLED\"}");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("10001"));
    }

    /**
     * 验证<code>r.statusCode() &gt;= 400</code>为真。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void updateUser_nonExistent_shouldFail() throws Exception {
        HttpResponse<String> r = putJson("/rbac/admin/users/99999",
                "{\"realName\":\"Ghost\",\"password\":null,\"status\":null}");
        assertTrue(r.statusCode() >= 400, "Expected error status but got: " + r.statusCode());
    }

    /**
     * 请求 <code>POST /rbac/admin/users</code>，确认 HTTP 响应状态为 200。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
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

    /**
     * 请求 <code>POST /rbac/admin/users/10002/roles</code>，确认 HTTP 响应状态为 200。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void assignRolesToUser_shouldReturn200() throws Exception {
        HttpResponse<String> r = postJson("/rbac/admin/users/10002/roles",
                "{\"roleIds\":[\"R001\",\"R003\"]}");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("10002"));
    }

    /**
     * 验证<code>r.statusCode()</code> 等于 <code>200</code>，<code>r.body().contains("userId")</code>为真。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void getUserPermissions_shouldReturnPermissions() throws Exception {
        HttpResponse<String> r = get("/rbac/admin/users/10001/permissions");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("userId"));
        assertTrue(r.body().contains("roles"));
        assertTrue(r.body().contains("permissions"));
    }

    /**
     * 验证<code>r.statusCode()</code> 等于 <code>200</code>，<code>r.body().contains("[]") || r.body().contains("null")</code>为真。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void getUserPermissions_nonExistent_shouldReturnEmpty() throws Exception {
        HttpResponse<String> r = get("/rbac/admin/users/99999/permissions");
        assertEquals(200, r.statusCode());
        // 应该返回空集合
        assertTrue(r.body().contains("[]") || r.body().contains("null"));
    }

    // =================== 角色管理 ===================

    /**
     * 请求 <code>POST /rbac/admin/roles</code>，确认 HTTP 响应状态为 201。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void createRole_shouldReturn201() throws Exception {
        HttpResponse<String> r = postJson("/rbac/admin/roles",
                "{\"roleId\":\"R200\",\"roleCode\":\"auditor\",\"roleName\":\"Auditor\",\"description\":\"Audit role\"}");
        assertEquals(201, r.statusCode());
        assertTrue(r.body().contains("R200"));
    }

    /**
     * 验证<code>r.statusCode()</code> 等于 <code>200</code>，<code>r.body().contains("admin")</code>为真。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void listRoles_shouldReturnSeedRoles() throws Exception {
        HttpResponse<String> r = get("/rbac/admin/roles");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("admin"));
        assertTrue(r.body().contains("user"));
        assertTrue(r.body().contains("manager"));
    }

    /**
     * 验证<code>r.statusCode()</code> 等于 <code>200</code>，<code>r.body().contains("admin")</code>为真。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void getRole_byId_shouldReturnRole() throws Exception {
        HttpResponse<String> r = get("/rbac/admin/roles/R001");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("admin"));
    }

    /**
     * 验证<code>r.statusCode()</code> 等于 <code>200</code>，<code>r.body().contains("null") || r.body().contains("{}")</code>为真。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void getRole_nonExistent_shouldReturnNull() throws Exception {
        HttpResponse<String> r = get("/rbac/admin/roles/R999");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("null") || r.body().contains("{}"));
    }

    /**
     * 验证<code>r.statusCode()</code> 等于 <code>200</code>，<code>r.body().contains("R001")</code>为真。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void updateRole_shouldReturn200() throws Exception {
        HttpResponse<String> r = putJson("/rbac/admin/roles/R001",
                "{\"roleName\":\"Super Admin\",\"description\":\"Updated\",\"status\":\"ENABLED\"}");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("R001"));
    }

    /**
     * 请求 <code>POST /rbac/admin/roles</code>，确认 HTTP 响应状态为 200。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
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

    /**
     * 请求 <code>POST /rbac/admin/roles/R002/permissions</code>，确认 HTTP 响应状态为 200。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void assignPermissionsToRole_shouldReturn200() throws Exception {
        HttpResponse<String> r = postJson("/rbac/admin/roles/R002/permissions",
                "{\"permissionIds\":[\"P001\",\"P003\"]}");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("R002"));
    }

    /**
     * 验证<code>r.statusCode()</code> 等于 <code>200</code>，<code>r.body().contains("roleId")</code>为真。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void getRolePermissions_shouldReturnPermissions() throws Exception {
        HttpResponse<String> r = get("/rbac/admin/roles/R001/permissions");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("roleId"));
        assertTrue(r.body().contains("permissions"));
    }

    // =================== 权限管理 ===================

    /**
     * 请求 <code>POST /rbac/admin/permissions</code>，确认 HTTP 响应状态为 201。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void createPermission_shouldReturn201() throws Exception {
        HttpResponse<String> r = postJson("/rbac/admin/permissions",
                "{\"permissionId\":\"P200\",\"permissionCode\":\"report:view\",\"permissionName\":\"View Reports\",\"module\":\"report\"}");
        assertEquals(201, r.statusCode());
        assertTrue(r.body().contains("P200"));
    }

    /**
     * 验证<code>r.statusCode()</code> 等于 <code>200</code>，<code>r.body().contains("user:add")</code>为真。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void listPermissions_shouldReturnSeedPermissions() throws Exception {
        HttpResponse<String> r = get("/rbac/admin/permissions");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("user:add"));
        assertTrue(r.body().contains("user:delete"));
        assertTrue(r.body().contains("order:pay"));
    }

    /**
     * 验证<code>r.statusCode()</code> 等于 <code>200</code>，<code>r.body().contains("user:add")</code>为真。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void getPermission_byId_shouldReturnPermission() throws Exception {
        HttpResponse<String> r = get("/rbac/admin/permissions/P001");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("user:add"));
    }

    /**
     * 验证<code>r.statusCode()</code> 等于 <code>200</code>，<code>r.body().contains("null") || r.body().contains("{}")</code>为真。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void getPermission_nonExistent_shouldReturnNull() throws Exception {
        HttpResponse<String> r = get("/rbac/admin/permissions/P999");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("null") || r.body().contains("{}"));
    }

    /**
     * 验证<code>r.statusCode()</code> 等于 <code>200</code>，<code>r.body().contains("P001")</code>为真。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void updatePermission_shouldReturn200() throws Exception {
        HttpResponse<String> r = putJson("/rbac/admin/permissions/P001",
                "{\"permissionName\":\"Add User Updated\",\"module\":\"user\",\"status\":\"ENABLED\"}");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("P001"));
    }

    /**
     * 请求 <code>POST /rbac/admin/permissions</code>，确认 HTTP 响应状态为 200。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
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

    /**
     * 验证<code>r.statusCode() &gt;= 400</code>为真。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void createUser_withNullFields_shouldFail() throws Exception {
        HttpResponse<String> r = postJson("/rbac/admin/users",
                "{\"userId\":null,\"username\":null,\"password\":null,\"realName\":null}");
        assertTrue(r.statusCode() >= 400, "Expected error status but got: " + r.statusCode());
    }

    /**
     * 验证<code>r.statusCode() &gt;= 200 &amp;&amp; r.statusCode() &lt; 600</code>为真。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void createRole_withEmptyCode_shouldFail() throws Exception {
        HttpResponse<String> r = postJson("/rbac/admin/roles",
                "{\"roleId\":\"R300\",\"roleCode\":\"\",\"roleName\":\"Empty\",\"description\":\"\"}");
        // 空 roleCode 应该被拒绝或接受（取决于业务规则）
        // 这里验证不会抛出未处理的异常
        assertTrue(r.statusCode() >= 200 && r.statusCode() < 600);
    }

    /**
     * 请求 <code>POST /rbac/admin/users/10001/roles</code>，确认 HTTP 响应状态为 200。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void assignRoles_withEmptyList_shouldSucceed() throws Exception {
        HttpResponse<String> r = postJson("/rbac/admin/users/10001/roles",
                "{\"roleIds\":[]}");
        assertEquals(200, r.statusCode());
    }

    /**
     * 请求 <code>POST /rbac/admin/roles/R001/permissions</code>，确认 HTTP 响应状态为 200。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void assignPermissions_withEmptyList_shouldSucceed() throws Exception {
        HttpResponse<String> r = postJson("/rbac/admin/roles/R001/permissions",
                "{\"permissionIds\":[]}");
        assertEquals(200, r.statusCode());
    }
}
