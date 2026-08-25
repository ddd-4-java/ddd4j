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

/**
 * IP region lookup abstraction implemented by framework adapters.
 */
public interface IpRegionTemplate {

    IpRegionTemplate NONE = new IpRegionTemplate() {

        @Override
        public String getRegion(String ip) {
            return XdbSearcher.NOT_MATCH;
        }

        @Override
        public RegionAddress getRegionAddress(String ip) {
            return XdbSearcher.NOT_MATCH_REGION_ADDRESS;
        }

        @Override
        public RegionEnum getRegionByIp(String ip) {
            return RegionEnum.UK;
        }
    };

    /**
     * 获取空实现
     *
     * @return 空实现的 IpRegionTemplate 实例
     */
    static IpRegionTemplate none() {
        return NONE;
    }

    /**
     * 根据 IP 获取地区信息字符串
     *
     * @param ip IPv4 地址
     * @return 地区信息字符串
     */
    String getRegion(String ip);

    /**
     * 根据 IP 获取地区地址对象
     *
     * @param ip IPv4 地址
     * @return 地区地址对象
     */
    RegionAddress getRegionAddress(String ip);

    /**
     * 根据 IP 获取地区枚举
     *
     * @param ip IPv4 地址
     * @return 地区枚举
     */
    RegionEnum getRegionByIp(String ip);
}
