package io.ddd4j.sample.javalin.shiro.rbac.controller;

import io.ddd4j.sample.javalin.shiro.rbac.controller.AuthorizationController.CreateUserRequest;
import io.ddd4j.sample.javalin.shiro.rbac.controller.AuthorizationController.UpdateUserRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 通过 <code>request.setLoginId()</code>、<code>request.setPassword()</code>、<code>request.setDisplayName()</code> 验证 <code>UserRequestContract</code> 的运行行为。
 */
class UserRequestContractTest {

    /**
     * 验证<code>request.loginId()</code> 等于 <code>request.getLoginId()</code>，<code>request.password()</code> 等于 <code>request.getPassword()</code>，<code>request.toString().contains("secret")</code>为假。
     */
    @Test
    void createUserSupportsBeanAndComponentAccessorsAndArrayValues() {
        CreateUserRequest request = new CreateUserRequest();
        request.setLoginId("alice");
        request.setPassword("secret");
        request.setDisplayName("Alice");
        request.setRoles(new String[]{"admin"});
        request.setPermissions(new String[]{"goods:read"});

        assertEquals(request.getLoginId(), request.loginId());
        assertEquals(request.getPassword(), request.password());
        assertEquals(request.getDisplayName(), request.displayName());
        assertArrayEquals(request.getRoles(), request.roles());
        assertArrayEquals(request.getPermissions(), request.permissions());

        CreateUserRequest same = new CreateUserRequest("alice", "secret", "Alice",
                new String[]{"admin"}, new String[]{"goods:read"});
        assertEquals(same, request);
        assertEquals(same.hashCode(), request.hashCode());
        assertFalse(request.toString().contains("secret"));
        assertEquals(request, new CreateUserRequest("alice", "secret", "Alice",
                new String[]{"admin"}, new String[]{"goods:read"}) {
        });
    }

    /**
     * 验证<code>request.displayName()</code> 等于 <code>request.getDisplayName()</code>，<code>request.password()</code> 等于 <code>request.getPassword()</code>，<code>request.toString().contains("secret")</code>为假。
     */
    @Test
    void updateUserSupportsBeanAndComponentAccessorsAndArrayValues() {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setDisplayName("Alice");
        request.setPassword("secret");
        request.setRoles(new String[]{"admin"});
        request.setPermissions(new String[]{"goods:read"});

        assertEquals(request.getDisplayName(), request.displayName());
        assertEquals(request.getPassword(), request.password());
        assertArrayEquals(request.getRoles(), request.roles());
        assertArrayEquals(request.getPermissions(), request.permissions());

        UpdateUserRequest same = new UpdateUserRequest("Alice", "secret",
                new String[]{"admin"}, new String[]{"goods:read"});
        assertEquals(same, request);
        assertEquals(same.hashCode(), request.hashCode());
        assertFalse(request.toString().contains("secret"));
        assertEquals(request, new UpdateUserRequest("Alice", "secret",
                new String[]{"admin"}, new String[]{"goods:read"}) {
        });
    }
}
