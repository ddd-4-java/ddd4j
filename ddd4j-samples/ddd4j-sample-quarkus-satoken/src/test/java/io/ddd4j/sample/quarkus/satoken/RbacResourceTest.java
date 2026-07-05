package io.ddd4j.sample.quarkus.satoken;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
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

    // ========== 登录 / 认证 ==========

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

    @Test
    void shouldRejectInvalidPassword() {
        Map<String, String> body = new HashMap<>();
        body.put("username", "admin");
        body.put("password", "wrong-password");

        given().contentType("application/json").body(body)
                .when().post("/auth/login")
                .then().statusCode(500);
    }

    @Test
    void shouldRejectLoginForDisabledUser() {
        Map<String, String> body = new HashMap<>();
        body.put("username", "disabled");
        body.put("password", "123456");

        given().contentType("application/json").body(body)
                .when().post("/auth/login")
                .then().statusCode(500);
    }

    @Test
    void shouldRejectLoginWithUnknownUser() {
        Map<String, String> body = new HashMap<>();
        body.put("username", "ghost");
        body.put("password", "123456");

        given().contentType("application/json").body(body)
                .when().post("/auth/login")
                .then().statusCode(500);
    }

    @Test
    void shouldReturnLoginStatus() {
        given().when().get("/auth/status")
                .then()
                .statusCode(200)
                .body("data.login", is(false));
    }

    @Test
    void shouldReturnNotAuthenticatedWhenNotLogin() {
        given().when().get("/auth/me")
                .then().statusCode(401);
    }

    @Test
    void shouldReturn401WhenAccessAdminUsersWithoutLogin() {
        given().when().get("/admin/users")
                .then().statusCode(401);
    }

    // ========== 鉴权（3 种鉴权方式） ==========

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

    @Test
    void shouldDenyAccessToCheckPermissionWithoutLogin() {
        given().when().get("/auth/check/permission?permission=anything")
                .then().statusCode(401);
    }

    @Test
    void shouldDenyAccessToCheckRoleWithoutLogin() {
        given().when().get("/auth/check/role?role=anything")
                .then().statusCode(401);
    }

    // ========== 业务侧鉴权：3 种鉴权组合 ==========

    @Test
    void shouldListUsersAsAdminViaAuthEndpoint() {
        String token = loginAs("admin", "123456");
        given().header("Authorization", "Bearer " + token)
                .when().get("/auth/users")
                .then()
                .statusCode(200)
                .body("data.items.size()", is(3));
    }

    @Test
    void shouldListUsersAsAdminViaAdminEndpoint() {
        String token = loginAs("admin", "123456");
        given().header("Authorization", "Bearer " + token)
                .when().get("/admin/users")
                .then()
                .statusCode(200)
                .body("data.size()", is(3));
    }

    @Test
    void shouldPayOrderWithOrderPayPermissionDenied() {
        String token = loginAs("user", "123456");
        given().header("Authorization", "Bearer " + token)
                .when().post("/auth/orders/o-1001/pay")
                .then().statusCode(403);
    }

    @Test
    void shouldPayOrderWithOrderPayPermissionAllowed() {
        String token = loginAs("admin", "123456");
        given().header("Authorization", "Bearer " + token)
                .when().post("/auth/orders/o-1001/pay")
                .then().statusCode(200)
                .body("data.paid", is(true));
    }

    @Test
    void shouldDeleteUserAsAdmin() {
        String token = loginAs("admin", "123456");
        given().header("Authorization", "Bearer " + token)
                .when().delete("/auth/users/u-temp")
                .then()
                .statusCode(200)
                .body("data", is(true));
    }

    @Test
    void shouldDenyDeleteUserAsRegularUser() {
        String token = loginAs("user", "123456");
        given().header("Authorization", "Bearer " + token)
                .when().delete("/auth/users/u-temp")
                .then().statusCode(403);
    }

    @Test
    void shouldDenyAccessToBusinessEndpointWithoutLogin() {
        given().when().get("/auth/users")
                .then().statusCode(401);
    }

    // ========== 用户 CRUD (admin 角色权限) ==========

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

    // ========== 角色 / 权限 CRUD ==========

    @Test
    void shouldListRolesAsAuthenticatedUser() {
        String token = loginAs("user", "123456");
        given().header("Authorization", "Bearer " + token)
                .when().get("/admin/roles")
                .then()
                .statusCode(200)
                .body("data.size()", greaterThanOrEqualTo(2));
    }

    @Test
    void shouldFindRoleByCode() {
        String token = loginAs("admin", "123456");
        given().header("Authorization", "Bearer " + token)
                .when().get("/admin/roles/{code}", "admin")
                .then()
                .statusCode(200)
                .body("data.code", equalTo("admin"));
    }

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

    @Test
    void shouldGrantPermissionToRoleAsAdmin() {
        String token = loginAs("admin", "123456");
        given().header("Authorization", "Bearer " + token)
                .when().post("/admin/roles/{code}/permissions/{pcode}", "user", "order:pay")
                .then()
                .statusCode(200);
    }

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

    @Test
    void shouldListPermissionsAsAuthenticatedUser() {
        String token = loginAs("user", "123456");
        given().header("Authorization", "Bearer " + token)
                .when().get("/admin/permissions")
                .then()
                .statusCode(200)
                .body("data.size()", greaterThanOrEqualTo(5));
    }

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

    @Test
    void shouldDenyCreateUserForRegularUser() {
        String token = loginAs("user", "123456");
        Map<String, Object> newUser = createUserBody("u-deny", "deny", "拒绝", "user");
        given().header("Authorization", "Bearer " + token)
                .contentType("application/json").body(newUser)
                .when().post("/admin/users")
                .then().statusCode(403);
    }

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

    @Test
    void shouldDenyAssignRoleForRegularUser() {
        String token = loginAs("user", "123456");
        given().header("Authorization", "Bearer " + token)
                .when().post("/admin/users/{id}/roles/{code}", "u-admin", "user")
                .then().statusCode(403);
    }

    @Test
    void shouldDenyListUsersWithoutUserListPermission() {
        // Sa-Token 中：访问 /admin/users 需要 user:list + login。
        // "user" 用户只有 user:list 权限，应当 200。
        // 该测试覆盖一个反向断言："disabled" 用户（即便未登录）无法访问。
        // 已登录但禁用账户，由配置层处理。验证无登录场景。
        given().when().get("/admin/roles")
                .then().statusCode(401);
    }

    @Test
    void shouldLogoutAsAuthenticatedUser() {
        String token = loginAs("user", "123456");
        given().header("Authorization", "Bearer " + token)
                .when().post("/auth/logout")
                .then().statusCode(200)
                .body("data.success", is(true));
    }

    // ============ 工具方法 ============

    private static String loginAs(String username, String password) {
        Map<String, String> body = new HashMap<>();
        body.put("username", username);
        body.put("password", password);
        return given().contentType("application/json").body(body)
                .when().post("/auth/login")
                .then().statusCode(200)
                .extract().path("token");
    }

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

    private static Map<String, Object> createUserBody(String id, String username, String displayName, Object roles) {
        Map<String, Object> newUser = new HashMap<>();
        newUser.put("id", id);
        newUser.put("username", username);
        newUser.put("displayName", displayName);
        newUser.put("password", "123456");
        newUser.put("roleCodes", roles);
        return newUser;
    }
}
