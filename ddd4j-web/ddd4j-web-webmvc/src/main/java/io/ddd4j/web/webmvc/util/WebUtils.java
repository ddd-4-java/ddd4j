package io.ddd4j.web.webmvc.util;

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

    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>WebUtils</code> \u5b9e\u4f8b\u3002 */
    private WebUtils() {
    }

    /**
     * \u83b7\u53d6\u5f53\u524d HttpServletRequest
     *
     * @return \u5f53\u524d HTTP \u8bf7\u6c42
     */
    public static HttpServletRequest getHttpServletRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return Objects.nonNull(attrs) ? attrs.getRequest() : null;
    }

    /**
     * \u83b7\u53d6\u5ba2\u6237\u7aef\u771f\u5b9e IP
     *
     * @return \u8bf7\u6c42\u7684\u8fdc\u7aef\u5730\u5740
     * @param request HTTP \u8bf7\u6c42
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
