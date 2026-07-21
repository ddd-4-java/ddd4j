package io.ddd4j.sample.spring.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.sample.spring.security.rbac.Permission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RBAC 授权管理 CRUD 测试（Spring Security 示例）。
 * 用户/角色/权限的 CRUD + 分配/撤销。
 *
 * <p>权限模型：
 * <ul>
 *   <li>列表查询：仅需 user:list</li>
 *   <li>创建/分配：admin 角色 + user:add 权限</li>
 *   <li>删除/撤销：admin 角色 + user:delete 权限</li>
 * </ul>
 *
 * @author Test
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("RBAC 授权 - Spring Security")
class RbacAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private static String basic(String user, String pass) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((user + ":" + pass).getBytes(StandardCharsets.UTF_8));
    }

    // ============== 用户管理 ==============

    @Test
    @DisplayName("admin 列出用户")
    void listUsers_admin() throws Exception {
        mockMvc.perform(get("/auth/users")
                        .header("Authorization", basic("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("alice 列出用户（有 user:list）")
    void listUsers_alice() throws Exception {
        mockMvc.perform(get("/auth/users")
                        .header("Authorization", basic("alice", "alice123")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("未登录列出用户 → 401")
    void listUsers_noAuth() throws Exception {
        mockMvc.perform(get("/auth/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("admin 查询用户 - 存在")
    void getUser_exists() throws Exception {
        mockMvc.perform(get("/auth/users/10001")
                        .header("Authorization", basic("admin", "admin123")))
                .andExpect(jsonPath("$.username").value("admin"));
    }

    @Test
    @DisplayName("admin 创建用户")
    void createUser_admin() throws Exception {
        String username = "tu-" + UUID.randomUUID();
        mockMvc.perform(post("/auth/users")
                        .header("Authorization", basic("admin", "admin123"))
                        .param("username", username)
                        .param("password", "pwd123"))
                .andExpect(jsonPath("$.username").value(username));
    }

    @Test
    @DisplayName("alice 创建用户 → 403（非 admin 角色）")
    void createUser_alice_forbidden() throws Exception {
        mockMvc.perform(post("/auth/users")
                        .header("Authorization", basic("alice", "alice123"))
                        .param("username", "tu-x")
                        .param("password", "x"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("admin 删除用户")
    void deleteUser_admin() throws Exception {
        // 先创建一个测试用户
        String username = "td-" + UUID.randomUUID();
        String body = mockMvc.perform(post("/auth/users")
                        .header("Authorization", basic("admin", "admin123"))
                        .param("username", username)
                        .param("password", "pwd123"))
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(body).path("id").asText();

        mockMvc.perform(delete("/auth/users/" + id)
                        .header("Authorization", basic("admin", "admin123")))
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("alice 删除用户 → 403")
    void deleteUser_alice_forbidden() throws Exception {
        mockMvc.perform(delete("/auth/users/10001")
                        .header("Authorization", basic("alice", "alice123")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("admin 给用户分配角色")
    void assignRole_admin() throws Exception {
        String username = "ar-" + UUID.randomUUID();
        String body = mockMvc.perform(post("/auth/users")
                        .header("Authorization", basic("admin", "admin123"))
                        .param("username", username)
                        .param("password", "pwd123"))
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(body).path("id").asText();

        mockMvc.perform(put("/auth/users/" + id + "/roles/user")
                        .header("Authorization", basic("admin", "admin123")))
                .andExpect(jsonPath("$.roleCodes").exists());
    }

    @Test
    @DisplayName("admin 撤销用户角色")
    void revokeRole_admin() throws Exception {
        String username = "rr-" + UUID.randomUUID();
        String body = mockMvc.perform(post("/auth/users")
                        .header("Authorization", basic("admin", "admin123"))
                        .param("username", username)
                        .param("password", "pwd123"))
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(body).path("id").asText();
        mockMvc.perform(put("/auth/users/" + id + "/roles/user")
                        .header("Authorization", basic("admin", "admin123")))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/auth/users/" + id + "/roles/user")
                        .header("Authorization", basic("admin", "admin123")))
                .andExpect(jsonPath("$.username").value(username));
    }

    @Test
    @DisplayName("alice 分配角色 → 403")
    void assignRole_alice_forbidden() throws Exception {
        mockMvc.perform(put("/auth/users/10002/roles/admin")
                        .header("Authorization", basic("alice", "alice123")))
                .andExpect(status().isForbidden());
    }

    // ============== 角色管理 ==============

    @Test
    @DisplayName("admin 列出角色")
    void listRoles_admin() throws Exception {
        mockMvc.perform(get("/auth/roles")
                        .header("Authorization", basic("admin", "admin123")))
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("未登录列角色 → 401")
    void listRoles_noAuth() throws Exception {
        mockMvc.perform(get("/auth/roles"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("查询角色 - admin")
    void getRole_admin() throws Exception {
        mockMvc.perform(get("/auth/roles/admin")
                        .header("Authorization", basic("admin", "admin123")))
                .andExpect(jsonPath("$.code").value("admin"));
    }

    @Test
    @DisplayName("查询角色 - 不存在 → 400")
    void getRole_notFound() throws Exception {
        mockMvc.perform(get("/auth/roles/no-such-role")
                        .header("Authorization", basic("admin", "admin123")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("admin 创建角色")
    void createRole_admin() throws Exception {
        String code = "tr-" + UUID.randomUUID();
        mockMvc.perform(post("/auth/roles")
                        .header("Authorization", basic("admin", "admin123"))
                        .param("code", code)
                        .param("name", "Test Role")
                        .param("description", "created by test")
                        .param("permissionCodes", "user:list"))
                .andExpect(jsonPath("$.code").value(code));
    }

    @Test
    @DisplayName("alice 创建角色 → 403")
    void createRole_alice_forbidden() throws Exception {
        mockMvc.perform(post("/auth/roles")
                        .header("Authorization", basic("alice", "alice123"))
                        .param("code", "tr-x")
                        .param("name", "X"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("admin 删除角色")
    void deleteRole_admin() throws Exception {
        String code = "td-" + UUID.randomUUID();
        mockMvc.perform(post("/auth/roles")
                        .header("Authorization", basic("admin", "admin123"))
                        .param("code", code)
                        .param("name", "To delete"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/auth/roles/" + code)
                        .header("Authorization", basic("admin", "admin123")))
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("admin 给角色授予权限")
    void grantPermission_admin() throws Exception {
        mockMvc.perform(put("/auth/roles/user/permissions/order:list")
                        .header("Authorization", basic("admin", "admin123")))
                .andExpect(jsonPath("$.code").value("user"));
    }

    @Test
    @DisplayName("admin 撤销角色权限")
    void revokePermission_admin() throws Exception {
        mockMvc.perform(delete("/auth/roles/user/permissions/order:list")
                        .header("Authorization", basic("admin", "admin123")))
                .andExpect(jsonPath("$.code").value("user"));
    }

    // ============== 权限管理 ==============

    @Test
    @DisplayName("admin 列出权限")
    void listPermissions_admin() throws Exception {
        mockMvc.perform(get("/auth/permissions")
                        .header("Authorization", basic("admin", "admin123")))
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("未登录列权限 → 401")
    void listPermissions_noAuth() throws Exception {
        mockMvc.perform(get("/auth/permissions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("查询权限")
    void getPermission_exists() throws Exception {
        mockMvc.perform(get("/auth/permissions/user:list")
                        .header("Authorization", basic("admin", "admin123")))
                .andExpect(jsonPath("$.code").value("user:list"));
    }

    @Test
    @DisplayName("查询权限 - 不存在")
    void getPermission_notFound() throws Exception {
        mockMvc.perform(get("/auth/permissions/no:such")
                        .header("Authorization", basic("admin", "admin123")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("admin 创建权限")
    void createPermission_admin() throws Exception {
        String code = "test:" + UUID.randomUUID();
        mockMvc.perform(post("/auth/permissions")
                        .header("Authorization", basic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new Permission(code, "Test Perm", "Created by test"))))
                .andExpect(jsonPath("$.code").value(code));
    }

    @Test
    @DisplayName("alice 创建权限 → 403")
    void createPermission_alice_forbidden() throws Exception {
        mockMvc.perform(post("/auth/permissions")
                        .header("Authorization", basic("alice", "alice123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new Permission("test:x", "X", "X"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("admin 删除权限")
    void deletePermission_admin() throws Exception {
        String code = "test:" + UUID.randomUUID();
        mockMvc.perform(post("/auth/permissions")
                        .header("Authorization", basic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new Permission(code, "To delete", ""))))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/auth/permissions/" + code)
                        .header("Authorization", basic("admin", "admin123")))
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("alice 删除权限 → 403")
    void deletePermission_alice_forbidden() throws Exception {
        mockMvc.perform(delete("/auth/permissions/user:list")
                        .header("Authorization", basic("alice", "alice123")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("完整 CRUD 流：权限 → 角色 → 用户 → 分配 → 撤销 → 删除")
    void fullCrudFlow() throws Exception {
        String permCode = "p:" + UUID.randomUUID();
        String roleCode = "r:" + UUID.randomUUID();
        String userId;

        // 1. 创建权限
        mockMvc.perform(post("/auth/permissions")
                        .header("Authorization", basic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new Permission(permCode, "P", "p"))))
                .andExpect(status().isOk());

        // 2. 创建角色并授予权限
        mockMvc.perform(post("/auth/roles")
                        .header("Authorization", basic("admin", "admin123"))
                        .param("code", roleCode)
                        .param("name", "R")
                        .param("permissionCodes", permCode))
                .andExpect(status().isOk());

        // 3. 创建用户并分配角色
        String userBody = mockMvc.perform(post("/auth/users")
                        .header("Authorization", basic("admin", "admin123"))
                        .param("username", "u-" + UUID.randomUUID())
                        .param("password", "p"))
                .andReturn().getResponse().getContentAsString();
        userId = objectMapper.readTree(userBody).path("id").asText();
        assertThat(userId).isNotEmpty();

        mockMvc.perform(put("/auth/users/" + userId + "/roles/" + roleCode)
                        .header("Authorization", basic("admin", "admin123")))
                .andExpect(status().isOk());

        // 4. 撤销并删除
        mockMvc.perform(delete("/auth/users/" + userId + "/roles/" + roleCode)
                        .header("Authorization", basic("admin", "admin123")))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/auth/users/" + userId)
                        .header("Authorization", basic("admin", "admin123")))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/auth/roles/" + roleCode)
                        .header("Authorization", basic("admin", "admin123")))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/auth/permissions/" + permCode)
                        .header("Authorization", basic("admin", "admin123")))
                .andExpect(status().isOk());
    }
}
