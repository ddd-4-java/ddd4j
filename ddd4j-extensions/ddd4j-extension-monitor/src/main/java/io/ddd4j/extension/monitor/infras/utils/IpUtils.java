package io.ddd4j.extension.monitor.infras.utils;

import io.ddd4j.kit.lang.StrKit;
import lombok.extern.slf4j.Slf4j;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/**
 * 获取本机ip
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j(topic = "### BASE-MONITOR : IpUtils ###")
public class IpUtils {

    /**
     * 本机 IP 地址缓存
     */
    public static String HOST_ADDRESS = "";

    /**
     * 获取本机 IPv4 地址
     *
     * <p>遍历所有网络接口，排除回环接口和虚拟接口，返回第一个非 .0.1 结尾的 IPv4 地址。
     *
     * @return 本机 IP 地址，如果获取失败返回空字符串
     */
    public static String getLocalAddress() {
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
            log.warn("getIpAddress error", e);
        }
        return "";
    }
}
