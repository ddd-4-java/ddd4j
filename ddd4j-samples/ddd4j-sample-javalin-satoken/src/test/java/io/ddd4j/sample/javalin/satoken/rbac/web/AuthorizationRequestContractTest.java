package io.ddd4j.sample.javalin.satoken.rbac.web;

import io.ddd4j.sample.javalin.satoken.rbac.domain.model.Permission;
import io.ddd4j.sample.javalin.satoken.rbac.domain.model.Role;
import io.ddd4j.sample.javalin.satoken.rbac.domain.model.User;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AuthorizationRequestContractTest {

    private static void valueContract(Object bean, Object constructed) {
        assertEquals(constructed, bean);
        assertEquals(constructed.hashCode(), bean.hashCode());
        assertFalse(Modifier.isFinal(bean.getClass().getModifiers()));
    }

    @Test
    void userRequestsRetainBeanAndComponentContracts() {
        AuthorizationController.CreateUserRequest create = new AuthorizationController.CreateUserRequest();
        create.setUserId("u1");
        create.setUsername("alice");
        create.setPassword("secret");
        create.setRealName("Alice");
        assertEquals(create.getUserId(), create.userId());
        assertEquals(create.getUsername(), create.username());
        assertEquals(create.getPassword(), create.password());
        assertEquals(create.getRealName(), create.realName());
        valueContract(create, new AuthorizationController.CreateUserRequest("u1", "alice", "secret", "Alice"));

        User.Status status = User.Status.values()[0];
        AuthorizationController.UpdateUserRequest update = new AuthorizationController.UpdateUserRequest();
        update.setRealName("Alice");
        update.setPassword("new-secret");
        update.setStatus(status);
        assertEquals(update.getRealName(), update.realName());
        assertEquals(update.getPassword(), update.password());
        assertEquals(update.getStatus(), update.status());
        valueContract(update, new AuthorizationController.UpdateUserRequest("Alice", "new-secret", status));
    }

    @Test
    void roleRequestsRetainBeanAndComponentContracts() {
        AuthorizationController.CreateRoleRequest create = new AuthorizationController.CreateRoleRequest();
        create.setRoleId("r1");
        create.setRoleCode("admin");
        create.setRoleName("Admin");
        create.setDescription("manage");
        assertEquals(create.getRoleId(), create.roleId());
        assertEquals(create.getRoleCode(), create.roleCode());
        assertEquals(create.getRoleName(), create.roleName());
        assertEquals(create.getDescription(), create.description());
        valueContract(create, new AuthorizationController.CreateRoleRequest("r1", "admin", "Admin", "manage"));

        Role.Status status = Role.Status.values()[0];
        AuthorizationController.UpdateRoleRequest update = new AuthorizationController.UpdateRoleRequest();
        update.setRoleName("Admin");
        update.setDescription("manage");
        update.setStatus(status);
        assertEquals(update.getRoleName(), update.roleName());
        assertEquals(update.getDescription(), update.description());
        assertEquals(update.getStatus(), update.status());
        valueContract(update, new AuthorizationController.UpdateRoleRequest("Admin", "manage", status));
    }

    @Test
    void permissionRequestsRetainBeanAndComponentContracts() {
        AuthorizationController.CreatePermissionRequest create = new AuthorizationController.CreatePermissionRequest();
        create.setPermissionId("p1");
        create.setPermissionCode("goods:read");
        create.setPermissionName("Read goods");
        create.setModule("goods");
        assertEquals(create.getPermissionId(), create.permissionId());
        assertEquals(create.getPermissionCode(), create.permissionCode());
        assertEquals(create.getPermissionName(), create.permissionName());
        assertEquals(create.getModule(), create.module());
        valueContract(create, new AuthorizationController.CreatePermissionRequest(
                "p1", "goods:read", "Read goods", "goods"));

        Permission.Status status = Permission.Status.values()[0];
        AuthorizationController.UpdatePermissionRequest update =
                new AuthorizationController.UpdatePermissionRequest();
        update.setPermissionName("Read goods");
        update.setModule("goods");
        update.setStatus(status);
        assertEquals(update.getPermissionName(), update.permissionName());
        assertEquals(update.getModule(), update.module());
        assertEquals(update.getStatus(), update.status());
        valueContract(update, new AuthorizationController.UpdatePermissionRequest("Read goods", "goods", status));
    }

    @Test
    void assignmentRequestsRetainBeanAndComponentContracts() {
        AuthorizationController.AssignRolesRequest roles = new AuthorizationController.AssignRolesRequest();
        roles.setRoleIds(new ArrayList<>(Arrays.asList("admin", "auditor")));
        assertEquals(roles.getRoleIds(), roles.roleIds());
        valueContract(roles, new AuthorizationController.AssignRolesRequest(
                new ArrayList<>(Arrays.asList("admin", "auditor"))));

        AuthorizationController.AssignPermissionsRequest permissions =
                new AuthorizationController.AssignPermissionsRequest();
        permissions.setPermissionIds(new ArrayList<>(Arrays.asList("goods:read", "goods:write")));
        assertEquals(permissions.getPermissionIds(), permissions.permissionIds());
        valueContract(permissions, new AuthorizationController.AssignPermissionsRequest(
                new ArrayList<>(Arrays.asList("goods:read", "goods:write"))));
    }
}
