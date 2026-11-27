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

/** \u901a\u8fc7 <code>AuthcExtensionPoint.class.getMethod()</code>\u3001<code>AuthcExtensionPointAdapter.class.getMethod()</code>\u3001<code>ParamSignatureExtensionPoint.class.getMethod()</code> \u9a8c\u8bc1 <code>Pf4jServletContract</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class Pf4jServletContractTest {

    /**
     * \u9a8c\u8bc1<code>AuthcExtensionPoint.class .getMethod("getToken", HttpServletRequest.class, Map.class).getReturnType()</code> \u7b49\u4e8e <code>String.class</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
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
                HttpServletRequest.class.getClassLoader(), new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> "getHeader".equals(method.getName()) ? "Bearer token-123" : null);
        assertEquals("token-123", new AuthcExtensionPointAdapter().getToken(request, new HashMap<>()));
    }
}
