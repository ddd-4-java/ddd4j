package io.ddd4j.kit.web;

import cn.hutool.core.net.Ipv4Util;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import io.ddd4j.kit.lang.StrKit;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * IP 地址工具（纯 Java，不依赖 Servlet / Spring）。
 *
 * <p>Servlet 适配由具体框架项目提供，例如：
 * <ul>
 *   <li>Spring WebMVC: {@code ddd4j-boot-web-webmvc.ServletIpKit.getRemoteAddr(HttpServletRequest)}</li>
 *   <li>Quarkus RESTEasy: {@code ddd4j-quarkus-web.ServletIpKit.getRemoteAddr(HttpServerRequest)}</li>
 *   <li>Javalin: {@code ddd4j-javalin-api.ServletIpKit.getRemoteAddr(Context)}</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j(topic = "### BASE-KIT : IpKit ###")
@UtilityClass
public class IpKit extends Ipv4Util {

    public String HOST_ADDRESS = "";

    // 获取本机Ip
    public String getLocalAddress() {
        if (StrKit.isNotBlank(HOST_ADDRESS)) {
            return HOST_ADDRESS;
        }
        try {
            Enumeration<NetworkInterface> allNetInterfaces = NetworkInterface.getNetworkInterfaces();
            InetAddress ip;
            while (allNetInterfaces.hasMoreElements()) {
                NetworkInterface netInterface = allNetInterfaces.nextElement();
                if (!netInterface.isLoopback() && !netInterface.isVirtual() && netInterface.isUp()) {
                    Enumeration<InetAddress> addresses = netInterface.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        ip = addresses.nextElement();
                        if (ip instanceof Inet4Address) {
                            String hostAddress = ip.getHostAddress();
                            if (!hostAddress.endsWith(".0.1")) {
                                HOST_ADDRESS = hostAddress;
                                return hostAddress;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("getLocalAddress error", e);
        }
        return "";
    }

    public String getHostIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
        }
        return "127.0.0.1";
    }

    public String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
        }
        return "未知";
    }

    // 通过IP获取地址信息
    public String getAddresses(String ip) {
        try {
            // 这里调用pconline的接口
            String url = "http://whois.pconline.com.cn/ipJson.jsp";
            Map<String, Object> paramMap = new HashMap<>();
            paramMap.put("json", true);
            paramMap.put("ip", ip);
            // 带参GET请求
            String returnStr = HttpUtil.get(url, paramMap);
            if (returnStr != null) {
                JSONObject rs = JSONUtil.parseObj(returnStr);
                String region = rs.getStr("addr");
                return region;
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    // 通过IP获取省市
    public static Map<String, String> getProCity(String ip) {
        try {
            // 这里调用pconline的接口
            String url = "http://whois.pconline.com.cn/ipJson.jsp";
            Map<String, Object> paramMap = new HashMap<>();
            paramMap.put("json", true);
            paramMap.put("ip", ip);
            // 带参GET请求
            String returnStr = HttpUtil.get(url, paramMap);
            if (returnStr != null) {
                JSONObject rs = JSONUtil.parseObj(returnStr);
                Map<String, String> map = new HashMap<>();
                map.put("pro", rs.getStr("pro"));
                map.put("city", rs.getStr("city"));
                map.put("region", rs.getStr("region"));
                return map;
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    /**
     * 纯 Java 解析客户端真实 IP（不依赖 Servlet API）。
     *
     * <p>Servlet 适配层（{@code ddd4j-boot-web-webmvc.ServletIpKit} 等）应调用本方法：
     * <pre>{@code
     * String ip = IpKit.parseRemoteAddr(
     *     request.getHeader("X-Forwarded-For"),
     *     request.getHeader("X-Real-IP"),
     *     request.getRemoteAddr()
     * );
     * }</pre>
     *
     * @param xForwardedFor X-Forwarded-For 头（多级代理时第一个为真实 IP）
     * @param xRealIp       X-Real-IP 头
     * @param remoteAddr    request.getRemoteAddr() 兜底
     * @return 客户端真实 IP
     */
    public static String parseRemoteAddr(String xForwardedFor, String xRealIp, String remoteAddr) {
        if (StrKit.isNotBlank(xForwardedFor) && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            int index = xForwardedFor.indexOf(',');
            return index > 0 ? xForwardedFor.substring(0, index).trim() : xForwardedFor.trim();
        }
        if (StrKit.isNotBlank(xRealIp) && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp;
        }
        return remoteAddr;
    }
}
