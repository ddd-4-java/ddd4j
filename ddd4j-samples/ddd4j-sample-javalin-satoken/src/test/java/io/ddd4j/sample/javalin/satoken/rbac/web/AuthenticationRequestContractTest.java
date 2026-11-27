package io.ddd4j.sample.javalin.satoken.rbac.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 验证 <code>AuthenticationRequestContract</code> 在 <code>loginRequestKeepsMutableBeanContract</code> 等场景中的行为。
 */
class AuthenticationRequestContractTest {

    /**
     * 验证<code>request.username()</code> 等于 <code>"alice"</code>，<code>request.password()</code> 等于 <code>"secret-123"</code>，<code>request.toString().contains("secret-123")</code>为假。
     *
     * @throws Exception 处理测试数据的序列化时失败
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

    /**
     * 验证<code>request.getUsername()</code> 等于 <code>"alice"</code>，<code>request.username()</code> 等于 <code>request.getUsername()</code>，<code>request.toString().contains("secret-123")</code>为假。
     */
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

    /**
     * 验证<code>kickout.userId()</code> 等于 <code>kickout.getUserId()</code>，<code>role.role()</code> 等于 <code>role.getRole()</code>，<code>permission.permission()</code> 等于 <code>permission.getPermission()</code>。
     */
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
