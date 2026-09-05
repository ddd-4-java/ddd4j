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

import com.alibaba.fastjson2.JSONObject;
import io.ddd4j.kit.lang.StrKit;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * 百度地图 IP 定位模板
 * <p>IP获取经纬度：   http://lbsyun.baidu.com/index.php?title=webapi/ip-api</p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public class BaiduRegionTemplate {

    /**
     * IP定位请求地址
     */
    private static final String GET_LOCATION_BY_IP_URL = "https://api.map.baidu.com/location/ip?ak=%s&ip=%s&coor=bd09ll";
    /**
     * 百度地图AK密钥
     */
    private final String ak;
    /**
     * HTTP 客户端
     */
    private final HttpClient httpClient;
    /**
     * 缓存服务
     */
    private RegionCache regionCache;

    /**
     * 构造函数（无缓存）
     *
     * @param ak         百度地图AK密钥
     * @param httpClient HTTP 客户端
     */
    public BaiduRegionTemplate(String ak, HttpClient httpClient) {
        this(ak, httpClient, RegionCache.none());
    }

    /**
     * 构造函数
     *
     * @param ak          百度地图AK密钥
     * @param httpClient  HTTP 客户端
     * @param regionCache 缓存服务
     */
    public BaiduRegionTemplate(String ak, HttpClient httpClient, RegionCache regionCache) {
        this.ak = ak;
        this.httpClient = httpClient;
        this.regionCache = Objects.isNull(regionCache) ? RegionCache.none() : regionCache;
    }

    public static void main(String[] args) throws IOException {

        BaiduRegionTemplate template = new BaiduRegionTemplate("CGxeqGuAGgP7n475kMPTi58y2EqjAPTh", HttpClient.newHttpClient());

        Optional<JSONObject> mapLL2 = template.getLocationByIp("183.128.136.82"); // lng：116.86380647644208  lat：38.297615350325717
        log.debug(mapLL2.get().toJSONString());
    }

    private static long secondsUntilNextDay() {
        LocalDateTime tomorrowStart = LocalDate.now().plusDays(1).atStartOfDay();
        return Duration.between(LocalDateTime.now(), tomorrowStart).getSeconds();
    }

    /**
     * 获取指定IP对应的经纬度（为空返回当前机器经纬度）
     * <p>
     * {
     * address: "CN|北京|北京|None|CHINANET|1|None",    #详细地址信息
     * content:    #结构信息
     * {
     * address: "北京市",    #简要地址信息
     * address_detail:    #结构化地址信息
     * {
     * city: "北京市",    #城市
     * city_code: 131,    #百度城市代码
     * district: "",    #区县
     * province: "北京市",    #省份
     * street: "",    #街道
     * street_number: ""    #门牌号
     * },
     * point:    #当前城市中心点
     * {
     * x: "116.39564504",    #当前城市中心点经度
     * y: "39.92998578"    #当前城市中心点纬度
     * }
     * },
     * status: 0    #结果状态返回码
     * }
     *
     * @param ip ipv4
     * @return 经纬度
     */
    public Optional<JSONObject> getLocationByIp(String ip) {
        // 1、检查ip有效性
        if (Objects.isNull(ip)) {
            throw new NullPointerException("ip can not empty");
        }
        if (!IpAddressKit.isIpv4(ip)) {
            throw new IllegalArgumentException("Invalid IPv4 address");
        }
        // 2、优先从本地缓存获取数据
        String redisKey = RegionCacheKeys.baiduLocation(ip);
        String redisValue = regionCache.getString(redisKey);
        if (Objects.nonNull(redisValue)) {
            log.info(" IP : {} >> Location From Redis Cache : {} ", ip, redisValue);
            JSONObject jsonObject = JSONObject.parseObject(redisValue);
            return Optional.ofNullable(jsonObject);
        }
        // 3、调用三方接口解析IP信息
        try {
            String url = String.format(GET_LOCATION_BY_IP_URL, this.ak, ip);
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(URI.create(url))
                            .header("Accept", "application/json")
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String bodyString = response.body();
                log.info(" IP : {} >> Location : {} ", ip, bodyString);
                if (StrKit.hasText(bodyString)) {
                    JSONObject jsonObject = JSONObject.parseObject(bodyString);
                    if (jsonObject.getInteger("status") != 0) {
                        throw new IOException(jsonObject.getString("message"));
                    }
                    regionCache.set(redisKey, bodyString, Duration.ofSeconds(secondsUntilNextDay()));
                    return Optional.of(jsonObject);
                }
            }
            log.error("IP : {} >> Location Query Error. Response Code >> {}, Body >> {}", ip, response.statusCode(), response.body());
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("IP : {} >> Country/Region Parser Error：{}", ip, e.getMessage());
        }
        return Optional.empty();
    }

    public RegionAddress getRegionAddress(String ip) {
        try {
            Optional<JSONObject> optional = this.getLocationByIp(ip);
            if (optional.isPresent()) {

                JSONObject regionData = optional.get();

                if (regionData.getIntValue("status") == 0) {

                    // CN|浙江省|杭州市|None|None|99|99
                    String address = regionData.getString("address");

                    String[] addrArr = StrKit.tokenizeToStringArray(address, "|");

                    RegionEnum region = RegionEnum.getByCode2(addrArr[0]);

                    log.info(" IP : {} >> Country/Region : {} >> {} ", ip, region.getCode2(), region.getCname());

                    return new RegionAddress(region.getCname(), addrArr[1], addrArr[2], "", addrArr[4]);

                }

            }
            return XdbSearcher.NOT_MATCH_REGION_ADDRESS;
        } catch (Exception e) {
            log.error("IP : {} >> Country/Region Parser Error：{}", ip, e.getMessage());
            return XdbSearcher.NOT_MATCH_REGION_ADDRESS;
        }
    }

    public RegionEnum getRegionByIp(String ip) {
        try {
            Optional<JSONObject> optional = this.getLocationByIp(ip);
            if (optional.isPresent()) {
                JSONObject regionData = optional.get();
                if (regionData.getIntValue("status") == 0) {

                    // CN|浙江省|杭州市|None|None|99|99
                    String address = regionData.getString("address");

                    String[] addrArr = StrKit.tokenizeToStringArray(address, "|");

                    RegionEnum region = RegionEnum.getByCode2(addrArr[0]);

                    log.info(" IP : {} >> Country/Region : {} >> {} ", ip, region.getCode2(), region.getCname());

                    return region;

                }
            }
            return RegionEnum.UK;
        } catch (Exception e) {
            log.error("IP : {} >> Country/Region Parser Error：{}", ip, e.getMessage());
            return RegionEnum.UK;
        }
    }

    public Location getLocation(String ip) {
        try {
            Optional<JSONObject> optional = this.getLocationByIp(ip);
            if (optional.isPresent()) {
                JSONObject regionData = optional.get();
                if (regionData.getIntValue("status") == 0) {

                    // CN|浙江省|杭州市|None|None|99|99
                    JSONObject content = regionData.getJSONObject("content");
                    JSONObject point = content.getJSONObject("point");
                    // 当前城市中心点经度
                    Double longitude = point.getDouble("x");
                    // 当前城市中心点纬度
                    Double latitude = point.getDouble("y");

                    log.info(" IP : {} >> longitude,latitude : {},{} ", ip, longitude, latitude);

                    return new Location(longitude, latitude);
                }
            }
        } catch (Exception e) {
            log.error("IP : {} >> Location Parser Error：{}", ip, e.getMessage());
        }
        return null;
    }

    public boolean isMainlandIp(String ip) {
        try {
            Optional<JSONObject> optional = this.getLocationByIp(ip);
            if (optional.isPresent()) {

                JSONObject regionData = optional.get();
                log.info(" IP : {} >> Region : {} ", ip, regionData.toJSONString());


            }
        } catch (Exception e) {
            log.error("IP Region Parser Error：{}", e.getMessage());
        }
        return true;
    }

    @Data
    @AllArgsConstructor
    public class Location {

        /**
         * 经度
         */
        private final Double longitude;
        /**
         * 纬度
         */
        private final Double latitude;

    }


}
