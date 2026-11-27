package io.ddd4j.sample.javalin.shiro.rbac.controller;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 通过 <code>create.setCode()</code>、<code>create.setName()</code>、<code>create.setPermissions()</code> 验证 <code>AuthorizationRolePermissionContract</code> 的运行行为。
 */
class AuthorizationRolePermissionContractTest {

    /**
     * 验证<code>create.code()</code> 等于 <code>create.getCode()</code>，<code>create.name()</code> 等于 <code>create.getName()</code>，<code>update.hashCode()</code> 等于 <code>sameUpdate.hashCode()</code>。
     */
    @Test
    void roleRequestsRetainMutableBeanAndArrayValueContract() {
        AuthorizationController.CreateRoleRequest create = new AuthorizationController.CreateRoleRequest();
        create.setCode("admin");
        create.setName("Administrator");
        create.setPermissions(new String[]{"user:list", "order:pay"});
        assertEquals(create.getCode(), create.code());
        assertEquals(create.getName(), create.name());
        assertArrayEquals(create.getPermissions(), create.permissions());
        AuthorizationController.CreateRoleRequest sameCreate =
                new AuthorizationController.CreateRoleRequest("admin", "Administrator",
                        new String[]{"user:list", "order:pay"});
        assertEquals(sameCreate, create);
        assertEquals(sameCreate.hashCode(), create.hashCode());
        assertTrue(create.toString().contains("permissions=[user:list, order:pay]"));

        AuthorizationController.UpdateRoleRequest update = new AuthorizationController.UpdateRoleRequest();
        update.setName("Manager");
        update.setPermissions(new String[]{"order:pay"});
        assertEquals(update.getName(), update.name());
        assertArrayEquals(update.getPermissions(), update.permissions());
        AuthorizationController.UpdateRoleRequest sameUpdate =
                new AuthorizationController.UpdateRoleRequest("Manager", new String[]{"order:pay"});
        assertEquals(sameUpdate, update);
        assertEquals(sameUpdate.hashCode(), update.hashCode());
    }

    /**
     * 验证<code>request.code()</code> 等于 <code>request.getCode()</code>，<code>request.description()</code> 等于 <code>request.getDescription()</code>，<code>request.hashCode()</code> 等于 <code>same.hashCode()</code>。
     */
    @Test
    void permissionRequestRetainsMutableBeanContract() {
        AuthorizationController.CreatePermissionRequest request =
                new AuthorizationController.CreatePermissionRequest();
        request.setCode("order:pay");
        request.setDescription("Pay orders");
        assertEquals(request.getCode(), request.code());
        assertEquals(request.getDescription(), request.description());
        AuthorizationController.CreatePermissionRequest same =
                new AuthorizationController.CreatePermissionRequest("order:pay", "Pay orders");
        assertEquals(same, request);
        assertEquals(same.hashCode(), request.hashCode());
    }

    /**
     * 验证<code>user.loginId()</code> 等于 <code>user.getLoginId()</code>，<code>user.displayName()</code> 等于 <code>user.getDisplayName()</code>，<code>permission.description()</code> 等于 <code>permission.getDescription()</code>。
     */
    @Test
    void viewsRetainReadOnlyBeanAndValueContract() {
        Set<String> roles = new HashSet<>(Arrays.asList("admin"));
        Set<String> permissions = new HashSet<>(Arrays.asList("order:pay"));
        AuthorizationController.UserView user =
                new AuthorizationController.UserView("alice", "Alice", roles, permissions);
        assertEquals(user.getLoginId(), user.loginId());
        assertEquals(user.getDisplayName(), user.displayName());
        assertEquals(user.getRoles(), user.roles());
        assertEquals(user.getPermissions(), user.permissions());
        assertEquals(new AuthorizationController.UserView("alice", "Alice", roles, permissions), user);
        assertTrue(user.toString().contains("UserView("));

        AuthorizationController.RoleView role =
                new AuthorizationController.RoleView("admin", "Administrator", permissions);
        assertEquals(role.getCode(), role.code());
        assertEquals(role.getName(), role.name());
        assertEquals(role.getPermissions(), role.permissions());
        assertEquals(new AuthorizationController.RoleView("admin", "Administrator", permissions), role);

        AuthorizationController.PermissionView permission =
                new AuthorizationController.PermissionView("order:pay", "Pay orders");
        assertEquals(permission.getCode(), permission.code());
        assertEquals(permission.getDescription(), permission.description());
        assertEquals(new AuthorizationController.PermissionView("order:pay", "Pay orders"), permission);
    }
}
