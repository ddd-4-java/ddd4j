package io.ddd4j.sample.javalin.satoken.rbac.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AuthenticationRequestContractTest {

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
