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

    public static final DefaultLicenseMachineInfoProvider INSTANCE = new DefaultLicenseMachineInfoProvider();
    public static final String MACHINE_SN_PROPERTY = "ddd4j.license.machine-sn";
    public static final String MACHINE_SN_ENV = "DDD4J_LICENSE_MACHINE_SN";

    private DefaultLicenseMachineInfoProvider() {
    }

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

    @FunctionalInterface
    private interface NetworkInterfaceConsumer {
        void accept(NetworkInterface networkInterface) throws Exception;
    }
}
