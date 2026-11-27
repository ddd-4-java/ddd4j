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
package io.ddd4j.data.external.region;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * IP address helpers independent of Boot starters.
 */
public final class IpAddressKit {

    private static final Pattern IPV4 = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)$");

    private IpAddressKit() {
    }

    /**
     * 判断是否为 IPv4 地址
     *
     * @param ip IP 地址字符串
     * @return true 如果是有效的 IPv4 地址
     */
    public static boolean isIpv4(String ip) {
        return Objects.nonNull(ip) && IPV4.matcher(ip).matches();
    }

    /**
     * 判断是否为内网 IP 地址
     *
     * @param ip IP 地址字符串
     * @return true 如果是内网 IP
     */
    public static boolean internalIp(String ip) {
        if (!isIpv4(ip)) {
            return false;
        }
        String[] parts = ip.split("\\.");
        int first = Integer.parseInt(parts[0]);
        int second = Integer.parseInt(parts[1]);
        return first == 10
                || first == 127
                || first == 0
                || first == 169 && second == 254
                || first == 172 && second >= 16 && second <= 31
                || first == 192 && second == 168;
    }

    /**
     * 将 IP 地址转换为长整型数字
     *
     * @param ip IP 地址字符串
     * @return 长整型表示的 IP 地址
     */
    public static long ip2long(String ip) {
        String[] parts = ip.split("\\.");
        long result = 0L;
        for (String part : parts) {
            result = result << 8 | Integer.parseInt(part);
        }
        return result;
    }

    /**
     * 获取本机主机名
     *
     * @return 主机名字符串，获取失败返回 "unknown"
     */
    public static String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }

    /**
     * 获取本机 IP 地址
     *
     * @return IP 地址字符串，获取失败返回 "127.0.0.1"
     */
    public static String getHostAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            return "127.0.0.1";
        }
    }
}
