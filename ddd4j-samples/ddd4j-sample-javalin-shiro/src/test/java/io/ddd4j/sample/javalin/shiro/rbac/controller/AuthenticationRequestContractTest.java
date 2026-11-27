package io.ddd4j.sample.javalin.shiro.rbac.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 验证 <code>AuthenticationRequestContract</code> 在 <code>loginRequestKeepsMutableBeanContract</code> 等场景中的行为。
 */
class AuthenticationRequestContractTest {

    /**
     * 验证<code>request.getLoginId()</code> 等于 <code>"alice"</code>，<code>request.loginId()</code> 等于 <code>request.getLoginId()</code>，<code>request.toString().contains("secret-123")</code>为假。
     */
    @Test
    void loginRequestKeepsMutableBeanContractWithoutPrintingPassword() {
        AuthenticationController.LoginRequest request = new AuthenticationController.LoginRequest();
        request.setLoginId("alice");
        request.setPassword("secret-123");
        assertEquals("alice", request.getLoginId());
        assertEquals(request.getLoginId(), request.loginId());
        assertEquals("secret-123", request.getPassword());
        assertEquals(request.getPassword(), request.password());
        AuthenticationController.LoginRequest same =
                new AuthenticationController.LoginRequest("alice", "secret-123");
        assertEquals(same, request);
        assertEquals(same.hashCode(), request.hashCode());
        assertFalse(request.toString().contains("secret-123"));
    }
}
