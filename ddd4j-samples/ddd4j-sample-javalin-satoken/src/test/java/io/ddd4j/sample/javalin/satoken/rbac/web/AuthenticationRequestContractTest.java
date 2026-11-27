package io.ddd4j.sample.javalin.satoken.rbac.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** \u9a8c\u8bc1 <code>AuthenticationRequestContract</code> \u5728 <code>loginRequestKeepsMutableBeanContract</code> \u7b49\u573a\u666f\u4e2d\u7684\u884c\u4e3a\u3002 */
class AuthenticationRequestContractTest {

    /**
     * \u9a8c\u8bc1<code>request.username()</code> \u7b49\u4e8e <code>"alice"</code>\uff0c<code>request.password()</code> \u7b49\u4e8e <code>"secret-123"</code>\uff0c<code>request.toString().contains("secret-123")</code>\u4e3a\u5047\u3002
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u65f6\u5931\u8d25
     */
    @Test
    void loginRequestRetainsJsonFieldContract() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AuthenticationController.LoginRequest request = mapper.readValue(
                "{\"username\":\"alice\",\"password\":\"secret-123\"}",
                AuthenticationController.LoginRequest.class);
        assertEquals("alice", request.username());
        assertEquals("secret-123", request.password());
        assertEquals(request, mapper.readValue(mapper.writeValueAsString(request),
                AuthenticationController.LoginRequest.class));
        assertFalse(request.toString().contains("secret-123"));
    }

    /** \u9a8c\u8bc1<code>request.getUsername()</code> \u7b49\u4e8e <code>"alice"</code>\uff0c<code>request.username()</code> \u7b49\u4e8e <code>request.getUsername()</code>\uff0c<code>request.toString().contains("secret-123")</code>\u4e3a\u5047\u3002 */
    @Test
    void loginRequestKeepsMutableBeanContractWithoutPrintingPassword() {
        AuthenticationController.LoginRequest request = new AuthenticationController.LoginRequest();
        request.setUsername("alice");
        request.setPassword("secret-123");
        assertEquals("alice", request.getUsername());
        assertEquals(request.getUsername(), request.username());
        assertEquals("secret-123", request.getPassword());
        assertEquals(request.getPassword(), request.password());
        AuthenticationController.LoginRequest same =
                new AuthenticationController.LoginRequest("alice", "secret-123");
        assertEquals(same, request);
        assertEquals(same.hashCode(), request.hashCode());
        assertFalse(request.toString().contains("secret-123"));
    }

    /** \u9a8c\u8bc1<code>kickout.userId()</code> \u7b49\u4e8e <code>kickout.getUserId()</code>\uff0c<code>role.role()</code> \u7b49\u4e8e <code>role.getRole()</code>\uff0c<code>permission.permission()</code> \u7b49\u4e8e <code>permission.getPermission()</code>\u3002 */
    @Test
    void authorizationChecksKeepMutableBeanAndValueContract() {
        AuthenticationController.KickoutRequest kickout = new AuthenticationController.KickoutRequest();
        kickout.setUserId("u-1");
        assertEquals(kickout.getUserId(), kickout.userId());
        assertEquals(new AuthenticationController.KickoutRequest("u-1"), kickout);

        AuthenticationController.RoleCheckRequest role = new AuthenticationController.RoleCheckRequest();
        role.setRole("admin");
        assertEquals(role.getRole(), role.role());
        assertEquals(new AuthenticationController.RoleCheckRequest("admin"), role);

        AuthenticationController.PermissionCheckRequest permission =
                new AuthenticationController.PermissionCheckRequest();
        permission.setPermission("order:pay");
        assertEquals(permission.getPermission(), permission.permission());
        assertEquals(new AuthenticationController.PermissionCheckRequest("order:pay"), permission);
    }
}
