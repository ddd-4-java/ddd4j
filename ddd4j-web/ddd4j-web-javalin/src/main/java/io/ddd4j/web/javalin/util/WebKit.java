package io.ddd4j.web.javalin.util;

import io.ddd4j.kit.lang.StrKit;
import io.javalin.http.Context;

import java.util.Objects;

/**
 * Javalin 请求工具。
 */
public final class WebKit {

    /** \u8bfb\u53d6\u5ba2\u6237\u7aef\u5730\u5740\u65f6\u8003\u8651\u7684\u8f6c\u53d1\u8bf7\u6c42\u5934\u5019\u9009\u3002 */
    private static final String[] IP_HEADER_CANDIDATES = {
            "X-Forwarded-For", "Proxy-Client-IP", "WL-Proxy-Client-IP", "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED", "HTTP_X_CLUSTER_CLIENT_IP", "HTTP_CLIENT_IP", "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED", "HTTP_VIA", "REMOTE_ADDR"
    };

    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>WebKit</code> \u5b9e\u4f8b\u3002 */
    private WebKit() {
    }

    /**
     * \u89e3\u6790\u8bf7\u6c42\u7684\u5ba2\u6237\u7aef\u5730\u5740\u3002
     * @param context Javalin \u8bf7\u6c42\u4e0a\u4e0b\u6587
     * @return \u5ba2\u6237\u7aef\u5730\u5740
     */
    public static String getClientIp(Context context) {
        String ip = null;
        for (String header : IP_HEADER_CANDIDATES) {
            ip = context.header(header);
            if (StrKit.isNotBlank(ip) && !"unknown".equalsIgnoreCase(ip)) {
                break;
            }
        }
        if (StrKit.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = context.ip();
        }
        if (Objects.nonNull(ip) && ip.contains(",")) {
            return ip.split(",", 2)[0].trim();
        }
        return ip;
    }

    /**
     * \u5224\u65ad\u8bf7\u6c42\u662f\u5426\u4e3a AJAX \u8bf7\u6c42\u3002
     * @param context Javalin \u8bf7\u6c42\u4e0a\u4e0b\u6587
     * @return \u662f\u5426\u4e3a AJAX \u8bf7\u6c42
     */
    public static boolean isAjax(Context context) {
        return "XMLHttpRequest".equalsIgnoreCase(context.header("X-Requested-With"));
    }
}
