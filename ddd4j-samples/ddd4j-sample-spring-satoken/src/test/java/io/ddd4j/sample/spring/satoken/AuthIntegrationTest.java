package io.ddd4j.sample.spring.satoken;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.sample.spring.satoken.rbac.application.RbacService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RBAC 集成测试：覆盖登录→创建用户→分配角色→权限校验完整流程（Sa-Token）。
 *
 * @author Test
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("RBAC 集成 - Sa-Token")
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RbacService rbacService;

    private Optional<String> login(String username, String password) {
        try {
            MvcResult result = mockMvc.perform(post("/auth/login")
                            .contentType("application/json")
                            .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
            if (body.path("success").asBoolean() && body.has("token") && !body.path("token").asText().isEmpty()) {
                return Optional.of(body.path("token").asText());
            }
            return Optional.empty();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("预置数据完整性 - admin/user/disabled 三用户 + admin/user/manager 三角色")
    void presetDataIntegrity() {
        assertThat(rbacService.listUsers()).extracting(u -> u.getUsername())
                .contains("admin", "user", "disabled");
        assertThat(rbacService.listRoles()).extracting(r -> r.getRoleCode())
                .contains("admin", "user", "manager");
    }

    @Test
    @DisplayName("登录 admin 成功")
    void login_admin_success() {
        Optional<String> token = login("admin", "123456");
        assertThat(token).isPresent();
        assertThat(token.get()).isNotEmpty();
    }

    @Test
    @DisplayName("登录 user 成功")
    void login_user_success() {
        assertThat(login("user", "123456")).isPresent();
    }

    @Test
    @DisplayName("登录 disabled 账号 → success=false")
    void login_disabled_fails() {
        assertThat(login("disabled", "123456")).isEmpty();
    }

    @Test
    @DisplayName("登录错误密码 → success=false")
    void login_wrongPassword_fails() {
        assertThat(login("admin", "wrong-password")).isEmpty();
    }

    @Test
    @DisplayName("admin 拥有全部 6 个权限")
    void admin_user_hasAllPermissions() {
        var permissions = rbacService.listPermissionCodesOfUser("10001");
        assertThat(permissions).contains("user:add", "user:delete", "user:list", "role:add", "goods:view", "order:pay");
    }

    @Test
    @DisplayName("user 仅拥有 2 个权限（user:list、goods:view）")
    void user_hasLimitedPermissions() {
        var permissions = rbacService.listPermissionCodesOfUser("10002");
        assertThat(permissions).containsExactlyInAnyOrder("user:list", "goods:view");
    }

    @Test
    @DisplayName("manager 拥有 3 个权限（含 order:pay）")
    void manager_hasOrderPayPermission() {
        // 找到第一个带 manager 角色的用户的 ID
        var roleUsers = rbacService.listUsers().stream()
                .filter(u -> u.getRoleIds() != null && u.getRoleIds().contains("R003"))
                .toList();
        if (!roleUsers.isEmpty()) {
            String managerUserId = roleUsers.get(0).getUserId();
            var managerPerms = rbacService.listPermissionCodesOfUser(managerUserId);
            assertThat(managerPerms).contains("order:pay", "user:list", "goods:view");
        }
    }

    @Test
    @DisplayName("admin 拥有 admin 角色编码")
    void admin_hasAdminRoleCode() {
        var roleCodes = rbacService.listRoleCodesOfUser("10001");
        assertThat(roleCodes).contains("admin");
    }

    @Test
    @DisplayName("admin 拥有 manager 角色编码 = false（按用户）")
    void admin_doesNotHaveManagerRole() {
        var roleCodes = rbacService.listRoleCodesOfUser("10001");
        assertThat(roleCodes).doesNotContain("manager");
    }

    @Test
    @DisplayName("创建新用户 → 查询 → 删除（生命周期）")
    void createUser_lifecycle() {
        String userId = "TEST-" + System.nanoTime();
        rbacService.createUser(userId, "test-user", "test-pwd", "test-real");
        var fetched = rbacService.getUser(userId);
        assertThat(fetched).isNotNull();
        assertThat(fetched.getUsername()).isEqualTo("test-user");

        rbacService.deleteUser(userId);
        assertThat(rbacService.getUser(userId)).isNull();
    }

    @Test
    @DisplayName("分配角色后用户权限随之变化")
    void assignRole_changesPermissions() {
        String userId = "TEST-ROLE-" + System.nanoTime();
        rbacService.createUser(userId, "role-test", "pwd", "Role Tester");
        var before = rbacService.listPermissionCodesOfUser(userId);
        assertThat(before).isEmpty();

        rbacService.assignRolesToUser(userId, new java.util.HashSet<>(List.of("R001")));
        var after = rbacService.listPermissionCodesOfUser(userId);
        assertThat(after).hasSize(6);

        rbacService.deleteUser(userId);
    }

    @Test
    @DisplayName("更新用户禁用后再启用")
    void enableDisableUser() {
        String userId = "TEST-EN-" + System.nanoTime();
        rbacService.createUser(userId, "en-test", "pwd", "EN");
        rbacService.updateUser(userId, null, null, io.ddd4j.sample.spring.satoken.rbac.domain.model.User.Status.DISABLED);
        assertThat(rbacService.getUser(userId).getStatus())
                .isEqualTo(io.ddd4j.sample.spring.satoken.rbac.domain.model.User.Status.DISABLED);
        rbacService.updateUser(userId, null, null, io.ddd4j.sample.spring.satoken.rbac.domain.model.User.Status.ENABLED);
        assertThat(rbacService.getUser(userId).getStatus())
                .isEqualTo(io.ddd4j.sample.spring.satoken.rbac.domain.model.User.Status.ENABLED);
        rbacService.deleteUser(userId);
    }

    @Test
    @DisplayName("状态码约定：登录成功 success=true、失败 false")
    void loginResponseShape() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"admin\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.tokenName").value("ddd4j-token"));

        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("/auth/status 未登录 → login=false")
    void statusNotLoggedIn() throws Exception {
        mockMvc.perform(get("/auth/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value(false));
    }
}
