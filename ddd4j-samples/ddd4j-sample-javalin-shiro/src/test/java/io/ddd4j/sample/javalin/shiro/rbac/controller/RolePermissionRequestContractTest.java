package io.ddd4j.sample.javalin.shiro.rbac.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RolePermissionRequestContractTest {

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
