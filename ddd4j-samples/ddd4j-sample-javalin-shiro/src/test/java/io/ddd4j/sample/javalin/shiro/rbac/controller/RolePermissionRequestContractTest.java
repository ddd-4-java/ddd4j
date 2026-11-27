package io.ddd4j.sample.javalin.shiro.rbac.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 通过 <code>create.setCode()</code>、<code>create.setName()</code>、<code>create.setPermissions()</code> 验证 <code>RolePermissionRequestContract</code> 的运行行为。
 */
class RolePermissionRequestContractTest {

    /**
     * 验证<code>create.getCode()</code> 等于 <code>"admin"</code>，<code>create.code()</code> 等于 <code>create.getCode()</code>，<code>update.permissions()</code> 与…的元素逐项相同 <code>update.getPermissions()</code>。
     */
    @Test
    void roleRequestsKeepBeanComponentAndArrayValueContracts() {
        AuthorizationController.CreateRoleRequest create =
                new AuthorizationController.CreateRoleRequest();
        create.setCode("admin");
        create.setName("Administrator");
        create.setPermissions(new String[]{"user:read"});
        assertEquals("admin", create.getCode());
        assertEquals(create.getCode(), create.code());
        assertEquals(create.getName(), create.name());
        assertArrayEquals(create.getPermissions(), create.permissions());
        AuthorizationController.CreateRoleRequest same =
                new AuthorizationController.CreateRoleRequest("admin", "Administrator",
                        new String[]{"user:read"});
        assertEquals(same, create);
        assertEquals(same.hashCode(), create.hashCode());

        AuthorizationController.UpdateRoleRequest update =
                new AuthorizationController.UpdateRoleRequest();
        update.setName("Auditor");
        update.setPermissions(new String[]{"report:read"});
        assertEquals(update.getName(), update.name());
        assertArrayEquals(update.getPermissions(), update.permissions());
        assertEquals(new AuthorizationController.UpdateRoleRequest("Auditor",
                new String[]{"report:read"}), update);
    }

    /**
     * 验证<code>request.code()</code> 等于 <code>request.getCode()</code>，<code>request.description()</code> 等于 <code>request.getDescription()</code>，<code>request.hashCode()</code> 等于 <code>same.hashCode()</code>。
     */
    @Test
    void permissionRequestKeepsBeanAndComponentContract() {
        AuthorizationController.CreatePermissionRequest request =
                new AuthorizationController.CreatePermissionRequest();
        request.setCode("user:read");
        request.setDescription("Read users");
        assertEquals(request.getCode(), request.code());
        assertEquals(request.getDescription(), request.description());
        AuthorizationController.CreatePermissionRequest same =
                new AuthorizationController.CreatePermissionRequest("user:read", "Read users");
        assertEquals(same, request);
        assertEquals(same.hashCode(), request.hashCode());
    }
}
