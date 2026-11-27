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
import org.junit.jupiter.api.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

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
     * 调用 <code>httpClient.send()</code>、<code>HttpRequest.newBuilder()</code>、<code>URI.create()</code> 执行当前夹具操作。
     *
     * @param path 测试请求的资源路径或待访问文件路径
     * @param body 当前请求或响应中要读取的正文
     * @return 提供给后续契约断言的 <code>HttpResponse&lt;String&gt;</code> 结果
     * @throws Exception 发起测试请求时失败
     */
    private HttpResponse<String> postJson(String path, String body) throws Exception {
        return httpClient.send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /**
     * 通过 <code>httpClient.send()</code> 取得当前测试需要的结果。
     *
     * @param path 测试请求的资源路径或待访问文件路径
     * @return 当前测试使用的结果，取自 <code>httpClient.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build(), HttpResponse.BodyHandlers.ofString())</code>
     * @throws Exception 发起测试请求时失败
     */
    private HttpResponse<String> get(String path) throws Exception {
        return httpClient.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /**
     * 通过 <code>httpClient.send()</code> 取得当前测试需要的结果。
     *
     * @param path 测试请求的资源路径或待访问文件路径
     * @return 当前测试使用的结果，取自 <code>httpClient.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).DELETE().build(), HttpResponse.BodyHandlers.ofString())</code>
     * @throws Exception 发起测试请求时失败
     */
    private HttpResponse<String> delete(String path) throws Exception {
        return httpClient.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).DELETE().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /**
     * 通过 <code>r.body()</code> 取得当前测试需要的结果。
     *
     * @param username 测试会话使用的用户名
     * @param password 测试账号的口令
     * @return 当前测试使用的结果，取自 <code>r.body().substring(start, end)</code>
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
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

    /**
     * 通过 <code>HttpRequest.newBuilder()</code> 取得当前测试需要的结果。
     *
     * @param token 从测试登录获得的会话凭据
     * @param path  测试请求的资源路径或待访问文件路径
     * @return 当前测试使用的结果，取自 <code>HttpRequest.newBuilder(URI.create(baseUrl + path)).header("satoken", token)</code>
     */
    private HttpRequest.Builder authReq(String token, String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path)).header("satoken", token);
    }

    /**
     * 通过 <code>httpClient.send()</code> 取得当前测试需要的结果。
     *
     * @param token 从测试登录获得的会话凭据
     * @param path  测试请求的资源路径或待访问文件路径
     * @return 当前测试使用的结果，取自 <code>httpClient.send(authReq(token, path).GET().build(), HttpResponse.BodyHandlers.ofString())</code>
     * @throws Exception 发起测试请求时失败
     */
    private HttpResponse<String> authGet(String token, String path) throws Exception {
        return httpClient.send(authReq(token, path).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    /**
     * 调用 <code>httpClient.send()</code>、<code>authReq()</code>、<code>HttpRequest.BodyPublishers.ofString()</code> 执行当前夹具操作。
     *
     * @param token 从测试登录获得的会话凭据
     * @param path  测试请求的资源路径或待访问文件路径
     * @param body  当前请求或响应中要读取的正文
     * @return 提供给后续契约断言的 <code>HttpResponse&lt;String&gt;</code> 结果
     * @throws Exception 发起测试请求时失败
     */
    private HttpResponse<String> authPost(String token, String path, String body) throws Exception {
        return httpClient.send(authReq(token, path)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /**
     * 通过 <code>httpClient.send()</code> 取得当前测试需要的结果。
     *
     * @param token 从测试登录获得的会话凭据
     * @param path  测试请求的资源路径或待访问文件路径
     * @return 当前测试使用的结果，取自 <code>httpClient.send(authReq(token, path).DELETE().build(), HttpResponse.BodyHandlers.ofString())</code>
     * @throws Exception 发起测试请求时失败
     */
    private HttpResponse<String> authDelete(String token, String path) throws Exception {
        return httpClient.send(authReq(token, path).DELETE().build(), HttpResponse.BodyHandlers.ofString());
    }

    // =================== 登录 ===================

    /**
     * 验证<code>token</code>非空，<code>token.isEmpty()</code>为假。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void login_validCredentials_shouldReturnToken() throws Exception {
        String token = login("admin", "admin");
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    /**
     * 口令无效时请求 <code>POST /auth/login</code>，确认 HTTP 响应状态为 401。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void login_wrongPassword_shouldReturn401() throws Exception {
        HttpResponse<String> r = postJson("/auth/login",
                "{\"username\":\"admin\",\"password\":\"wrong\"}");
        assertEquals(401, r.statusCode());
    }

    /**
     * 请求 <code>POST /auth/login</code>，确认 HTTP 响应状态为 401。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void login_disabledUser_shouldReturn401() throws Exception {
        HttpResponse<String> r = postJson("/auth/login",
                "{\"username\":\"disabled\",\"password\":\"disabled\"}");
        assertEquals(401, r.statusCode());
    }

    /**
     * 请求 <code>POST /auth/login</code>，确认 HTTP 响应状态为 401。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void login_nonExistentUser_shouldReturn401() throws Exception {
        HttpResponse<String> r = postJson("/auth/login",
                "{\"username\":\"ghost\",\"password\":\"ghost\"}");
        assertEquals(401, r.statusCode());
    }

    // =================== 登出 ===================

    /**
     * 已登录时请求 <code>POST /auth/logout</code>，确认 HTTP 响应状态为 200。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void logout_authenticated_shouldReturn200() throws Exception {
        String token = login("admin", "admin");
        HttpResponse<String> r = authPost(token, "/auth/logout", "");
        assertEquals(200, r.statusCode());
    }

    /**
     * 已登录时请求 <code>POST /auth/logout</code>，确认 HTTP 响应状态为 401。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void logout_unauthenticated_shouldReturn401() throws Exception {
        HttpResponse<String> r = postJson("/auth/logout", "");
        assertEquals(401, r.statusCode());
    }

    // =================== 当前用户 ===================

    /**
     * 已登录时请求 <code>GET /auth/me</code>，确认 HTTP 响应状态为 200。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void me_authenticated_shouldReturnUserInfo() throws Exception {
        String token = login("admin", "admin");
        HttpResponse<String> r = authGet(token, "/auth/me");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"authenticated\":true"));
        assertTrue(r.body().contains("admin"));
    }

    /**
     * 验证<code>r.statusCode()</code> 等于 <code>200</code>，<code>r.body().contains("\"authenticated\":false")</code>为真。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void me_unauthenticated_shouldReturnNotAuthenticated() throws Exception {
        HttpResponse<String> r = get("/auth/me");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"authenticated\":false"));
    }

    // =================== 登录状态 ===================

    /**
     * 已登录时请求 <code>GET /auth/status</code>，确认 HTTP 响应状态为 200。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void status_authenticated_shouldReturnLoginTrue() throws Exception {
        String token = login("admin", "admin");
        HttpResponse<String> r = authGet(token, "/auth/status");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"login\":true"));
    }

    /**
     * 验证<code>r.statusCode()</code> 等于 <code>200</code>，<code>r.body().contains("\"login\":false")</code>为真。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void status_unauthenticated_shouldReturnLoginFalse() throws Exception {
        HttpResponse<String> r = get("/auth/status");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"login\":false"));
    }

    // =================== 踢人下线 ===================

    /**
     * 请求 <code>POST /auth/kickout</code>，确认 HTTP 响应状态为 200。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void kickout_shouldReturn200() throws Exception {
        HttpResponse<String> r = postJson("/auth/kickout", "{\"userId\":\"10002\"}");
        assertEquals(200, r.statusCode());
    }

    // =================== 角色鉴权 ===================

    /**
     * 使用管理员会话时请求 <code>POST /auth/check/role</code>，确认 HTTP 响应状态为 200。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void checkRole_adminHasAdminRole_shouldReturnTrue() throws Exception {
        String token = login("admin", "admin");
        HttpResponse<String> r = authPost(token, "/auth/check/role", "{\"role\":\"admin\"}");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"has\":true"));
    }

    /**
     * 请求 <code>POST /auth/check/role</code>，确认 HTTP 响应状态为 200。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void checkRole_userLacksAdminRole_shouldReturnFalse() throws Exception {
        String token = login("user", "user");
        HttpResponse<String> r = authPost(token, "/auth/check/role", "{\"role\":\"admin\"}");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"has\":false"));
    }

    /**
     * 已登录时请求 <code>POST /auth/check/role</code>，确认 HTTP 响应状态为 401。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void checkRole_unauthenticated_shouldReturn401() throws Exception {
        HttpResponse<String> r = postJson("/auth/check/role", "{\"role\":\"admin\"}");
        assertEquals(401, r.statusCode());
    }

    /**
     * 使用管理员会话时请求 <code>GET /auth/admin</code>，确认 HTTP 响应状态为 200。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void adminArea_asAdmin_shouldReturn200() throws Exception {
        String token = login("admin", "admin");
        HttpResponse<String> r = authGet(token, "/auth/admin");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("admin area accessed"));
    }

    /**
     * 使用普通用户会话时请求 <code>GET /auth/admin</code>，确认 HTTP 响应状态为 403。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void adminArea_asUser_shouldReturn403() throws Exception {
        String token = login("user", "user");
        HttpResponse<String> r = authGet(token, "/auth/admin");
        assertEquals(403, r.statusCode());
    }

    /**
     * 验证<code>r.statusCode()</code> 等于 <code>401</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void adminArea_unauthenticated_shouldReturn401() throws Exception {
        HttpResponse<String> r = get("/auth/admin");
        assertEquals(401, r.statusCode());
    }

    /**
     * 使用管理员会话时请求 <code>GET /auth/manager</code>，确认 HTTP 响应状态为 403。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void managerArea_asAdmin_shouldReturn403() throws Exception {
        String token = login("admin", "admin");
        HttpResponse<String> r = authGet(token, "/auth/manager");
        assertEquals(403, r.statusCode());
    }

    /**
     * 验证<code>r.statusCode()</code> 等于 <code>401</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void managerArea_unauthenticated_shouldReturn401() throws Exception {
        HttpResponse<String> r = get("/auth/manager");
        assertEquals(401, r.statusCode());
    }

    // =================== 权限鉴权 ===================

    /**
     * 请求 <code>POST /auth/check/permission</code>，确认 HTTP 响应状态为 200。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void checkPermission_adminHasOrderPay_shouldReturnTrue() throws Exception {
        String token = login("admin", "admin");
        HttpResponse<String> r = authPost(token, "/auth/check/permission", "{\"permission\":\"order:pay\"}");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"has\":true"));
    }

    /**
     * 请求 <code>POST /auth/check/permission</code>，确认 HTTP 响应状态为 200。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void checkPermission_userLacksOrderPay_shouldReturnFalse() throws Exception {
        String token = login("user", "user");
        HttpResponse<String> r = authPost(token, "/auth/check/permission", "{\"permission\":\"order:pay\"}");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"has\":false"));
    }

    /**
     * 已登录时请求 <code>POST /auth/check/permission</code>，确认 HTTP 响应状态为 401。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void checkPermission_unauthenticated_shouldReturn401() throws Exception {
        HttpResponse<String> r = postJson("/auth/check/permission", "{\"permission\":\"order:pay\"}");
        assertEquals(401, r.statusCode());
    }

    /**
     * 使用管理员会话时请求 <code>GET /auth/users</code>，确认 HTTP 响应状态为 200。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void usersList_asAdmin_shouldReturn200() throws Exception {
        String token = login("admin", "admin");
        HttpResponse<String> r = authGet(token, "/auth/users");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("user list accessed"));
    }

    /**
     * 使用普通用户会话时请求 <code>GET /auth/users</code>，确认 HTTP 响应状态为 200。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void usersList_asUser_shouldReturn200() throws Exception {
        String token = login("user", "user");
        HttpResponse<String> r = authGet(token, "/auth/users");
        assertEquals(200, r.statusCode());
    }

    /**
     * 验证<code>r.statusCode()</code> 等于 <code>401</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void usersList_unauthenticated_shouldReturn401() throws Exception {
        HttpResponse<String> r = get("/auth/users");
        assertEquals(401, r.statusCode());
    }

    /**
     * 使用管理员会话时请求 <code>POST /auth/orders/ORD-001/pay</code>，确认 HTTP 响应状态为 200。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void orderPay_asAdmin_shouldReturn200() throws Exception {
        String token = login("admin", "admin");
        HttpResponse<String> r = authPost(token, "/auth/orders/ORD-001/pay", "");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("order paid"));
        assertTrue(r.body().contains("ORD-001"));
    }

    /**
     * 使用普通用户会话时请求 <code>POST /auth/orders/ORD-001/pay</code>，确认 HTTP 响应状态为 403。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void orderPay_asUser_shouldReturn403() throws Exception {
        String token = login("user", "user");
        HttpResponse<String> r = authPost(token, "/auth/orders/ORD-001/pay", "");
        assertEquals(403, r.statusCode());
    }

    /**
     * 已登录时请求 <code>POST /auth/orders/ORD-001/pay</code>，确认 HTTP 响应状态为 401。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void orderPay_unauthenticated_shouldReturn401() throws Exception {
        HttpResponse<String> r = postJson("/auth/orders/ORD-001/pay", "");
        assertEquals(401, r.statusCode());
    }

    // =================== 组合鉴权 ===================

    /**
     * 使用管理员会话时请求 <code>DELETE /auth/users/10002</code>，确认 HTTP 响应状态为 200。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void deleteUser_asAdmin_shouldReturn200() throws Exception {
        String token = login("admin", "admin");
        HttpResponse<String> r = authDelete(token, "/auth/users/10002");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"success\":true"));
    }

    /**
     * 使用普通用户会话时请求 <code>DELETE /auth/users/10001</code>，确认 HTTP 响应状态为 403。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void deleteUser_asUser_shouldReturn403() throws Exception {
        String token = login("user", "user");
        HttpResponse<String> r = authDelete(token, "/auth/users/10001");
        assertEquals(403, r.statusCode());
    }

    /**
     * 验证<code>r.statusCode()</code> 等于 <code>401</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void deleteUser_unauthenticated_shouldReturn401() throws Exception {
        HttpResponse<String> r = delete("/auth/users/10001");
        assertEquals(401, r.statusCode());
    }
}
