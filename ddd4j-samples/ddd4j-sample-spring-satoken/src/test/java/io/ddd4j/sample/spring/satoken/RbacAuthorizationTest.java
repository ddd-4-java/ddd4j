package io.ddd4j.sample.spring.satoken;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.sample.spring.satoken.rbac.domain.model.Permission;
import io.ddd4j.sample.spring.satoken.rbac.domain.model.Role;
import io.ddd4j.sample.spring.satoken.rbac.domain.model.User;
import io.ddd4j.sample.spring.satoken.rbac.domain.repository.PermissionRepository;
import io.ddd4j.sample.spring.satoken.rbac.domain.repository.RoleRepository;
import io.ddd4j.sample.spring.satoken.rbac.domain.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AuthorizationController 授权管理 CRUD 测试（Sa-Token）。
 *
 * <p>覆盖 17 个 admin 端点：用户/角色/权限的完整 CRUD + 分配操作。
 *
 * @author Test
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("授权管理 CRUD - Sa-Token")
class RbacAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PermissionRepository permissionRepository;

    // ============================ 用户管理 ============================

    @Test
    @DisplayName("GET /admin/users - 用户列表")
    void listUsers() throws Exception {
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("POST /admin/users - 创建用户")
    void createUser_succeeds() throws Exception {
        String userId = "TU-" + System.nanoTime();
        mockMvc.perform(post("/admin/users")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "userId", userId, "username", "test", "password", "test", "realName", "tester"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.userId").value(userId));
        userRepository.deleteById(userId);
    }

    @Test
    @DisplayName("POST /admin/users - 创建已存在用户不会失败（in-memory 覆盖）")
    void createUser_idempotentInMemoryRepo() throws Exception {
        String userId = "TU-DUP-" + System.nanoTime();
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "userId", userId, "username", "dup-test", "password", "pwd", "realName", "Dup"));
        mockMvc.perform(post("/admin/users").contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId));
        mockMvc.perform(post("/admin/users").contentType("application/json").content(body))
                .andExpect(status().isOk());
        userRepository.deleteById(userId);
    }

    @Test
    @DisplayName("GET /admin/users/{id} - 用户详情")
    void getUser_returnsExisting() throws Exception {
        mockMvc.perform(get("/admin/users/10001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("10001"))
                .andExpect(jsonPath("$.username").value("admin"));
    }

    @Test
    @DisplayName("PUT /admin/users/{id} - 更新用户")
    void updateUser_modifiesFields() throws Exception {
        String userId = "TU-UPD-" + System.nanoTime();
        userRepository.save(new User(userId, "toupd", "pwd", "Original", User.Status.ENABLED));
        mockMvc.perform(put("/admin/users/" + userId)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new io.ddd4j.sample.spring.satoken.rbac.web.AuthorizationController.UpdateUserRequest(
                                        "Renamed", null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        assertThat(userRepository.findById(userId).get().getRealName()).isEqualTo("Renamed");
        userRepository.deleteById(userId);
    }

    @Test
    @DisplayName("PUT /admin/users/{id} - 不存在 → 业务异常")
    void updateUser_notFound_throws() throws Exception {
        mockMvc.perform(put("/admin/users/NON-EXISTENT")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new io.ddd4j.sample.spring.satoken.rbac.web.AuthorizationController.UpdateUserRequest(
                                        "X", null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    @DisplayName("DELETE /admin/users/{id} - 删除用户")
    void deleteUser_removesFromRepo() throws Exception {
        String userId = "TU-DEL-" + System.nanoTime();
        userRepository.save(new User(userId, "todel", "pwd", "DeleteMe", User.Status.ENABLED));
        mockMvc.perform(delete("/admin/users/" + userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.deleted").value(userId));
        assertThat(userRepository.findById(userId)).isEmpty();
    }

    @Test
    @DisplayName("POST /admin/users/{id}/roles - 分配角色（全量替换）")
    void assignRolesToUser_replacesAllRoles() throws Exception {
        String userId = "TU-R-" + System.nanoTime();
        userRepository.save(new User(userId, "toassign", "pwd", "Assigner", User.Status.ENABLED));
        userRepository.findById(userId).get().assignRoles(new HashSet<>(List.of("R001")));
        userRepository.save(userRepository.findById(userId).get());

        mockMvc.perform(post("/admin/users/" + userId + "/roles")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new io.ddd4j.sample.spring.satoken.rbac.web.AuthorizationController.AssignRolesRequest(
                                        List.of("R002", "R003")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        assertThat(userRepository.findById(userId).get().getRoleIds()).containsExactlyInAnyOrder("R002", "R003");
        userRepository.deleteById(userId);
    }

    @Test
    @DisplayName("POST /admin/users/{id}/roles - 不存在用户 → 业务异常")
    void assignRolesToUser_notFound_throws() throws Exception {
        mockMvc.perform(post("/admin/users/NON-EXISTENT/roles")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new io.ddd4j.sample.spring.satoken.rbac.web.AuthorizationController.AssignRolesRequest(
                                        List.of("R001")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    @DisplayName("GET /admin/users/{id}/permissions - 用户权限 + 角色编码")
    void getUserPermissions_returnsRoleAndPermissionCodes() throws Exception {
        mockMvc.perform(get("/admin/users/10001/permissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("10001"))
                .andExpect(jsonPath("$.roles").isArray())
                .andExpect(jsonPath("$.permissions").isArray());
    }

    // ============================ 角色管理 ============================

    @Test
    @DisplayName("GET /admin/roles - 角色列表")
    void listRoles() throws Exception {
        mockMvc.perform(get("/admin/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("POST /admin/roles - 创建角色")
    void createRole_succeeds() throws Exception {
        String roleId = "TR-" + System.nanoTime();
        mockMvc.perform(post("/admin/roles")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "roleId", roleId, "roleCode", "test-role",
                                "roleName", "测试角色", "description", "desc"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.roleId").value(roleId));
        roleRepository.deleteById(roleId);
    }

    @Test
    @DisplayName("GET /admin/roles/{id} - 角色详情")
    void getRole_returnsExisting() throws Exception {
        mockMvc.perform(get("/admin/roles/R001"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /admin/roles/{id} - 更新角色")
    void updateRole_modifiesFields() throws Exception {
        String roleId = "TR-U-" + System.nanoTime();
        roleRepository.save(new Role(roleId, "test-role-upd", "Old Name", "Desc", Role.Status.ENABLED));
        mockMvc.perform(put("/admin/roles/" + roleId)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new io.ddd4j.sample.spring.satoken.rbac.web.AuthorizationController.UpdateRoleRequest(
                                        "New Name", "New Desc", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        assertThat(roleRepository.findById(roleId).get().getRoleName()).isEqualTo("New Name");
        roleRepository.deleteById(roleId);
    }

    @Test
    @DisplayName("DELETE /admin/roles/{id} - 删除角色")
    void deleteRole_succeeds() throws Exception {
        String roleId = "TR-D-" + System.nanoTime();
        roleRepository.save(new Role(roleId, "del-role", "DelRole", "x", Role.Status.ENABLED));
        mockMvc.perform(delete("/admin/roles/" + roleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        assertThat(roleRepository.findById(roleId)).isEmpty();
    }

    @Test
    @DisplayName("POST /admin/roles/{id}/permissions - 分配权限（全量替换）")
    void assignPermissionsToRole_replacesAll() throws Exception {
        String roleId = "TR-P-" + System.nanoTime();
        roleRepository.save(new Role(roleId, "test-role-assign", "X", "x", Role.Status.ENABLED));
        mockMvc.perform(post("/admin/roles/" + roleId + "/permissions")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new io.ddd4j.sample.spring.satoken.rbac.web.AuthorizationController.AssignPermissionsRequest(
                                        List.of("P001", "P003")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        assertThat(roleRepository.findById(roleId).get().getPermissionIds())
                .containsExactlyInAnyOrder("P001", "P003");
        roleRepository.deleteById(roleId);
    }

    @Test
    @DisplayName("GET /admin/roles/{id}/permissions - 角色权限码")
    void getRolePermissions_returnsCodes() throws Exception {
        mockMvc.perform(get("/admin/roles/R001/permissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleId").value("R001"))
                .andExpect(jsonPath("$.permissions").isArray());
    }

    // ============================ 权限管理 ============================

    @Test
    @DisplayName("GET /admin/permissions - 权限列表")
    void listPermissions() throws Exception {
        mockMvc.perform(get("/admin/permissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("POST /admin/permissions - 创建权限")
    void createPermission_succeeds() throws Exception {
        String permId = "TP-" + System.nanoTime();
        mockMvc.perform(post("/admin/permissions")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "permissionId", permId, "permissionCode", "test:perm",
                                "permissionName", "Test Perm", "module", "test"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.permissionId").value(permId));
        permissionRepository.deleteById(permId);
    }

    @Test
    @DisplayName("GET /admin/permissions/{id} - 权限详情")
    void getPermission_returnsExisting() throws Exception {
        mockMvc.perform(get("/admin/permissions/P001"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /admin/permissions/{id} - 更新权限")
    void updatePermission_modifiesFields() throws Exception {
        String permId = "TP-U-" + System.nanoTime();
        permissionRepository.save(new Permission(permId, "test:code", "Old", "test", Permission.Status.ENABLED));
        mockMvc.perform(put("/admin/permissions/" + permId)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new io.ddd4j.sample.spring.satoken.rbac.web.AuthorizationController.UpdatePermissionRequest(
                                        "Renamed", "test", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        assertThat(permissionRepository.findById(permId).get().getPermissionName()).isEqualTo("Renamed");
        permissionRepository.deleteById(permId);
    }

    @Test
    @DisplayName("DELETE /admin/permissions/{id} - 删除权限")
    void deletePermission_succeeds() throws Exception {
        String permId = "TP-D-" + System.nanoTime();
        permissionRepository.save(new Permission(permId, "test:del", "Del", "test", Permission.Status.ENABLED));
        mockMvc.perform(delete("/admin/permissions/" + permId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        assertThat(permissionRepository.findById(permId)).isEmpty();
    }
}
