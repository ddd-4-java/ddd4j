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

    /** \u7531\u5f53\u524d\u6d4b\u8bd5\u542f\u52a8\u548c\u5173\u95ed\u7684\u5e94\u7528\u5b9e\u4f8b\u3002 */
    private static Javalin app;
    /** \u7528\u4e8e\u53d1\u9001 HTTP \u8bf7\u6c42\u7684\u5ba2\u6237\u7aef\u3002 */
    private static HttpClient httpClient;
    /** \u5f53\u524d\u670d\u52a1\u7684 HTTP \u57fa\u7840\u5730\u5740\u3002 */
    private static String baseUrl;

    /** \u6d4b\u8bd5\u6267\u884c\u524d\u6267\u884c <code>TestSupport.start()</code>\u3001<code>app.port()</code>\u3001<code>HttpClient.newBuilder()</code>\u3001<code>Duration.ofSeconds()</code> \u5b8c\u6210\u8d44\u6e90\u51c6\u5907\u3002 */
    @BeforeAll
    void startServer() {
        app = TestSupport.start();
        baseUrl = "http://localhost:" + app.port();
        httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>app.stop()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterAll
    void stopServer() {
        if (Objects.nonNull(app)) {
            app.stop();
        }
    }

    /** \u6d4b\u8bd5\u6267\u884c\u524d\u6267\u884c <code>app.stop()</code>\u3001<code>TestSupport.start()</code>\u3001<code>app.port()</code>\u3001<code>HttpClient.newBuilder()</code> \u5b8c\u6210\u8d44\u6e90\u51c6\u5907\u3002 */
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
     * \u8c03\u7528 <code>httpClient.send()</code>\u3001<code>HttpRequest.newBuilder()</code>\u3001<code>URI.create()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
     * @param body \u5f53\u524d\u8bf7\u6c42\u6216\u54cd\u5e94\u4e2d\u8981\u8bfb\u53d6\u7684\u6b63\u6587
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>HttpResponse&lt;String&gt;</code> \u7ed3\u679c
     * @throws Exception \u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
    private HttpResponse<String> postJson(String path, String body) throws Exception {
        return httpClient.send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /**
     * \u901a\u8fc7 <code>httpClient.send()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>httpClient.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build(), HttpResponse.BodyHandlers.ofString())</code>
     * @throws Exception \u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
    private HttpResponse<String> get(String path) throws Exception {
        return httpClient.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /**
     * \u901a\u8fc7 <code>httpClient.send()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>httpClient.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).DELETE().build(), HttpResponse.BodyHandlers.ofString())</code>
     * @throws Exception \u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
    private HttpResponse<String> delete(String path) throws Exception {
        return httpClient.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).DELETE().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /**
     * \u901a\u8fc7 <code>r.body()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param username \u6d4b\u8bd5\u4f1a\u8bdd\u4f7f\u7528\u7684\u7528\u6237\u540d
     * @param password \u6d4b\u8bd5\u8d26\u53f7\u7684\u53e3\u4ee4
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>r.body().substring(start, end)</code>
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
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
     * \u901a\u8fc7 <code>HttpRequest.newBuilder()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param token \u4ece\u6d4b\u8bd5\u767b\u5f55\u83b7\u5f97\u7684\u4f1a\u8bdd\u51ed\u636e
     * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>HttpRequest.newBuilder(URI.create(baseUrl + path)).header("satoken", token)</code>
     */
    private HttpRequest.Builder authReq(String token, String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path)).header("satoken", token);
    }

    /**
     * \u901a\u8fc7 <code>httpClient.send()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param token \u4ece\u6d4b\u8bd5\u767b\u5f55\u83b7\u5f97\u7684\u4f1a\u8bdd\u51ed\u636e
     * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>httpClient.send(authReq(token, path).GET().build(), HttpResponse.BodyHandlers.ofString())</code>
     * @throws Exception \u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
    private HttpResponse<String> authGet(String token, String path) throws Exception {
        return httpClient.send(authReq(token, path).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    /**
     * \u8c03\u7528 <code>httpClient.send()</code>\u3001<code>authReq()</code>\u3001<code>HttpRequest.BodyPublishers.ofString()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param token \u4ece\u6d4b\u8bd5\u767b\u5f55\u83b7\u5f97\u7684\u4f1a\u8bdd\u51ed\u636e
     * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
     * @param body \u5f53\u524d\u8bf7\u6c42\u6216\u54cd\u5e94\u4e2d\u8981\u8bfb\u53d6\u7684\u6b63\u6587
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>HttpResponse&lt;String&gt;</code> \u7ed3\u679c
     * @throws Exception \u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
    private HttpResponse<String> authPost(String token, String path, String body) throws Exception {
        return httpClient.send(authReq(token, path)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /**
     * \u901a\u8fc7 <code>httpClient.send()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param token \u4ece\u6d4b\u8bd5\u767b\u5f55\u83b7\u5f97\u7684\u4f1a\u8bdd\u51ed\u636e
     * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>httpClient.send(authReq(token, path).DELETE().build(), HttpResponse.BodyHandlers.ofString())</code>
     * @throws Exception \u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
    private HttpResponse<String> authDelete(String token, String path) throws Exception {
        return httpClient.send(authReq(token, path).DELETE().build(), HttpResponse.BodyHandlers.ofString());
    }

    // =================== 登录 ===================

    /**
     * \u9a8c\u8bc1<code>token</code>\u975e\u7a7a\uff0c<code>token.isEmpty()</code>\u4e3a\u5047\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void login_validCredentials_shouldReturnToken() throws Exception {
        String token = login("admin", "admin");
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    /**
     * \u53e3\u4ee4\u65e0\u6548\u65f6\u8bf7\u6c42 <code>POST /auth/login</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 401\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void login_wrongPassword_shouldReturn401() throws Exception {
        HttpResponse<String> r = postJson("/auth/login",
                "{\"username\":\"admin\",\"password\":\"wrong\"}");
        assertEquals(401, r.statusCode());
    }

    /**
     * \u8bf7\u6c42 <code>POST /auth/login</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 401\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void login_disabledUser_shouldReturn401() throws Exception {
        HttpResponse<String> r = postJson("/auth/login",
                "{\"username\":\"disabled\",\"password\":\"disabled\"}");
        assertEquals(401, r.statusCode());
    }

    /**
     * \u8bf7\u6c42 <code>POST /auth/login</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 401\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void login_nonExistentUser_shouldReturn401() throws Exception {
        HttpResponse<String> r = postJson("/auth/login",
                "{\"username\":\"ghost\",\"password\":\"ghost\"}");
        assertEquals(401, r.statusCode());
    }

    // =================== 登出 ===================

    /**
     * \u5df2\u767b\u5f55\u65f6\u8bf7\u6c42 <code>POST /auth/logout</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void logout_authenticated_shouldReturn200() throws Exception {
        String token = login("admin", "admin");
        HttpResponse<String> r = authPost(token, "/auth/logout", "");
        assertEquals(200, r.statusCode());
    }

    /**
     * \u5df2\u767b\u5f55\u65f6\u8bf7\u6c42 <code>POST /auth/logout</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 401\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void logout_unauthenticated_shouldReturn401() throws Exception {
        HttpResponse<String> r = postJson("/auth/logout", "");
        assertEquals(401, r.statusCode());
    }

    // =================== 当前用户 ===================

    /**
     * \u5df2\u767b\u5f55\u65f6\u8bf7\u6c42 <code>GET /auth/me</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
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
     * \u9a8c\u8bc1<code>r.statusCode()</code> \u7b49\u4e8e <code>200</code>\uff0c<code>r.body().contains("\"authenticated\":false")</code>\u4e3a\u771f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void me_unauthenticated_shouldReturnNotAuthenticated() throws Exception {
        HttpResponse<String> r = get("/auth/me");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"authenticated\":false"));
    }

    // =================== 登录状态 ===================

    /**
     * \u5df2\u767b\u5f55\u65f6\u8bf7\u6c42 <code>GET /auth/status</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void status_authenticated_shouldReturnLoginTrue() throws Exception {
        String token = login("admin", "admin");
        HttpResponse<String> r = authGet(token, "/auth/status");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"login\":true"));
    }

    /**
     * \u9a8c\u8bc1<code>r.statusCode()</code> \u7b49\u4e8e <code>200</code>\uff0c<code>r.body().contains("\"login\":false")</code>\u4e3a\u771f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void status_unauthenticated_shouldReturnLoginFalse() throws Exception {
        HttpResponse<String> r = get("/auth/status");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"login\":false"));
    }

    // =================== 踢人下线 ===================

    /**
     * \u8bf7\u6c42 <code>POST /auth/kickout</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void kickout_shouldReturn200() throws Exception {
        HttpResponse<String> r = postJson("/auth/kickout", "{\"userId\":\"10002\"}");
        assertEquals(200, r.statusCode());
    }

    // =================== 角色鉴权 ===================

    /**
     * \u4f7f\u7528\u7ba1\u7406\u5458\u4f1a\u8bdd\u65f6\u8bf7\u6c42 <code>POST /auth/check/role</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void checkRole_adminHasAdminRole_shouldReturnTrue() throws Exception {
        String token = login("admin", "admin");
        HttpResponse<String> r = authPost(token, "/auth/check/role", "{\"role\":\"admin\"}");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"has\":true"));
    }

    /**
     * \u8bf7\u6c42 <code>POST /auth/check/role</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void checkRole_userLacksAdminRole_shouldReturnFalse() throws Exception {
        String token = login("user", "user");
        HttpResponse<String> r = authPost(token, "/auth/check/role", "{\"role\":\"admin\"}");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"has\":false"));
    }

    /**
     * \u5df2\u767b\u5f55\u65f6\u8bf7\u6c42 <code>POST /auth/check/role</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 401\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void checkRole_unauthenticated_shouldReturn401() throws Exception {
        HttpResponse<String> r = postJson("/auth/check/role", "{\"role\":\"admin\"}");
        assertEquals(401, r.statusCode());
    }

    /**
     * \u4f7f\u7528\u7ba1\u7406\u5458\u4f1a\u8bdd\u65f6\u8bf7\u6c42 <code>GET /auth/admin</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void adminArea_asAdmin_shouldReturn200() throws Exception {
        String token = login("admin", "admin");
        HttpResponse<String> r = authGet(token, "/auth/admin");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("admin area accessed"));
    }

    /**
     * \u4f7f\u7528\u666e\u901a\u7528\u6237\u4f1a\u8bdd\u65f6\u8bf7\u6c42 <code>GET /auth/admin</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 403\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void adminArea_asUser_shouldReturn403() throws Exception {
        String token = login("user", "user");
        HttpResponse<String> r = authGet(token, "/auth/admin");
        assertEquals(403, r.statusCode());
    }

    /**
     * \u9a8c\u8bc1<code>r.statusCode()</code> \u7b49\u4e8e <code>401</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void adminArea_unauthenticated_shouldReturn401() throws Exception {
        HttpResponse<String> r = get("/auth/admin");
        assertEquals(401, r.statusCode());
    }

    /**
     * \u4f7f\u7528\u7ba1\u7406\u5458\u4f1a\u8bdd\u65f6\u8bf7\u6c42 <code>GET /auth/manager</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 403\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void managerArea_asAdmin_shouldReturn403() throws Exception {
        String token = login("admin", "admin");
        HttpResponse<String> r = authGet(token, "/auth/manager");
        assertEquals(403, r.statusCode());
    }

    /**
     * \u9a8c\u8bc1<code>r.statusCode()</code> \u7b49\u4e8e <code>401</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void managerArea_unauthenticated_shouldReturn401() throws Exception {
        HttpResponse<String> r = get("/auth/manager");
        assertEquals(401, r.statusCode());
    }

    // =================== 权限鉴权 ===================

    /**
     * \u8bf7\u6c42 <code>POST /auth/check/permission</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void checkPermission_adminHasOrderPay_shouldReturnTrue() throws Exception {
        String token = login("admin", "admin");
        HttpResponse<String> r = authPost(token, "/auth/check/permission", "{\"permission\":\"order:pay\"}");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"has\":true"));
    }

    /**
     * \u8bf7\u6c42 <code>POST /auth/check/permission</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void checkPermission_userLacksOrderPay_shouldReturnFalse() throws Exception {
        String token = login("user", "user");
        HttpResponse<String> r = authPost(token, "/auth/check/permission", "{\"permission\":\"order:pay\"}");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"has\":false"));
    }

    /**
     * \u5df2\u767b\u5f55\u65f6\u8bf7\u6c42 <code>POST /auth/check/permission</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 401\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void checkPermission_unauthenticated_shouldReturn401() throws Exception {
        HttpResponse<String> r = postJson("/auth/check/permission", "{\"permission\":\"order:pay\"}");
        assertEquals(401, r.statusCode());
    }

    /**
     * \u4f7f\u7528\u7ba1\u7406\u5458\u4f1a\u8bdd\u65f6\u8bf7\u6c42 <code>GET /auth/users</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void usersList_asAdmin_shouldReturn200() throws Exception {
        String token = login("admin", "admin");
        HttpResponse<String> r = authGet(token, "/auth/users");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("user list accessed"));
    }

    /**
     * \u4f7f\u7528\u666e\u901a\u7528\u6237\u4f1a\u8bdd\u65f6\u8bf7\u6c42 <code>GET /auth/users</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void usersList_asUser_shouldReturn200() throws Exception {
        String token = login("user", "user");
        HttpResponse<String> r = authGet(token, "/auth/users");
        assertEquals(200, r.statusCode());
    }

    /**
     * \u9a8c\u8bc1<code>r.statusCode()</code> \u7b49\u4e8e <code>401</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void usersList_unauthenticated_shouldReturn401() throws Exception {
        HttpResponse<String> r = get("/auth/users");
        assertEquals(401, r.statusCode());
    }

    /**
     * \u4f7f\u7528\u7ba1\u7406\u5458\u4f1a\u8bdd\u65f6\u8bf7\u6c42 <code>POST /auth/orders/ORD-001/pay</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
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
     * \u4f7f\u7528\u666e\u901a\u7528\u6237\u4f1a\u8bdd\u65f6\u8bf7\u6c42 <code>POST /auth/orders/ORD-001/pay</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 403\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void orderPay_asUser_shouldReturn403() throws Exception {
        String token = login("user", "user");
        HttpResponse<String> r = authPost(token, "/auth/orders/ORD-001/pay", "");
        assertEquals(403, r.statusCode());
    }

    /**
     * \u5df2\u767b\u5f55\u65f6\u8bf7\u6c42 <code>POST /auth/orders/ORD-001/pay</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 401\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void orderPay_unauthenticated_shouldReturn401() throws Exception {
        HttpResponse<String> r = postJson("/auth/orders/ORD-001/pay", "");
        assertEquals(401, r.statusCode());
    }

    // =================== 组合鉴权 ===================

    /**
     * \u4f7f\u7528\u7ba1\u7406\u5458\u4f1a\u8bdd\u65f6\u8bf7\u6c42 <code>DELETE /auth/users/10002</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void deleteUser_asAdmin_shouldReturn200() throws Exception {
        String token = login("admin", "admin");
        HttpResponse<String> r = authDelete(token, "/auth/users/10002");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"success\":true"));
    }

    /**
     * \u4f7f\u7528\u666e\u901a\u7528\u6237\u4f1a\u8bdd\u65f6\u8bf7\u6c42 <code>DELETE /auth/users/10001</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 403\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void deleteUser_asUser_shouldReturn403() throws Exception {
        String token = login("user", "user");
        HttpResponse<String> r = authDelete(token, "/auth/users/10001");
        assertEquals(403, r.statusCode());
    }

    /**
     * \u9a8c\u8bc1<code>r.statusCode()</code> \u7b49\u4e8e <code>401</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void deleteUser_unauthenticated_shouldReturn401() throws Exception {
        HttpResponse<String> r = delete("/auth/users/10001");
        assertEquals(401, r.statusCode());
    }
}
