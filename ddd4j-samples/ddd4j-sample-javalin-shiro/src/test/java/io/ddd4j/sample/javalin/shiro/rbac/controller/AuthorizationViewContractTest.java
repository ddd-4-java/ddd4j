package io.ddd4j.sample.javalin.shiro.rbac.controller;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthorizationViewContractTest {

    @Test
    void viewsExposeMatchingBeanAndComponentAccessors() {
        Set<String> roles = Collections.singleton("admin");
        Set<String> permissions = Collections.singleton("user:read");
        AuthorizationController.UserView user =
                new AuthorizationController.UserView("alice", "Alice", roles, permissions);
        assertEquals(user.loginId(), user.getLoginId());
        assertEquals(user.displayName(), user.getDisplayName());
        assertEquals(user.roles(), user.getRoles());
        assertEquals(user.permissions(), user.getPermissions());

        AuthorizationController.RoleView role =
                new AuthorizationController.RoleView("admin", "Administrator", permissions);
        assertEquals(role.code(), role.getCode());
        assertEquals(role.name(), role.getName());
        assertEquals(role.permissions(), role.getPermissions());

        AuthorizationController.PermissionView permission =
                new AuthorizationController.PermissionView("user:read", "Read users");
        assertEquals(permission.code(), permission.getCode());
        assertEquals(permission.description(), permission.getDescription());
    }

    @Test
    void viewsRetainThreeLineValueHashes() {
        Set<String> roles = Collections.singleton("admin");
        AuthorizationController.UserView user =
                new AuthorizationController.UserView("alice", null, roles, null);
        int userHash = 1;
        userHash = 59 * userHash + "alice".hashCode();
        userHash = 59 * userHash + 43;
        userHash = 59 * userHash + roles.hashCode();
        userHash = 59 * userHash + 43;
        assertEquals(userHash, user.hashCode());

        AuthorizationController.RoleView role =
                new AuthorizationController.RoleView("admin", null, roles);
        int roleHash = 1;
        roleHash = 59 * roleHash + "admin".hashCode();
        roleHash = 59 * roleHash + 43;
        roleHash = 59 * roleHash + roles.hashCode();
        assertEquals(roleHash, role.hashCode());

        AuthorizationController.PermissionView permission =
                new AuthorizationController.PermissionView("user:read", null);
        int permissionHash = 1;
        permissionHash = 59 * permissionHash + "user:read".hashCode();
        permissionHash = 59 * permissionHash + 43;
        assertEquals(permissionHash, permission.hashCode());
    }
}
