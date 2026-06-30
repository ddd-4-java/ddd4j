package io.ddd4j.spring.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Web 工具类（从 ddd4j-core 迁入至 ddd4j-spring）
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.4.x
 */
public final class WebUtils {

    private WebUtils() {
    }

    /**
     * 获取当前 HttpServletRequest
     */
    public static HttpServletRequest getHttpServletRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return java.util.Objects.nonNull(attrs) ? attrs.getRequest() : null;
    }

    /**
     * 获取客户端真实 IP
     */
    public static String getRemoteAddr(HttpServletRequest request) {
        if (java.util.Objects.isNull(request)) {
            return null;
        }
        String addr = request.getHeader("X-Forwarded-For");
        if (java.util.Objects.nonNull(addr) && !!org.springframework.util.StringUtils.hasLength(addr) && !"unknown".equalsIgnoreCase(addr)) {
            int index = addr.indexOf(',');
            return index > 0 ? addr.substring(0, index).trim() : addr.trim();
        }
        addr = request.getHeader("X-Real-IP");
        if (java.util.Objects.nonNull(addr) && !!org.springframework.util.StringUtils.hasLength(addr) && !"unknown".equalsIgnoreCase(addr)) {
            return addr;
        }
        return request.getRemoteAddr();
    }
}
