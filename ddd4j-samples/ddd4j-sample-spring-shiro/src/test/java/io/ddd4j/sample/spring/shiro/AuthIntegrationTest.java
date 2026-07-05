package io.ddd4j.sample.spring.shiro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.sample.spring.shiro.rbac.application.RbacService;
import io.ddd4j.sample.spring.shiro.rbac.domain.model.Role;
import io.ddd4j.sample.spring.shiro.rbac.domain.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RBAC 集成测试（Shiro）。
 *
 * @author Test
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("RBAC 集成 - Shiro")
class AuthIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RbacService rbacService;

    private Optional<String> login(String username, String password) {
        try {
            MvcResult r = mockMvc.perform(post("/auth/login")
                            .contentType("application/json")
                            .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode body = objectMapper.readTree(r.getResponse().getContentAsString());
            if (body.path("success").asBoolean() && body.has("token") && !body.path("token").asText().isEmpty()) {
                return Optional.of(body.path("token").asText());
            }
            return Optional.empty();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("预置数据完整性")
    void presetDataIntegrity() {
        assertThat(rbacService.listUsers()).extracting(User::getUsername)
                .contains("admin", "user", "disabled");
        assertThat(rbacService.listRoles()).extracting(Role::getRoleCode)
                .contains("admin", "user", "manager");
    }

    @Test
    @DisplayName("登录 admin")
    void login_admin_success() { assertThat(login("admin", "123456")).isPresent(); }

    @Test
    @DisplayName("登录 user")
    void login_user_success() { assertThat(login("user", "123456")).isPresent(); }

    @Test
    @DisplayName("登录 disabled → 失败")
    void login_disabled_fails() { assertThat(login("disabled", "123456")).isEmpty(); }

    @Test
    @DisplayName("登录错误密码 → 失败")
    void login_wrongPassword_fails() { assertThat(login("admin", "wrong")).isEmpty(); }

    @Test
    @DisplayName("admin 用户拥有所有权限")
    void admin_user_hasAllPermissions() {
        var permissions = rbacService.listPermissionCodesOfUser("10001");
        assertThat(permissions).contains("user:add", "user:delete", "user:list", "role:add", "goods:view", "order:pay");
    }

    @Test
    @DisplayName("user 仅拥有 2 个权限")
    void user_hasLimitedPermissions() {
        var permissions = rbacService.listPermissionCodesOfUser("10002");
        assertThat(permissions).containsExactlyInAnyOrder("user:list", "goods:view");
    }

    @Test
    @DisplayName("manager 拥有 order:pay")
    void manager_hasOrderPayPermission() {
        var roleUsers = rbacService.listUsers().stream()
                .filter(u -> u.getRoleIds().contains("R003")).toList();
        if (!roleUsers.isEmpty()) {
            String managerUserId = roleUsers.get(0).getUserId();
            var perms = rbacService.listPermissionCodesOfUser(managerUserId);
            assertThat(perms).contains("order:pay", "user:list", "goods:view");
        }
    }

    @Test
    @DisplayName("admin 拥有 admin 角色")
    void admin_hasAdminRoleCode() {
        assertThat(rbacService.listRoleCodesOfUser("10001")).contains("admin");
    }

    @Test
    @DisplayName("admin 不拥有 manager 角色")
    void admin_doesNotHaveManagerRole() {
        assertThat(rbacService.listRoleCodesOfUser("10001")).doesNotContain("manager");
    }

    @Test
    @DisplayName("创建用户 → 查询 → 删除")
    void createUser_lifecycle() {
        String userId = "TEST-" + System.nanoTime();
        rbacService.createUser(userId, "test-user", "test-pwd", "test-real");
        var fetched = rbacService.getUser(userId);
        assertThat(fetched).isNotNull();
        rbacService.deleteUser(userId);
        assertThat(rbacService.getUser(userId)).isNull();
    }

    @Test
    @DisplayName("分配角色后权限变化")
    void assignRole_changesPermissions() {
        String userId = "TEST-ROLE-" + System.nanoTime();
        rbacService.createUser(userId, "role-test", "pwd", "Role Tester");
        var before = rbacService.listPermissionCodesOfUser(userId);
        assertThat(before).isEmpty();
        rbacService.assignRolesToUser(userId, new HashSet<>(List.of("R001")));
        var after = rbacService.listPermissionCodesOfUser(userId);
        assertThat(after).hasSize(6);
        rbacService.deleteUser(userId);
    }

    @Test
    @DisplayName("用户禁用→启用")
    void enableDisableUser() {
        String userId = "TEST-EN-" + System.nanoTime();
        rbacService.createUser(userId, "en-test", "pwd", "EN");
        rbacService.updateUser(userId, null, null, User.Status.DISABLED);
        assertThat(rbacService.getUser(userId).getStatus()).isEqualTo(User.Status.DISABLED);
        rbacService.updateUser(userId, null, null, User.Status.ENABLED);
        assertThat(rbacService.getUser(userId).getStatus()).isEqualTo(User.Status.ENABLED);
        rbacService.deleteUser(userId);
    }

    @Test
    @DisplayName("登录响应字段")
    void loginResponseShape() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"admin\",\"password\":\"123456\"}"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.tokenName").value("ddd4j-token"));

        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("/auth/status")
    void statusNotLoggedIn() throws Exception {
        mockMvc.perform(get("/auth/status"))
                .andExpect(jsonPath("$.login").value(false));
    }
}
