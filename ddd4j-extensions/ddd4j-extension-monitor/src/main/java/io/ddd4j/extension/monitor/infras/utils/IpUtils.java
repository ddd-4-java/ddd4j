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
 * @author Loong Wan
 * @公众号 PartMe.AI
 */
@Slf4j(topic = "### BASE-MONITOR : IpUtils ###")
public class IpUtils {

    public static String HOST_ADDRESS = "";

    // 获取本机Ip
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
