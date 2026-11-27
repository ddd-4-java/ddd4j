/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

    /**
     * 默认机器信息提供器实例。
     */
    public static final DefaultLicenseMachineInfoProvider INSTANCE = new DefaultLicenseMachineInfoProvider();
    /**
     * 机器序列号系统属性名称。
     */
    public static final String MACHINE_SN_PROPERTY = "ddd4j.license.machine-sn";
    /**
     * 机器序列号环境变量名称。
     */
    public static final String MACHINE_SN_ENV = "DDD4J_LICENSE_MACHINE_SN";

    /**
     * 限制调用方直接创建 <code>DefaultLicenseMachineInfoProvider</code> 实例。
     */
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
     * 枚举可用网络接口并将其交给机器信息收集回调。
     *
     * @param consumer 接收每个可用接口的回调
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

    /**
     * 表示读取许可绑定所需网络接口信息的回调。
     */
    @FunctionalInterface
    private interface NetworkInterfaceConsumer {
        /**
         * 消费一个已枚举的网络接口。
         *
         * @param networkInterface 当前可用网络接口
         * @throws Exception 无法从当前接口读取所需信息
         */
        void accept(NetworkInterface networkInterface) throws Exception;
    }
}
