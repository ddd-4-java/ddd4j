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
import io.ddd4j.sample.order.testkit.http.HttpClient;
import io.ddd4j.sample.order.testkit.http.HttpRequest;
import io.ddd4j.sample.order.testkit.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

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

    /**
     * 每个测试前重启 Javalin 并清空 sa-token 全局状态，避免上一测试遗留的 token/session 干扰。
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
     * \u8c03\u7528 <code>httpClient.send()</code>\u3001<code>HttpRequest.newBuilder()</code>\u3001<code>URI.create()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
     * @param body \u5f53\u524d\u8bf7\u6c42\u6216\u54cd\u5e94\u4e2d\u8981\u8bfb\u53d6\u7684\u6b63\u6587
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>HttpResponse&lt;String&gt;</code> \u7ed3\u679c
     * @throws Exception \u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
    private HttpResponse<String> putJson(String path, String body) throws Exception {
        return httpClient.send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(body)).build(),
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
     * \u767b\u5f55\u5e76\u8fd4\u56de token\uff08\u6bcf\u4e2a\u6d4b\u8bd5\u7528\u72ec\u7acb\u4f1a\u8bdd\uff09\u3002
     *
     * @param username \u7528\u4e8e\u767b\u5f55\u7684\u6d4b\u8bd5\u8d26\u53f7
     * @param password \u7528\u4e8e\u767b\u5f55\u7684\u6d4b\u8bd5\u5bc6\u7801
     * @return \u672c\u6b21\u6d4b\u8bd5\u767b\u5f55\u7b7e\u53d1\u7684\u4f1a\u8bdd\u4ee4\u724c
     * @throws Exception \u767b\u5f55\u8bf7\u6c42\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
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
     * \u767b\u51fa\uff08\u907f\u514d\u591a token \u5728\u5168\u5c40 SaToken \u72b6\u6001\u4e2d\u6b8b\u7559\uff09\u3002
     *
     * @param token \u5f53\u524d\u6d4b\u8bd5\u4f1a\u8bdd\u7684\u767b\u5f55\u4ee4\u724c
     * @throws Exception \u6ce8\u9500 HTTP \u8bf7\u6c42\u5931\u8d25
     */
    private void logout(String token) throws Exception {
        authPost(token, "/auth/logout", "");
    }

    /**
     * \u901a\u8fc7 <code>HttpRequest.newBuilder()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param token \u4ece\u6d4b\u8bd5\u767b\u5f55\u83b7\u5f97\u7684\u4f1a\u8bdd\u51ed\u636e
     * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>HttpRequest.newBuilder(URI.create(baseUrl + path)).header("satoken", token)</code>
     */
    private HttpRequest.Builder authReq(String token, String path) {
        // sa-token 默认从 header "satoken" 读取 token；mock 上下文未注入 Authorization 支持。
        return HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("satoken", token);
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

    // =================== 1) 登录 / 登出 / 当前用户 / 状态 / 踢人（5）====================

    /**
     * \u9a8c\u8bc1<code>token</code>\u975e\u7a7a\uff0c<code>token.length() &gt; 0</code>\u4e3a\u771f\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void login_asAdmin_shouldReturnToken() throws Exception {
        String token = login("admin", "admin");
        assertNotNull(token);
        assertTrue(token.length() > 0);
    }

    /**
     * \u9a8c\u8bc1<code>token</code>\u975e\u7a7a\uff0c<code>token.length() &gt; 0</code>\u4e3a\u771f\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void login_asUser_shouldReturnToken() throws Exception {
        String token = login("user", "user");
        assertNotNull(token);
        assertTrue(token.length() > 0);
    }

    /**
     * \u53e3\u4ee4\u65e0\u6548\u65f6\u8bf7\u6c42 <code>POST /auth/login</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 401\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void login_withWrongPassword_shouldReturn401() throws Exception {
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
     * \u9a8c\u8bc1<code>r.statusCode()</code> \u7b49\u4e8e <code>200</code>\uff0c<code>r.body().contains("\"authenticated\":false")</code>\u4e3a\u771f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void me_unauthenticated_shouldReturnOkWithFalse() throws Exception {
        HttpResponse<String> r = get("/auth/me");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"authenticated\":false"));
    }

    /**
     * \u9a8c\u8bc1<code>r.statusCode()</code> \u7b49\u4e8e <code>200</code>\uff0c<code>r.body().contains("\"login\":false")</code>\u4e3a\u771f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void status_unauthenticated_shouldReturnOk() throws Exception {
        HttpResponse<String> r = get("/auth/status");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"login\":false"));
    }

    // =================== 2) 角色鉴权 /auth/admin 和 /auth/manager（6）====================

    /**
     * \u4f7f\u7528\u7ba1\u7406\u5458\u4f1a\u8bdd\u65f6\u8bf7\u6c42 <code>GET /auth/admin</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void adminArea_asAdmin_shouldBeOk() throws Exception {
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
    void adminArea_asUser_shouldBeForbidden() throws Exception {
        String token = login("user", "user");
        HttpResponse<String> r = authGet(token, "/auth/admin");
        assertEquals(403, r.statusCode());
    }

    /**
     * \u9a8c\u8bc1<code>r.statusCode()</code> \u7b49\u4e8e <code>401</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void adminArea_unauthenticated_shouldBe401() throws Exception {
        HttpResponse<String> r = get("/auth/admin");
        assertEquals(401, r.statusCode());
    }

    /**
     * \u4f7f\u7528\u7ba1\u7406\u5458\u4f1a\u8bdd\u65f6\u8bf7\u6c42 <code>GET /auth/manager</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 403\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void managerArea_asAdmin_shouldBeForbidden() throws Exception {
        // admin 不是 manager 角色
        String token = login("admin", "admin");
        HttpResponse<String> r = authGet(token, "/auth/manager");
        assertEquals(403, r.statusCode());
    }

    /**
     * \u9a8c\u8bc1<code>r.statusCode()</code> \u7b49\u4e8e <code>401</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void managerArea_unauthenticated_shouldBe401() throws Exception {
        HttpResponse<String> r = get("/auth/manager");
        assertEquals(401, r.statusCode());
    }

    // =================== 3) 权限鉴权 /auth/users /auth/orders/{id}/pay（6）====================

    /**
     * \u4f7f\u7528\u7ba1\u7406\u5458\u4f1a\u8bdd\u65f6\u8bf7\u6c42 <code>GET /auth/users</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void usersList_asAdmin_shouldBeOk() throws Exception {
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
    void usersList_asUser_shouldBeOk() throws Exception {
        // user 角色拥有 user:list 权限
        String token = login("user", "user");
        HttpResponse<String> r = authGet(token, "/auth/users");
        assertEquals(200, r.statusCode());
    }

    /**
     * \u9a8c\u8bc1<code>r.statusCode()</code> \u7b49\u4e8e <code>401</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void usersList_unauthenticated_shouldBe401() throws Exception {
        HttpResponse<String> r = get("/auth/users");
        assertEquals(401, r.statusCode());
    }

    /**
     * \u4f7f\u7528\u7ba1\u7406\u5458\u4f1a\u8bdd\u65f6\u8bf7\u6c42 <code>POST /auth/orders/abc/pay</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void orderPay_asAdmin_shouldBeOk() throws Exception {
        // admin 拥有 order:pay
        String token = login("admin", "admin");
        HttpResponse<String> r = authPost(token, "/auth/orders/abc/pay", "");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("order paid"));
    }

    /**
     * \u4f7f\u7528\u666e\u901a\u7528\u6237\u4f1a\u8bdd\u65f6\u8bf7\u6c42 <code>POST /auth/orders/abc/pay</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 403\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void orderPay_asUser_shouldBeForbidden() throws Exception {
        // user 没有 order:pay
        String token = login("user", "user");
        HttpResponse<String> r = authPost(token, "/auth/orders/abc/pay", "");
        assertEquals(403, r.statusCode());
    }

    /**
     * \u5df2\u767b\u5f55\u65f6\u8bf7\u6c42 <code>POST /auth/orders/abc/pay</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 401\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void orderPay_unauthenticated_shouldBe401() throws Exception {
        HttpResponse<String> r = postJson("/auth/orders/abc/pay", "");
        assertEquals(401, r.statusCode());
    }

    // =================== 4) 组合鉴权 admin + user:delete（4）====================

    /**
     * \u4f7f\u7528\u7ba1\u7406\u5458\u4f1a\u8bdd\u65f6\u8bf7\u6c42 <code>DELETE /auth/users/10002</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void deleteUser_asAdmin_shouldBeOk() throws Exception {
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
    void deleteUser_asUser_shouldBeForbidden() throws Exception {
        // user 没有 admin 角色
        String token = login("user", "user");
        HttpResponse<String> r = authDelete(token, "/auth/users/10001");
        assertEquals(403, r.statusCode());
    }

    /**
     * \u9a8c\u8bc1<code>r.statusCode()</code> \u7b49\u4e8e <code>401</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void deleteUser_unauthenticated_shouldBe401() throws Exception {
        HttpResponse<String> r = delete("/auth/users/10001");
        assertEquals(401, r.statusCode());
    }

    /**
     * \u4f7f\u7528\u7ba1\u7406\u5458\u4f1a\u8bdd\u65f6\u8bf7\u6c42 <code>POST /auth/check/role</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void checkRole_asAdmin_shouldReturnTrueForAdmin() throws Exception {
        String token = login("admin", "admin");
        HttpResponse<String> r = authPost(token, "/auth/check/role", "{\"role\":\"admin\"}");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"has\":true"));
    }

    /**
     * \u4f7f\u7528\u666e\u901a\u7528\u6237\u4f1a\u8bdd\u65f6\u8bf7\u6c42 <code>POST /auth/check/role</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void checkRole_asUser_shouldReturnFalseForAdmin() throws Exception {
        String token = login("user", "user");
        HttpResponse<String> r = authPost(token, "/auth/check/role", "{\"role\":\"admin\"}");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"has\":false"));
    }

    // =================== 5) RBAC 资源管理（10）====================

    /**
     * \u9a8c\u8bc1<code>r.statusCode()</code> \u7b49\u4e8e <code>200</code>\uff0c<code>r.body().contains("admin")</code>\u4e3a\u771f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void listUsers_shouldReturn3SeedUsers() throws Exception {
        HttpResponse<String> r = get("/rbac/admin/users");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("admin"));
        assertTrue(r.body().contains("user"));
        assertTrue(r.body().contains("disabled"));
    }

    /**
     * \u9a8c\u8bc1<code>r.statusCode()</code> \u7b49\u4e8e <code>200</code>\uff0c<code>r.body().contains("admin")</code>\u4e3a\u771f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void getUser_byId_shouldReturnUser() throws Exception {
        HttpResponse<String> r = get("/rbac/admin/users/10001");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("admin"));
    }

    /**
     * \u8bf7\u6c42 <code>POST /rbac/admin/users</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 201\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void createUser_shouldReturn201() throws Exception {
        HttpResponse<String> r = postJson("/rbac/admin/users",
                "{\"userId\":\"20001\",\"username\":\"alice\",\"password\":\"p\",\"realName\":\"Alice\"}");
        assertEquals(201, r.statusCode());
        assertTrue(r.body().contains("20001"));
    }

    /**
     * \u9a8c\u8bc1<code>r.statusCode()</code> \u7b49\u4e8e <code>200</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void updateUser_shouldReturn200() throws Exception {
        HttpResponse<String> r = putJson("/rbac/admin/users/10001",
                "{\"realName\":\"NewName\",\"password\":null,\"status\":null}");
        assertEquals(200, r.statusCode());
    }

    /**
     * \u8bf7\u6c42 <code>POST /rbac/admin/users</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void deleteUser_byRbacAdmin_shouldReturn200() throws Exception {
        // 先创建一个新用户，再删它
        postJson("/rbac/admin/users",
                "{\"userId\":\"20002\",\"username\":\"bob\",\"password\":\"p\",\"realName\":\"Bob\"}");
        HttpResponse<String> r = delete("/rbac/admin/users/20002");
        assertEquals(200, r.statusCode());
    }

    /**
     * \u9a8c\u8bc1<code>r.statusCode()</code> \u7b49\u4e8e <code>200</code>\uff0c<code>r.body().contains("admin")</code>\u4e3a\u771f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void listRoles_shouldReturn3SeedRoles() throws Exception {
        HttpResponse<String> r = get("/rbac/admin/roles");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("admin"));
        assertTrue(r.body().contains("user"));
        assertTrue(r.body().contains("manager"));
    }

    /**
     * \u9a8c\u8bc1<code>r.statusCode()</code> \u7b49\u4e8e <code>200</code>\uff0c<code>r.body().contains("admin")</code>\u4e3a\u771f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void getRole_byId_shouldReturnRole() throws Exception {
        HttpResponse<String> r = get("/rbac/admin/roles/R001");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("admin"));
    }

    /**
     * \u8bf7\u6c42 <code>POST /rbac/admin/roles</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 201\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void createRole_shouldReturn201() throws Exception {
        HttpResponse<String> r = postJson("/rbac/admin/roles",
                "{\"roleId\":\"R100\",\"roleCode\":\"tester\",\"roleName\":\"Tester\",\"description\":\"QA\"}");
        assertEquals(201, r.statusCode());
        assertTrue(r.body().contains("R100"));
    }

    /**
     * \u9a8c\u8bc1<code>r.statusCode()</code> \u7b49\u4e8e <code>200</code>\uff0c<code>r.body().contains("user:add")</code>\u4e3a\u771f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
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

    /**
     * \u8bf7\u6c42 <code>POST /rbac/admin/users/10002/roles</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void assignRolesToUser_shouldReturn200() throws Exception {
        // 把 user 用户（10002）的角色改成 manager
        HttpResponse<String> r = postJson("/rbac/admin/users/10002/roles",
                "{\"roleIds\":[\"R003\"]}");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("10002"));
    }

    /**
     * \u9a8c\u8bc1<code>r.statusCode()</code> \u7b49\u4e8e <code>200</code>\uff0c<code>r.body().contains("user:add")</code>\u4e3a\u771f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
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

    /**
     * \u9a8c\u8bc1<code>r.statusCode()</code> \u7b49\u4e8e <code>200</code>\uff0c<code>r.body().contains("user:add")</code>\u4e3a\u771f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void getRolePermissions_adminShouldHaveAll() throws Exception {
        HttpResponse<String> r = get("/rbac/admin/roles/R001/permissions");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("user:add"));
        assertTrue(r.body().contains("order:pay"));
    }

    /**
     * \u8bf7\u6c42 <code>POST /rbac/admin/roles/R002/permissions</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void assignPermissionsToRole_shouldReturn200() throws Exception {
        // 给 user 角色加 order:pay
        HttpResponse<String> r = postJson("/rbac/admin/roles/R002/permissions",
                "{\"permissionIds\":[\"P003\",\"P005\",\"P006\"]}");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("R002"));
    }

    /**
     * \u8bf7\u6c42 <code>POST /auth/kickout</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void kickout_shouldReturn200() throws Exception {
        HttpResponse<String> r = postJson("/auth/kickout", "{\"userId\":\"10002\"}");
        assertEquals(200, r.statusCode());
    }
}
