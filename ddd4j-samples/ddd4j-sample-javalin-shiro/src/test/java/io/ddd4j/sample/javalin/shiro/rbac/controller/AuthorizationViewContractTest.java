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
}
