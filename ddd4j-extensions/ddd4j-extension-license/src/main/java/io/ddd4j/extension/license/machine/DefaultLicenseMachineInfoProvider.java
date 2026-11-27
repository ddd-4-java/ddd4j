package io.ddd4j.extension.license.machine;

import io.ddd4j.kit.lang.StrKit;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 基于 JVM 与网卡信息的默认机器信息采集策略。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public final class DefaultLicenseMachineInfoProvider implements LicenseMachineInfoProvider {

    /** \u9ed8\u8ba4\u673a\u5668\u4fe1\u606f\u63d0\u4f9b\u5668\u5b9e\u4f8b\u3002 */
    public static final DefaultLicenseMachineInfoProvider INSTANCE = new DefaultLicenseMachineInfoProvider();
    /** \u673a\u5668\u5e8f\u5217\u53f7\u7cfb\u7edf\u5c5e\u6027\u540d\u79f0\u3002 */
    public static final String MACHINE_SN_PROPERTY = "ddd4j.license.machine-sn";
    /** \u673a\u5668\u5e8f\u5217\u53f7\u73af\u5883\u53d8\u91cf\u540d\u79f0\u3002 */
    public static final String MACHINE_SN_ENV = "DDD4J_LICENSE_MACHINE_SN";

    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>DefaultLicenseMachineInfoProvider</code> \u5b9e\u4f8b\u3002 */
    private DefaultLicenseMachineInfoProvider() {
    }

    /**
     * 获取当前机器的 IP 地址。
     *
     * @return 实际返回<code>addresses</code>，供调用方取得当前值
     */
    @Override
    public Set<String> ipAddresses() {
        Set<String> addresses = new LinkedHashSet<>();
        forEachAvailableNetworkInterface(networkInterface -> {
            Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
            while (inetAddresses.hasMoreElements()) {
                InetAddress address = inetAddresses.nextElement();
                if (!address.isLoopbackAddress()) {
                    addresses.add(address.getHostAddress());
                }
            }
        });
        return addresses;
    }

    /**
     * 获取当前机器的 MAC 地址。
     *
     * @return 实际返回<code>addresses</code>，供调用方取得当前值
     */
    @Override
    public Set<String> macAddresses() {
        Set<String> addresses = new LinkedHashSet<>();
        forEachAvailableNetworkInterface(networkInterface -> {
            byte[] hardwareAddress = networkInterface.getHardwareAddress();
            if (Objects.isNull(hardwareAddress) || hardwareAddress.length == 0) {
                return;
            }
            StringBuilder address = new StringBuilder();
            for (int index = 0; index < hardwareAddress.length; index++) {
                address.append(String.format("%02X", hardwareAddress[index]));
                if (index < hardwareAddress.length - 1) {
                    address.append(':');
                }
            }
            addresses.add(address.toString());
        });
        return addresses;
    }

    /**
     * 获取机器序列号。
     *
     * <p>此实现调用 <code>System.getProperty(MACHINE_SN_PROPERTY)</code>。</p>
     *
     * @return 实际返回<code>serialNumber.trim()</code>
     */
    @Override
    public String serialNumber() {
        String serialNumber = System.getProperty(MACHINE_SN_PROPERTY);
        if (Objects.isNull(serialNumber) || StrKit.isEmpty(serialNumber.trim())) {
            serialNumber = System.getenv(MACHINE_SN_ENV);
        }
        if (Objects.nonNull(serialNumber) && !StrKit.isEmpty(serialNumber.trim())) {
            return serialNumber.trim();
        }
        return System.getProperty("os.name") + ":" + System.getProperty("user.name");
    }

    /**
     * \u679a\u4e3e\u53ef\u7528\u7f51\u7edc\u63a5\u53e3\u5e76\u5c06\u5176\u4ea4\u7ed9\u673a\u5668\u4fe1\u606f\u6536\u96c6\u56de\u8c03\u3002
     * @param consumer \u63a5\u6536\u6bcf\u4e2a\u53ef\u7528\u63a5\u53e3\u7684\u56de\u8c03
     */
    private void forEachAvailableNetworkInterface(NetworkInterfaceConsumer consumer) {
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (Objects.nonNull(networkInterfaces) && networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }
                consumer.accept(networkInterface);
            }
        } catch (Exception e) {
            log.warn("获取本机网络标识失败", e);
        }
    }

    /** \u8868\u793a\u8bfb\u53d6\u8bb8\u53ef\u7ed1\u5b9a\u6240\u9700\u7f51\u7edc\u63a5\u53e3\u4fe1\u606f\u7684\u56de\u8c03\u3002 */
    @FunctionalInterface
    private interface NetworkInterfaceConsumer {
        /**
         * \u6d88\u8d39\u4e00\u4e2a\u5df2\u679a\u4e3e\u7684\u7f51\u7edc\u63a5\u53e3\u3002
         * @param networkInterface \u5f53\u524d\u53ef\u7528\u7f51\u7edc\u63a5\u53e3
         * @throws Exception \u65e0\u6cd5\u4ece\u5f53\u524d\u63a5\u53e3\u8bfb\u53d6\u6240\u9700\u4fe1\u606f
         */
        void accept(NetworkInterface networkInterface) throws Exception;
    }
}
