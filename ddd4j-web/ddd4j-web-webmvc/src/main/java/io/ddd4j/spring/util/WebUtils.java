package io.ddd4j.spring.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Objects;

/**
 * Web 工具类。
 *
 * <p>该实现依赖 Servlet RequestContext，归属 {@code ddd4j-web-webmvc}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public final class WebUtils {

    private WebUtils() {
    }

    /**
     * 获取当前 HttpServletRequest
     */
    public static HttpServletRequest getHttpServletRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return Objects.nonNull(attrs) ? attrs.getRequest() : null;
    }

    /**
     * 获取客户端真实 IP
     */
    public static String getRemoteAddr(HttpServletRequest request) {
        if (Objects.isNull(request)) {
            return null;
        }
        String addr = request.getHeader("X-Forwarded-For");
        if (Objects.nonNull(addr) && StringUtils.hasLength(addr) && !"unknown".equalsIgnoreCase(addr)) {
            int index = addr.indexOf(',');
            return index > 0 ? addr.substring(0, index).trim() : addr.trim();
        }
        addr = request.getHeader("X-Real-IP");
        if (Objects.nonNull(addr) && StringUtils.hasLength(addr) && !"unknown".equalsIgnoreCase(addr)) {
            return addr;
        }
        return request.getRemoteAddr();
    }

}
