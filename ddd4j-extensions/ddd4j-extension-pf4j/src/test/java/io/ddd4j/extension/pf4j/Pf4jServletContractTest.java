package io.ddd4j.extension.pf4j;

import io.ddd4j.extension.pf4j.point.authc.AuthcExtensionPoint;
import io.ddd4j.extension.pf4j.point.authc.AuthcExtensionPointAdapter;
import io.ddd4j.extension.pf4j.point.web.ParamSignatureExtensionPoint;
import io.ddd4j.extension.pf4j.point.web.ServletRequestExtensionPoint;
import io.ddd4j.extension.pf4j.point.web.ServletResponseExtensionPoint;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Pf4jServletContractTest {

    @Test
    void pluginExtensionPointsUseThisLinesServletApi() throws Exception {
        assertEquals(String.class, AuthcExtensionPoint.class
                .getMethod("getToken", HttpServletRequest.class, Map.class).getReturnType());
        AuthcExtensionPointAdapter.class.getMethod("getToken", HttpServletRequest.class, Map.class);
        AuthcExtensionPoint.class.getMethod("handleHeader", HttpServletRequest.class, Map.class);
        AuthcExtensionPoint.class.getMethod("handleRequest", HttpServletRequest.class, Map.class);
        ParamSignatureExtensionPoint.class.getMethod("sign", HttpServletRequest.class, Map.class);
        ServletRequestExtensionPoint.class.getMethod("wrap", HttpServletRequest.class, Map.class);
        ServletResponseExtensionPoint.class.getMethod("dewrap", HttpServletResponse.class, Map.class);

        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(), new Class<?>[] {HttpServletRequest.class},
                (proxy, method, args) -> "getHeader".equals(method.getName()) ? "Bearer token-123" : null);
        assertEquals("token-123", new AuthcExtensionPointAdapter().getToken(request, new HashMap<>()));
    }
}
