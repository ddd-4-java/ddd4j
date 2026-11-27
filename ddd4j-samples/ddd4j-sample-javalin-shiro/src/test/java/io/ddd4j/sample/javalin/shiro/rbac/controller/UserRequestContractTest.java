package io.ddd4j.sample.javalin.shiro.rbac.controller;

import io.ddd4j.sample.javalin.shiro.rbac.controller.AuthorizationController.CreateUserRequest;
import io.ddd4j.sample.javalin.shiro.rbac.controller.AuthorizationController.UpdateUserRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** \u901a\u8fc7 <code>request.setLoginId()</code>\u3001<code>request.setPassword()</code>\u3001<code>request.setDisplayName()</code> \u9a8c\u8bc1 <code>UserRequestContract</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class UserRequestContractTest {

    /** \u9a8c\u8bc1<code>request.loginId()</code> \u7b49\u4e8e <code>request.getLoginId()</code>\uff0c<code>request.password()</code> \u7b49\u4e8e <code>request.getPassword()</code>\uff0c<code>request.toString().contains("secret")</code>\u4e3a\u5047\u3002 */
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

    /** \u9a8c\u8bc1<code>request.displayName()</code> \u7b49\u4e8e <code>request.getDisplayName()</code>\uff0c<code>request.password()</code> \u7b49\u4e8e <code>request.getPassword()</code>\uff0c<code>request.toString().contains("secret")</code>\u4e3a\u5047\u3002 */
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
