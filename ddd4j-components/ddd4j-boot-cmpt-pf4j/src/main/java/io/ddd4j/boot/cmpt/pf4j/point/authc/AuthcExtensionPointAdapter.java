package io.ddd4j.boot.cmpt.pf4j.point.authc;

import jakarta.servlet.http.HttpServletRequest;
import org.pf4j.PluginRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * 认证扩展点默认适配器。
 *
 * <p>提供 PF4J 插件体系下的认证扩展默认实现。插件可通过实现 {@link AuthcExtensionPoint}
 * 覆盖这些行为，实现自定义认证逻辑（如自定义 Token 提取、请求预处理等）。
 *
 * <p>本适配器的默认行为：
 * <ul>
 *   <li>{@link #getToken}：从 Authorization Header 提取 Bearer Token</li>
 *   <li>{@link #handleHeader}：空实现（插件可覆盖做 Header 预处理）</li>
 *   <li>{@link #handleRequest}：将请求参数拷贝到 params Map</li>
 *   <li>{@link #handleResult}：原样返回结果</li>
 * </ul>
 *
 * @author wandl
 * @since 3.4.x
 */
public class AuthcExtensionPointAdapter implements AuthcExtensionPoint {

    private static final Logger log = LoggerFactory.getLogger(AuthcExtensionPointAdapter.class);

    /** Authorization Header 名称 */
    public static final String AUTHORIZATION_HEADER = "Authorization";

    /** Bearer Token 前缀 */
    public static final String BEARER_PREFIX = "Bearer ";

    @Override
    public String getToken(HttpServletRequest request, Map<String, Object> params) throws PluginRuntimeException {
        if (request == null) {
            return null;
        }
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length()).trim();
        }
        // 兜底：尝试从 query parameter 提取
        String tokenParam = request.getParameter("token");
        if (tokenParam != null && !tokenParam.isBlank()) {
            return tokenParam.trim();
        }
        return null;
    }

    @Override
    public void handleHeader(HttpServletRequest request, Map<String, Object> params) throws PluginRuntimeException {
        if (request == null || params == null) {
            return;
        }
        // 默认实现：将所有自定义 Header（X- 前缀）拷贝到 params
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames != null && headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            if (name != null && name.toLowerCase().startsWith("x-")) {
                params.putIfAbsent(name, request.getHeader(name));
            }
        }
    }

    @Override
    public void handleRequest(HttpServletRequest request, Map<String, Object> params) throws PluginRuntimeException {
        if (request == null || params == null) {
            return;
        }
        // 默认实现：将请求参数拷贝到 params
        Map<String, String[]> paramMap = request.getParameterMap();
        if (paramMap != null) {
            for (Map.Entry<String, String[]> entry : paramMap.entrySet()) {
                String[] values = entry.getValue();
                if (values != null && values.length > 0) {
                    params.putIfAbsent(entry.getKey(), values.length == 1 ? values[0] : values);
                }
            }
        }
    }

    @Override
    public Object handleResult(Object res) throws PluginRuntimeException {
        // 默认实现：原样返回结果
        return res;
    }

}
