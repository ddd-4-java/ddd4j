package io.ddd4j.sample.javalin.shiro.rbac.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** \u901a\u8fc7 <code>create.setCode()</code>\u3001<code>create.setName()</code>\u3001<code>create.setPermissions()</code> \u9a8c\u8bc1 <code>RolePermissionRequestContract</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class RolePermissionRequestContractTest {

    /** \u9a8c\u8bc1<code>create.getCode()</code> \u7b49\u4e8e <code>"admin"</code>\uff0c<code>create.code()</code> \u7b49\u4e8e <code>create.getCode()</code>\uff0c<code>update.permissions()</code> \u4e0e\u2026\u7684\u5143\u7d20\u9010\u9879\u76f8\u540c <code>update.getPermissions()</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>request.code()</code> \u7b49\u4e8e <code>request.getCode()</code>\uff0c<code>request.description()</code> \u7b49\u4e8e <code>request.getDescription()</code>\uff0c<code>request.hashCode()</code> \u7b49\u4e8e <code>same.hashCode()</code>\u3002 */
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
