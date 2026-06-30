package io.ddd4j.web.webmvc.extension.authc;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.pf4j.PluginRuntimeException;

import java.util.Enumeration;
import java.util.Map;

/**
 * 认证扩展点默认适配器（Spring Web 适配）。
 *
 * <p>从 ddd4j-extension-pf4j 迁入至 ddd4j-web-webmvc 模块。
 * 提供 PF4J 插件体系下的认证扩展默认实现，可由插件覆盖：
 * <ul>
 *   <li>{@link #getToken}：从 Authorization Header 提取 Bearer Token</li>
 *   <li>{@link #handleHeader}：空实现（插件可覆盖做 Header 预处理）</li>
 *   <li>{@link #handleRequest}：将请求参数拷贝到 params Map</li>
 *   <li>{@link #handleResult}：原样返回结果</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public class AuthcExtensionPointAdapter implements AuthcExtensionPoint {

    public static final String AUTHORIZATION_HEADER = "Authorization";
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
        return res;
    }

}
