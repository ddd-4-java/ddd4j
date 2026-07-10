/*
 * Copyright 2017-2026 the original author hiwepy.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.guice.util;

import io.javalin.http.Context;
import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.Objects;

/**
 * Javalin Web 工具类（等价于 Spring 的 WebUtils）。
 * <p>
 * 提供从 Javalin Context 获取 HttpServletRequest、客户端真实 IP 等能力。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class WebKit {

    /**
     * 客户端 IP 获取时依次尝试的 HTTP 请求头
     */
    private static final String[] IP_HEADER_CANDIDATES = {
            "X-Forwarded-For",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED",
            "HTTP_X_CLUSTER_CLIENT_IP",
            "HTTP_CLIENT_IP",
            "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED",
            "HTTP_VIA",
            "REMOTE_ADDR"
    };

    private WebKit() {
    }

    /**
     * 获取 HttpServletRequest
     */
    public static HttpServletRequest getServletRequest(Context ctx) {
        return ctx.req();
    }

    /**
     * 获取客户端真实 IP（支持多级代理）
     */
    public static String getClientIp(Context ctx) {
        HttpServletRequest request = ctx.req();
        return getClientIp(request);
    }

    /**
     * 获取客户端真实 IP（支持多级代理）
     */
    public static String getClientIp(HttpServletRequest request) {
        String ip = null;
        for (String header : IP_HEADER_CANDIDATES) {
            ip = request.getHeader(header);
            if (Objects.nonNull(ip) && !io.ddd4j.kit.lang.StrKit.isEmpty(ip) && !"unknown".equalsIgnoreCase(ip)) {
                break;
            }
        }
        if (Objects.isNull(ip) || io.ddd4j.kit.lang.StrKit.isEmpty(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多个代理时取第一个
        if (Objects.nonNull(ip) && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        // 本地回环地址处理
        if ("127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip)) {
            ip = getLocalIp();
        }
        return ip;
    }

    /**
     * 获取本机 IP
     */
    public static String getLocalIp() {
        try {
            InetAddress addr = InetAddress.getLocalHost();
            return addr.getHostAddress();
        } catch (Exception e) {
            try {
                Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                while (interfaces.hasMoreElements()) {
                    NetworkInterface ni = interfaces.nextElement();
                    Enumeration<InetAddress> addresses = ni.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        InetAddress address = addresses.nextElement();
                        if (!address.isLoopbackAddress() && !address.isLinkLocalAddress()
                                && address instanceof java.net.Inet4Address) {
                            return address.getHostAddress();
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return "127.0.0.1";
    }

    /**
     * 获取请求 URL
     */
    public static String getRequestUrl(Context ctx) {
        return ctx.url();
    }

    /**
     * 获取请求方法
     */
    public static String getMethod(Context ctx) {
        return ctx.method().name();
    }

    /**
     * 获取 User-Agent
     */
    public static String getUserAgent(Context ctx) {
        return ctx.userAgent();
    }

    /**
     * 获取 Referer
     */
    public static String getReferer(Context ctx) {
        return ctx.header("Referer");
    }

    /**
     * 判断是否为 AJAX 请求
     */
    public static boolean isAjax(Context ctx) {
        String xRequestedWith = ctx.header("X-Requested-With");
        return "XMLHttpRequest".equalsIgnoreCase(xRequestedWith);
    }

    /**
     * 获取请求的 Content-Type
     */
    public static String getContentType(Context ctx) {
        return ctx.contentType();
    }
}
