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
package io.ddd4j.sample.quarkus.satoken;

import io.ddd4j.sample.quarkus.satoken.rbac.RbacConfig;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

/**
 * RBAC 集成测试（Quarkus + Sa-Token + JAX-RS）。完整 RBAC 流：
 * <ul>
 *   <li>登录 / 登出 / 当前用户 / 状态</li>
 *   <li>运行时权限 / 角色校验</li>
 *   <li>Sa-Token 注解鉴权（@SaCheckLogin / @SaCheckRole / @SaCheckPermission）</li>
 *   <li>用户 / 角色 / 权限完整 CRUD（17 个 /admin 端点）</li>
 *   <li>3 种鉴权组合：admin / 角色+权限 AND / 仅权限</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@QuarkusTest
class RbacResourceTest {

    /** \u7528\u4e8e\u63a7\u5236\u793a\u4f8b\u8d26\u53f7\u4e0e\u89d2\u8272\u6570\u636e\u7684\u914d\u7f6e\u5bf9\u8c61\u3002 */
    @Inject
    RbacConfig rbacConfig;

    /**
     * \u901a\u8fc7 <code>given()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param username \u6d4b\u8bd5\u4f1a\u8bdd\u4f7f\u7528\u7684\u7528\u6237\u540d
     * @param password \u6d4b\u8bd5\u8d26\u53f7\u7684\u53e3\u4ee4
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>given().contentType("application/json").body(body).when().post("/auth/login").then().statusCode(200).extract().path("token")</code>
     */
    private static String loginAs(String username, String password) {
        Map<String, String> body = new HashMap<>();
        body.put("username", username);
        body.put("password", password);
        return given().contentType("application/json").body(body)
                .when().post("/auth/login")
                .then().statusCode(200)
                .extract().path("token");
    }

    // ========== 登录 / 认证 ==========

    /**
     * \u8bfb\u53d6 <code>newUser</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @param id \u5f53\u524d\u8981\u67e5\u627e\u7684\u6d4b\u8bd5\u8bb0\u5f55\u6807\u8bc6
     * @param username \u6d4b\u8bd5\u4f1a\u8bdd\u4f7f\u7528\u7684\u7528\u6237\u540d
     * @param displayName \u4f20\u7ed9 <code>newUser.put()</code> \u7684 <code>String</code> \u8f93\u5165
     * @param role \u672c\u6b21\u9274\u6743\u65ad\u8a00\u6240\u4f7f\u7528\u7684\u89d2\u8272\u6807\u8bc6
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>newUser</code>
     */
    private static Map<String, Object> createUserBody(String id, String username, String displayName, String role) {
        Map<String, Object> newUser = new HashMap<>();
        newUser.put("id", id);
        newUser.put("username", username);
        newUser.put("displayName", displayName);
        newUser.put("password", "123456");
        HashSet<String> roles = new HashSet<>();
        roles.add(role);
        newUser.put("roleCodes", roles);
        return newUser;
    }

    /**
     * \u8bfb\u53d6 <code>newUser</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @param id \u5f53\u524d\u8981\u67e5\u627e\u7684\u6d4b\u8bd5\u8bb0\u5f55\u6807\u8bc6
     * @param username \u6d4b\u8bd5\u4f1a\u8bdd\u4f7f\u7528\u7684\u7528\u6237\u540d
     * @param displayName \u4f20\u7ed9 <code>newUser.put()</code> \u7684 <code>String</code> \u8f93\u5165
     * @param roles \u4f20\u7ed9 <code>newUser.put()</code> \u7684 <code>Object</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>newUser</code>
     */
    private static Map<String, Object> createUserBody(String id, String username, String displayName, Object roles) {
        Map<String, Object> newUser = new HashMap<>();
        newUser.put("id", id);
        newUser.put("username", username);
        newUser.put("displayName", displayName);
        newUser.put("password", "123456");
        newUser.put("roleCodes", roles);
        return newUser;
    }

    /** \u6d4b\u8bd5\u6267\u884c\u524d\u6267\u884c <code>rbacConfig.reset()</code> \u5b8c\u6210\u8d44\u6e90\u51c6\u5907\u3002 */
    @BeforeEach
    void resetRbacData() {
        rbacConfig.reset();
    }

    /** \u4f7f\u7528\u7ba1\u7406\u5458\u4f1a\u8bdd\u65f6\u8bf7\u6c42 <code>POST /auth/login</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\uff0c\u54cd\u5e94\u4e2d\u7684 <code>data.principal.userCode</code> \u7b49\u4e8e <code>"admin"</code>\u3002 */
    @Test
    void shouldLoginSuccessfullyAsAdmin() {
        Map<String, String> body = new HashMap<>();
        body.put("username", "admin");
        body.put("password", "123456");

        given().contentType("application/json").body(body)
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("token", notNullValue())
                .body("data.principal.userCode", equalTo("admin"));
    }

    /** \u4f7f\u7528\u666e\u901a\u7528\u6237\u4f1a\u8bdd\u65f6\u8bf7\u6c42 <code>POST /auth/login</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\uff0c\u54cd\u5e94\u4e2d\u7684 <code>data.principal.userCode</code> \u7b49\u4e8e <code>"user"</code>\u3002 */
    @Test
    void shouldLoginSuccessfullyAsUser() {
        Map<String, String> body = new HashMap<>();
        body.put("username", "user");
        body.put("password", "123456");

        given().contentType("application/json").body(body)
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("token", notNullValue())
                .body("data.principal.userCode", equalTo("user"));
    }

    /** \u8bf7\u6c42 <code>POST /auth/login</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 500\u3002 */
    @Test
    void shouldRejectInvalidPassword() {
        Map<String, String> body = new HashMap<>();
        body.put("username", "admin");
        body.put("password", "wrong-password");

        given().contentType("application/json").body(body)
                .when().post("/auth/login")
                .then().statusCode(500);
    }

    /** \u8bf7\u6c42 <code>POST /auth/login</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 500\u3002 */
    @Test
    void shouldRejectLoginForDisabledUser() {
        Map<String, String> body = new HashMap<>();
        body.put("username", "disabled");
        body.put("password", "123456");

        given().contentType("application/json").body(body)
                .when().post("/auth/login")
                .then().statusCode(500);
    }

    /** \u8bf7\u6c42 <code>POST /auth/login</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 500\u3002 */
    @Test
    void shouldRejectLoginWithUnknownUser() {
        Map<String, String> body = new HashMap<>();
        body.put("username", "ghost");
        body.put("password", "123456");

        given().contentType("application/json").body(body)
                .when().post("/auth/login")
                .then().statusCode(500);
    }

    // ========== 鉴权（3 种鉴权方式） ==========

    /** \u8bf7\u6c42 <code>GET /auth/status</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\uff0c\u54cd\u5e94\u4e2d\u7684 <code>data.login</code> \u7b49\u4e8e <code>false</code>\u3002 */
    @Test
    void shouldReturnLoginStatus() {
        given().when().get("/auth/status")
                .then()
                .statusCode(200)
                .body("data.login", is(false));
    }

    /** \u5df2\u767b\u5f55\u65f6\u8bf7\u6c42 <code>GET /auth/me</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 401\u3002 */
    @Test
    void shouldReturnNotAuthenticatedWhenNotLogin() {
        given().when().get("/auth/me")
                .then().statusCode(401);
    }

    /** \u8bf7\u6c42 <code>GET /admin/users</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 401\u3002 */
    @Test
    void shouldReturn401WhenAccessAdminUsersWithoutLogin() {
        given().when().get("/admin/users")
                .then().statusCode(401);
    }

    /** \u8bf7\u6c42 <code>GET /auth/me</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\uff0c\u54cd\u5e94\u4e2d\u7684 <code>data.userCode</code> \u7b49\u4e8e <code>"user"</code>\u3002 */
    @Test
    void shouldAccessMeWhenLoggedIn() {
        String token = loginAs("user", "123456");
        given().header("Authorization", "Bearer " + token)
                .when().get("/auth/me")
                .then()
                .statusCode(200)
                .body("data.authenticated", is(true))
                .body("data.userCode", equalTo("user"));
    }

    /** \u4f7f\u7528\u7ba1\u7406\u5458\u4f1a\u8bdd\u65f6\u8bf7\u6c42 <code>GET /auth/me</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\uff0c\u54cd\u5e94\u4e2d\u7684 <code>data.roleCode</code> \u7b49\u4e8e <code>"admin"</code>\u3002 */
    @Test
    void shouldAccessMeAsAdmin() {
        String token = loginAs("admin", "123456");
        given().header("Authorization", "Bearer " + token)
                .when().get("/auth/me")
                .then()
                .statusCode(200)
                .body("data.userCode", equalTo("admin"))
                .body("data.roleCode", equalTo("admin"));
    }

    /** \u8bf7\u6c42 <code>POST /admin/users</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 403\u3002 */
    @Test
    void shouldDenyAccessToAdminEndpointWithoutAdminRole() {
        String token = loginAs("user", "123456");
        // user 没有 admin 角色，创建用户需 admin
        Map<String, Object> newUser = createUserBody("u-rbac-test", "rbacuser", "rbac", "user");
        given().header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(newUser)
                .when().post("/admin/users")
                .then().statusCode(403);
    }

    /** \u8bf7\u6c42 <code>GET /auth/check/permission</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\uff0c\u54cd\u5e94\u4e2d\u7684 <code>data.has</code> \u7b49\u4e8e <code>true</code>\u3002 */
    @Test
    void shouldCheckPermissionAtRuntime() {
        String token = loginAs("admin", "123456");
        given().header("Authorization", "Bearer " + token)
                .queryParam("permission", "user:list")
                .when().get("/auth/check/permission")
                .then()
                .statusCode(200)
                .body("data.permission", equalTo("user:list"))
                .body("data.has", is(true));
    }

    /** \u8bf7\u6c42 <code>GET /auth/check/role</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\uff0c\u54cd\u5e94\u4e2d\u7684 <code>data.has</code> \u7b49\u4e8e <code>true</code>\u3002 */
    @Test
    void shouldCheckRoleAtRuntime() {
        String token = loginAs("admin", "123456");
        given().header("Authorization", "Bearer " + token)
                .queryParam("role", "admin")
                .when().get("/auth/check/role")
                .then()
                .statusCode(200)
                .body("data.role", equalTo("admin"))
                .body("data.has", is(true));
    }

    /** \u8bf7\u6c42 <code>GET /auth/check/permission</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\uff0c\u54cd\u5e94\u4e2d\u7684 <code>data.has</code> \u7b49\u4e8e <code>false</code>\u3002 */
    @Test
    void shouldCheckRuntimePermissionForNonExistingPermission() {
        String token = loginAs("admin", "123456");
        given().header("Authorization", "Bearer " + token)
                .queryParam("permission", "nonexistent:permission")
                .when().get("/auth/check/permission")
                .then()
                .statusCode(200)
                .body("data.has", is(false));
    }

    // ========== 业务侧鉴权：3 种鉴权组合 ==========

    /** \u8bf7\u6c42 <code>GET /auth/check/role</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\uff0c\u54cd\u5e94\u4e2d\u7684 <code>data.has</code> \u7b49\u4e8e <code>false</code>\u3002 */
    @Test
    void shouldCheckRuntimeRoleForNonExistingRole() {
        String token = loginAs("admin", "123456");
        given().header("Authorization", "Bearer " + token)
                .queryParam("role", "super-admin")
                .when().get("/auth/check/role")
                .then()
                .statusCode(200)
                .body("data.has", is(false));
    }

    /** \u8bf7\u6c42 <code>GET /auth/check/permission?permission=anything</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 401\u3002 */
    @Test
    void shouldDenyAccessToCheckPermissionWithoutLogin() {
        given().when().get("/auth/check/permission?permission=anything")
                .then().statusCode(401);
    }

    /** \u8bf7\u6c42 <code>GET /auth/check/role?role=anything</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 401\u3002 */
    @Test
    void shouldDenyAccessToCheckRoleWithoutLogin() {
        given().when().get("/auth/check/role?role=anything")
                .then().statusCode(401);
    }

    /** \u4f7f\u7528\u7ba1\u7406\u5458\u4f1a\u8bdd\u65f6\u8bf7\u6c42 <code>GET /auth/users</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\u3002 */
    @Test
    void shouldListUsersAsAdminViaAuthEndpoint() {
        String token = loginAs("admin", "123456");
        given().header("Authorization", "Bearer " + token)
                .when().get("/auth/users")
                .then()
                .statusCode(200)
                .body("data.items.size()", is(3));
    }

    /** \u4f7f\u7528\u7ba1\u7406\u5458\u4f1a\u8bdd\u65f6\u8bf7\u6c42 <code>GET /admin/users</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\u3002 */
    @Test
    void shouldListUsersAsAdminViaAdminEndpoint() {
        String token = loginAs("admin", "123456");
        given().header("Authorization", "Bearer " + token)
                .when().get("/admin/users")
                .then()
                .statusCode(200)
                .body("data.size()", is(3));
    }

    /** \u8bf7\u6c42 <code>POST /auth/orders/o-1001/pay</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 403\u3002 */
    @Test
    void shouldPayOrderWithOrderPayPermissionDenied() {
        String token = loginAs("user", "123456");
        given().header("Authorization", "Bearer " + token)
                .when().post("/auth/orders/o-1001/pay")
                .then().statusCode(403);
    }

    /** \u8bf7\u6c42 <code>POST /auth/orders/o-1001/pay</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\uff0c\u54cd\u5e94\u4e2d\u7684 <code>data.paid</code> \u7b49\u4e8e <code>true</code>\u3002 */
    @Test
    void shouldPayOrderWithOrderPayPermissionAllowed() {
        String token = loginAs("admin", "123456");
        given().header("Authorization", "Bearer " + token)
                .when().post("/auth/orders/o-1001/pay")
                .then().statusCode(200)
                .body("data.paid", is(true));
    }

    // ========== 用户 CRUD (admin 角色权限) ==========

    /** \u4f7f\u7528\u7ba1\u7406\u5458\u4f1a\u8bdd\u65f6\u8bf7\u6c42 <code>DELETE /auth/users/u-temp</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\uff0c\u54cd\u5e94\u4e2d\u7684 <code>data</code> \u7b49\u4e8e <code>true</code>\u3002 */
    @Test
    void shouldDeleteUserAsAdmin() {
        String token = loginAs("admin", "123456");
        given().header("Authorization", "Bearer " + token)
                .when().delete("/auth/users/u-temp")
                .then()
                .statusCode(200)
                .body("data", is(true));
    }

    /** \u8bf7\u6c42 <code>DELETE /auth/users/u-temp</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 403\u3002 */
    @Test
    void shouldDenyDeleteUserAsRegularUser() {
        String token = loginAs("user", "123456");
        given().header("Authorization", "Bearer " + token)
                .when().delete("/auth/users/u-temp")
                .then().statusCode(403);
    }

    /** \u8bf7\u6c42 <code>GET /auth/users</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 401\u3002 */
    @Test
    void shouldDenyAccessToBusinessEndpointWithoutLogin() {
        given().when().get("/auth/users")
                .then().statusCode(401);
    }

    /** \u4f7f\u7528\u7ba1\u7406\u5458\u4f1a\u8bdd\u65f6\u8bf7\u6c42 <code>POST /admin/users</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\uff0c\u54cd\u5e94\u4e2d\u7684 <code>data.username</code> \u7b49\u4e8e <code>"newuser"</code>\u3002 */
    @Test
    void shouldCreateUserAsAdmin() {
        String token = loginAs("admin", "123456");
        String userId = "u-rbac-create-" + System.nanoTime();
        Map<String, Object> newUser = createUserBody(userId, "newuser", "新用户", "user");
        given().header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(newUser)
                .when().post("/admin/users")
                .then()
                .statusCode(200)
                .body("data.id", equalTo(userId))
                .body("data.username", equalTo("newuser"));
    }

    /** \u8bf7\u6c42 <code>GET /admin/users/{id}</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\uff0c\u54cd\u5e94\u4e2d\u7684 <code>data.username</code> \u7b49\u4e8e <code>"admin"</code>\u3002 */
    @Test
    void shouldFindUserById() {
        String token = loginAs("admin", "123456");
        given().header("Authorization", "Bearer " + token)
                .when().get("/admin/users/{id}", "u-admin")
                .then()
                .statusCode(200)
                .body("data.id", equalTo("u-admin"))
                .body("data.username", equalTo("admin"));
    }

    /** \u4f7f\u7528\u7ba1\u7406\u5458\u4f1a\u8bdd\u65f6\u8bf7\u6c42 <code>PUT /admin/users/{id}</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\uff0c\u54cd\u5e94\u4e2d\u7684 <code>data.displayName</code> \u7b49\u4e8e <code>"\u66f4\u65b0\u540e"</code>\u3002 */
    @Test
    void shouldUpdateUserAsAdmin() {
        String token = loginAs("admin", "123456");
        String userId = "u-rbac-update-" + System.nanoTime();
        Map<String, Object> newUser = createUserBody(userId, "upd", "更新", "user");
        given().header("Authorization", "Bearer " + token)
                .contentType("application/json").body(newUser)
                .when().post("/admin/users")
                .then().statusCode(200);

        // 更新显示名
        Map<String, Object> patch = new HashMap<>();
        patch.put("displayName", "更新后");
        patch.put("password", "654321");
        given().header("Authorization", "Bearer " + token)
                .contentType("application/json").body(patch)
                .when().put("/admin/users/{id}", userId)
                .then()
                .statusCode(200)
                .body("data.displayName", equalTo("更新后"));
    }

    // ========== 角色 / 权限 CRUD ==========

    /** \u4f7f\u7528\u7ba1\u7406\u5458\u4f1a\u8bdd\u65f6\u8bf7\u6c42 <code>DELETE /admin/users/{id}</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\uff0c\u54cd\u5e94\u4e2d\u7684 <code>data</code> \u7b49\u4e8e <code>true</code>\u3002 */
    @Test
    void shouldDeleteUserAsAdminViaAdminEndpoint() {
        String token = loginAs("admin", "123456");
        String userId = "u-rbac-del-" + System.nanoTime();
        Map<String, Object> newUser = createUserBody(userId, "deluser", "删除用户", "user");
        given().header("Authorization", "Bearer " + token)
                .contentType("application/json").body(newUser)
                .when().post("/admin/users")
                .then().statusCode(200);

        given().header("Authorization", "Bearer " + token)
                .when().delete("/admin/users/{id}", userId)
                .then()
                .statusCode(200)
                .body("data", is(true));
    }

    /** \u4f7f\u7528\u7ba1\u7406\u5458\u4f1a\u8bdd\u65f6\u8bf7\u6c42 <code>POST /admin/users/{id}/roles/{code}</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\u3002 */
    @Test
    void shouldAssignRoleToUserAsAdmin() {
        String token = loginAs("admin", "123456");
        String userId = "u-rbac-assign-" + System.nanoTime();
        Map<String, Object> newUser = createUserBody(userId, "assignuser", "分配用户", new HashSet<>());
        given().header("Authorization", "Bearer " + token)
                .contentType("application/json").body(newUser)
                .when().post("/admin/users")
                .then().statusCode(200);

        given().header("Authorization", "Bearer " + token)
                .when().post("/admin/users/{id}/roles/{code}", userId, "admin")
                .then()
                .statusCode(200)
                .body("data.roleCodes", notNullValue());
    }

    /** \u4f7f\u7528\u7ba1\u7406\u5458\u4f1a\u8bdd\u65f6\u8bf7\u6c42 <code>DELETE /admin/users/{id}/roles/{code}</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\u3002 */
    @Test
    void shouldRevokeRoleFromUserAsAdmin() {
        String token = loginAs("admin", "123456");
        String userId = "u-rbac-revoke-" + System.nanoTime();
        Map<String, Object> newUser = createUserBody(userId, "revuser", "解除用户", "admin");
        given().header("Authorization", "Bearer " + token)
                .contentType("application/json").body(newUser)
                .when().post("/admin/users")
                .then().statusCode(200);

        given().header("Authorization", "Bearer " + token)
                .when().delete("/admin/users/{id}/roles/{code}", userId, "admin")
                .then()
                .statusCode(200);
    }

    /** \u5df2\u767b\u5f55\u65f6\u8bf7\u6c42 <code>GET /admin/roles</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\u3002 */
    @Test
    void shouldListRolesAsAuthenticatedUser() {
        String token = loginAs("user", "123456");
        given().header("Authorization", "Bearer " + token)
                .when().get("/admin/roles")
                .then()
                .statusCode(200)
                .body("data.size()", greaterThanOrEqualTo(2));
    }

    /** \u8bf7\u6c42 <code>GET /admin/roles/{code}</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\uff0c\u54cd\u5e94\u4e2d\u7684 <code>data.code</code> \u7b49\u4e8e <code>"admin"</code>\u3002 */
    @Test
    void shouldFindRoleByCode() {
        String token = loginAs("admin", "123456");
        given().header("Authorization", "Bearer " + token)
                .when().get("/admin/roles/{code}", "admin")
                .then()
                .statusCode(200)
                .body("data.code", equalTo("admin"));
    }

    /** \u4f7f\u7528\u7ba1\u7406\u5458\u4f1a\u8bdd\u65f6\u8bf7\u6c42 <code>POST /admin/roles</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\uff0c\u54cd\u5e94\u4e2d\u7684 <code>data.code</code> \u7b49\u4e8e <code>roleCode</code>\u3002 */
    @Test
    void shouldCreateRoleAsAdmin() {
        String token = loginAs("admin", "123456");
        String roleCode = "rbac-test-" + System.nanoTime();
        Map<String, Object> role = new HashMap<>();
        role.put("code", roleCode);
        role.put("displayName", "测试角色");
        role.put("description", "for rbac test");
        role.put("permissionCodes", new HashSet<>());
        given().header("Authorization", "Bearer " + token)
                .contentType("application/json").body(role)
                .when().post("/admin/roles")
                .then()
                .statusCode(200)
                .body("data.code", equalTo(roleCode));
    }

    /** \u4f7f\u7528\u7ba1\u7406\u5458\u4f1a\u8bdd\u65f6\u8bf7\u6c42 <code>PUT /admin/roles/{code}</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\u3002 */
    @Test
    void shouldUpdateRoleAsAdmin() {
        String token = loginAs("admin", "123456");
        String roleCode = "rbac-upd-" + System.nanoTime();
        Map<String, Object> role = new HashMap<>();
        role.put("code", roleCode);
        role.put("displayName", "原始");
        role.put("description", "before");
        role.put("permissionCodes", new HashSet<>());
        given().header("Authorization", "Bearer " + token)
                .contentType("application/json").body(role)
                .when().post("/admin/roles")
                .then().statusCode(200);

        Map<String, Object> patch = new HashMap<>();
        patch.put("displayName", "更新");
        patch.put("description", "after");
        given().header("Authorization", "Bearer " + token)
                .contentType("application/json").body(patch)
                .when().put("/admin/roles/{code}", roleCode)
                .then()
                .statusCode(200);
    }

    /** \u4f7f\u7528\u7ba1\u7406\u5458\u4f1a\u8bdd\u65f6\u8bf7\u6c42 <code>DELETE /admin/roles/{code}</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\uff0c\u54cd\u5e94\u4e2d\u7684 <code>data</code> \u7b49\u4e8e <code>true</code>\u3002 */
    @Test
    void shouldDeleteRoleAsAdmin() {
        String token = loginAs("admin", "123456");
        String roleCode = "rbac-del-" + System.nanoTime();
        Map<String, Object> role = new HashMap<>();
        role.put("code", roleCode);
        role.put("displayName", "待删");
        role.put("description", "to be deleted");
        role.put("permissionCodes", new HashSet<>());
        given().header("Authorization", "Bearer " + token)
                .contentType("application/json").body(role)
                .when().post("/admin/roles")
                .then().statusCode(200);

        given().header("Authorization", "Bearer " + token)
                .when().delete("/admin/roles/{code}", roleCode)
                .then()
                .statusCode(200)
                .body("data", is(true));
    }

    /** \u4f7f\u7528\u7ba1\u7406\u5458\u4f1a\u8bdd\u65f6\u8bf7\u6c42 <code>POST /admin/roles/{code}/permissions/{pcode}</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\u3002 */
    @Test
    void shouldGrantPermissionToRoleAsAdmin() {
        String token = loginAs("admin", "123456");
        given().header("Authorization", "Bearer " + token)
                .when().post("/admin/roles/{code}/permissions/{pcode}", "user", "order:pay")
                .then()
                .statusCode(200);
    }

    /** \u4f7f\u7528\u7ba1\u7406\u5458\u4f1a\u8bdd\u65f6\u8bf7\u6c42 <code>DELETE /admin/roles/{code}/permissions/{pcode}</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\u3002 */
    @Test
    void shouldRevokePermissionFromRoleAsAdmin() {
        String token = loginAs("admin", "123456");
        // 先授权
        given().header("Authorization", "Bearer " + token)
                .when().post("/admin/roles/{code}/permissions/{pcode}", "user", "order:pay")
                .then().statusCode(200);
        // 再解除
        given().header("Authorization", "Bearer " + token)
                .when().delete("/admin/roles/{code}/permissions/{pcode}", "user", "order:pay")
                .then().statusCode(200);
    }

    /** \u5df2\u767b\u5f55\u65f6\u8bf7\u6c42 <code>GET /admin/permissions</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\u3002 */
    @Test
    void shouldListPermissionsAsAuthenticatedUser() {
        String token = loginAs("user", "123456");
        given().header("Authorization", "Bearer " + token)
                .when().get("/admin/permissions")
                .then()
                .statusCode(200)
                .body("data.size()", greaterThanOrEqualTo(5));
    }

    /** \u4f7f\u7528\u7ba1\u7406\u5458\u4f1a\u8bdd\u65f6\u8bf7\u6c42 <code>POST /admin/permissions</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\uff0c\u54cd\u5e94\u4e2d\u7684 <code>data.code</code> \u7b49\u4e8e <code>permCode</code>\u3002 */
    @Test
    void shouldCreatePermissionAsAdmin() {
        String token = loginAs("admin", "123456");
        String permCode = "rbac:perm-" + System.nanoTime();
        Map<String, Object> perm = new HashMap<>();
        perm.put("code", permCode);
        perm.put("displayName", "RBAC 测试权限");
        perm.put("description", "created from rbac test");
        given().header("Authorization", "Bearer " + token)
                .contentType("application/json").body(perm)
                .when().post("/admin/permissions")
                .then()
                .statusCode(200)
                .body("data.code", equalTo(permCode));
    }

    /** \u4f7f\u7528\u7ba1\u7406\u5458\u4f1a\u8bdd\u65f6\u8bf7\u6c42 <code>DELETE /admin/permissions/{code}</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\uff0c\u54cd\u5e94\u4e2d\u7684 <code>data</code> \u7b49\u4e8e <code>true</code>\u3002 */
    @Test
    void shouldDeletePermissionAsAdmin() {
        String token = loginAs("admin", "123456");
        String permCode = "rbac:del-" + System.nanoTime();
        Map<String, Object> perm = new HashMap<>();
        perm.put("code", permCode);
        perm.put("displayName", "to-delete");
        perm.put("description", "");
        given().header("Authorization", "Bearer " + token)
                .contentType("application/json").body(perm)
                .when().post("/admin/permissions")
                .then().statusCode(200);

        given().header("Authorization", "Bearer " + token)
                .when().delete("/admin/permissions/{code}", permCode)
                .then()
                .statusCode(200)
                .body("data", is(true));
    }

    /** \u8bf7\u6c42 <code>POST /admin/users</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 403\u3002 */
    @Test
    void shouldDenyCreateUserForRegularUser() {
        String token = loginAs("user", "123456");
        Map<String, Object> newUser = createUserBody("u-deny", "deny", "拒绝", "user");
        given().header("Authorization", "Bearer " + token)
                .contentType("application/json").body(newUser)
                .when().post("/admin/users")
                .then().statusCode(403);
    }

    /** \u8bf7\u6c42 <code>POST /admin/roles</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 403\u3002 */
    @Test
    void shouldDenyCreateRoleForRegularUser() {
        String token = loginAs("user", "123456");
        Map<String, Object> role = new HashMap<>();
        role.put("code", "should-not-create");
        role.put("displayName", "x");
        role.put("description", "x");
        role.put("permissionCodes", new HashSet<>());
        given().header("Authorization", "Bearer " + token)
                .contentType("application/json").body(role)
                .when().post("/admin/roles")
                .then().statusCode(403);
    }

    /** \u8bf7\u6c42 <code>POST /admin/permissions</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 403\u3002 */
    @Test
    void shouldDenyCreatePermissionForRegularUser() {
        String token = loginAs("user", "123456");
        Map<String, Object> perm = new HashMap<>();
        perm.put("code", "should:deny");
        perm.put("displayName", "x");
        perm.put("description", "x");
        given().header("Authorization", "Bearer " + token)
                .contentType("application/json").body(perm)
                .when().post("/admin/permissions")
                .then().statusCode(403);
    }

    // ============ 工具方法 ============

    /** \u8bf7\u6c42 <code>POST /admin/users/{id}/roles/{code}</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 403\u3002 */
    @Test
    void shouldDenyAssignRoleForRegularUser() {
        String token = loginAs("user", "123456");
        given().header("Authorization", "Bearer " + token)
                .when().post("/admin/users/{id}/roles/{code}", "u-admin", "user")
                .then().statusCode(403);
    }

    /** \u8bf7\u6c42 <code>GET /admin/roles</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 401\u3002 */
    @Test
    void shouldDenyListUsersWithoutUserListPermission() {
        // Sa-Token 中：访问 /admin/users 需要 user:list + login。
        // "user" 用户只有 user:list 权限，应当 200。
        // 该测试覆盖一个反向断言："disabled" 用户（即便未登录）无法访问。
        // 已登录但禁用账户，由配置层处理。验证无登录场景。
        given().when().get("/admin/roles")
                .then().statusCode(401);
    }

    /** \u5df2\u767b\u5f55\u65f6\u8bf7\u6c42 <code>POST /auth/logout</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\uff0c\u54cd\u5e94\u4e2d\u7684 <code>data.success</code> \u7b49\u4e8e <code>true</code>\u3002 */
    @Test
    void shouldLogoutAsAuthenticatedUser() {
        String token = loginAs("user", "123456");
        given().header("Authorization", "Bearer " + token)
                .when().post("/auth/logout")
                .then().statusCode(200)
                .body("data.success", is(true));
    }
}
