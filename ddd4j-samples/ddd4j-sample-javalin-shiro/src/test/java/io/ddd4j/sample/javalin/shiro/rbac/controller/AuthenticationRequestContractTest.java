package io.ddd4j.sample.javalin.shiro.rbac.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** \u9a8c\u8bc1 <code>AuthenticationRequestContract</code> \u5728 <code>loginRequestKeepsMutableBeanContract</code> \u7b49\u573a\u666f\u4e2d\u7684\u884c\u4e3a\u3002 */
class AuthenticationRequestContractTest {

    /** \u9a8c\u8bc1<code>request.getLoginId()</code> \u7b49\u4e8e <code>"alice"</code>\uff0c<code>request.loginId()</code> \u7b49\u4e8e <code>request.getLoginId()</code>\uff0c<code>request.toString().contains("secret-123")</code>\u4e3a\u5047\u3002 */
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
