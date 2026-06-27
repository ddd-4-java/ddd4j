package io.ddd4j.spring.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Web 工具类（从 ddd4j-core 迁入至 ddd4j-spring）
 *
 * @author Loong Wan
 * @since 3.4.x
 */
public final class WebUtils {

    private WebUtils() {}

    /**
     * 获取当前 HttpServletRequest
     */
    public static HttpServletRequest getHttpServletRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
    }

    /**
     * 获取客户端真实 IP
     */
    public static String getRemoteAddr(HttpServletRequest request) {
        if (request == null) return null;
        String addr = request.getHeader("X-Forwarded-For");
        if (addr != null && !addr.isEmpty() && !"unknown".equalsIgnoreCase(addr)) {
            int index = addr.indexOf(',');
            return index > 0 ? addr.substring(0, index).trim() : addr.trim();
        }
        addr = request.getHeader("X-Real-IP");
        if (addr != null && !addr.isEmpty() && !"unknown".equalsIgnoreCase(addr)) {
            return addr;
        }
        return request.getRemoteAddr();
    }
}
