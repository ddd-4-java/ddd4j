/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
package io.ddd4j.data.external.region;

import com.alibaba.fastjson2.JSONObject;
import io.ddd4j.kit.lang.StrKit;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;

/**
 * 太平洋网络 IP 地址解析模板
 * <p>通过太平洋网络的 IP 查询接口获取 IP 地址对应的地理位置信息</p>
 * <p>参考文档：<a href="http://whois.pconline.com.cn/">http://whois.pconline.com.cn/</a></p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public class PconlineRegionTemplate {

    /**
     * 太平洋网络 IP 查询地址
     */
    private static final String GET_COUNTRY_BY_IP_URL = "https://whois.pconline.com.cn/ipJson.jsp?json=true&ip=%s";
    /**
     * 特殊行政区划代码：810000 香港，820000 澳门 ，710000 台湾，999999 国外
     */
    private static final String[] SPECIAL_PROVINCE = new String[]{"810000", "820000", "710000", "999999"};
    /**
     * 中国
     */
    private static final String CHINA = "中国";
    /**
     * 特殊地区名称
     */
    private static final String[] SPECIAL_REGION = new String[]{"香港", "澳门", "台湾"};
    /**
     * 特殊地区枚举映射
     */
    private static Map<String, RegionEnum> SPECIAL_REGION_MAP;
    /**
     * 特殊行政区划代码集合
     */
    private static Set<String> SPECIAL_PROVINCE_SET;

    static {
        SPECIAL_REGION_MAP = new HashMap<>();
        SPECIAL_REGION_MAP.put(SPECIAL_REGION[0], RegionEnum.HK);
        SPECIAL_REGION_MAP.put(SPECIAL_REGION[1], RegionEnum.MO);
        SPECIAL_REGION_MAP.put(SPECIAL_REGION[2], RegionEnum.TW);
        SPECIAL_PROVINCE_SET = Arrays.stream(SPECIAL_PROVINCE).collect(Collectors.toSet());
    }

    /**
     * HTTP 客户端
     */
    /**
     * 缓存服务
     */
    private RegionCache regionCache;

    /**
     * 构造函数（无缓存）
     *
     * @param httpClient HTTP 客户端
     */
    public PconlineRegionTemplate() {
        this(RegionCache.none());
    }

    /**
     * 构造函数
     *
     * @param httpClient  HTTP 客户端
     * @param regionCache 缓存服务
     */
    public PconlineRegionTemplate(RegionCache regionCache) {
        this.regionCache = Objects.isNull(regionCache) ? RegionCache.none() : regionCache;
    }

    public static void main(String[] args) throws IOException {

        PconlineRegionTemplate template = new PconlineRegionTemplate();

        Optional<JSONObject> mapLL2 = template.getLocationByIp("13.228.204.118"); // lng：116.86380647644208  lat：38.297615350325717
        mapLL2.ifPresent(location -> log.info("Location: {}", location.toJSONString()));
    }

    private static String trimWhitespace(String value) {
        return Objects.isNull(value) ? null : value.trim();
    }

    /**
     * IP地址解析：http://whois.pconline.com.cn/ipJson.jsp?json=true&ip=183.128.136.82
     *
     * @param ip
     * @return {"ip":"110.137.48.237","pro":"","proCode":"999999","city":"","cityCode":"0","region":"","regionCode":"0","addr":" 印度尼西亚","regionNames":"","err":"noprovince"}
     * @throws ExecutionException
     */
    public Optional<JSONObject> getLocationByIp(String ip) {
        // 1、检查ip有效性
        if (Objects.isNull(ip)) {
            throw new NullPointerException("IP can not empty");
        }
        if (!IpAddressKit.isIpv4(ip)) {
            throw new IllegalArgumentException("Invalid IPv4 address");
        }
        // 2、优先从本地缓存获取数据
        String redisKey = RegionCacheKeys.pconlineLocation(ip);
        String redisValue = regionCache.getString(redisKey);
        if (Objects.nonNull(redisValue)) {
            log.info(" IP : {} >> Location From Redis Cache : {} ", ip, redisValue);
            JSONObject jsonObject = JSONObject.parseObject(redisValue);
            return Optional.ofNullable(jsonObject);
        }
        // 3、调用三方接口解析IP信息
        try {

            String url = String.format(GET_COUNTRY_BY_IP_URL, ip);
            HttpResponse response = HttpRequest.get(url).header("Accept", "application/json").execute();
            if (response.getStatus() >= 200 && response.getStatus() < 300) {
                String bodyString = response.body();
                log.info(" IP : {} >> Location : {} ", ip, bodyString);
                if (StrKit.hasText(bodyString)) {
                    JSONObject jsonObject = JSONObject.parseObject(bodyString);
                    String addr = jsonObject.getString("addr");
                    if (StrKit.hasText(addr)) {
                        regionCache.set(redisKey, bodyString, Duration.ofMinutes(30));
                        return Optional.of(jsonObject);
                    }
                }
            }
            log.error("IP : {} >> Location Query Error. Response Code >> {}, Body >> {}", ip, response.getStatus(), response.body());
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("IP : {} >> Location Query Error：{}", ip, e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * 根据 IP 地址获取地区地址对象
     *
     * @param ip IPv4 地址
     * @return 地区地址对象
     */
    public RegionAddress getRegionAddress(String ip) {
        try {
            Optional<JSONObject> optional = this.getLocationByIp(ip);
            if (optional.isPresent()) {

                JSONObject regionData = optional.get();

                log.debug(" IP : {} >> Region : {} ", ip, regionData.toJSONString());

                String province = regionData.getString("pro");
                String city = regionData.getString("city");
                String addr = trimWhitespace(regionData.getString("addr"));
                String country = addr;

                if (Stream.of(SPECIAL_REGION).anyMatch(region -> addr.contains(region))) {
                    country = CHINA;
                } else {
                    Optional<ProvinceEnum> proEnum = Stream.of(ProvinceEnum.values()).filter(pro -> addr.contains(pro.getCname())).findFirst();
                    if (proEnum.isPresent()) {
                        country = CHINA;
                        province = proEnum.get().getCname();
                    }
                }

                Optional<RegionEnum> regionEnum = Stream.of(RegionEnum.values()).filter(region -> addr.contains(region.getCname())).findFirst();
                if (regionEnum.isPresent()) {
                    country = regionEnum.get().getCname();
                }

                log.debug(" IP : {} >> Country/Region : {} ", ip, country);

                return new RegionAddress(country, province, city, "", "");
            }
            return XdbSearcher.NOT_MATCH_REGION_ADDRESS;
        } catch (Exception e) {
            log.error("IP : {} >> Country/Region Parser Error：{}", ip, e.getMessage());
            return XdbSearcher.NOT_MATCH_REGION_ADDRESS;
        }
    }

    /**
     * 根据 IP 地址获取地区枚举
     *
     * @param ip IPv4 地址
     * @return 地区枚举
     */
    public RegionEnum getRegionByIp(String ip) {
        try {
            if (!IpAddressKit.isIpv4(ip)) {
                return RegionEnum.UK;
            }
            Optional<JSONObject> optional = this.getLocationByIp(ip);
            if (optional.isPresent()) {

                JSONObject regionData = optional.get();
                log.debug(" IP : {} >> Region : {} ", ip, regionData.toJSONString());

                String addr = trimWhitespace(regionData.getString("addr"));
                String country = addr;

                Optional<String> regionOptional = Stream.of(SPECIAL_REGION).filter(region -> addr.contains(region)).findFirst();
                if (regionOptional.isPresent()) {
                    log.debug(" IP : {} >> Country/Region : {} ", ip, country);
                    return SPECIAL_REGION_MAP.get(regionOptional.get());
                }

                Optional<ProvinceEnum> proEnum = Stream.of(ProvinceEnum.values()).filter(pro -> addr.contains(pro.getCname())).findFirst();
                if (proEnum.isPresent()) {
                    log.debug(" IP : {} >> Country/Region : {} ", ip, proEnum.get().getCname());
                    return RegionEnum.CN;
                }

                Optional<RegionEnum> regionEnum = Stream.of(RegionEnum.values()).filter(region -> addr.contains(region.getCname())).findFirst();
                if (regionEnum.isPresent()) {
                    log.debug(" IP : {} >> Country/Region : {} ", ip, regionEnum.get().getCname());
                    return regionEnum.get();
                }

                return RegionEnum.UK;
            }
            return RegionEnum.UK;
        } catch (Exception e) {
            log.error("IP : {} >> Country/Region Parser Error：{}", ip, e.getMessage());
            return RegionEnum.UK;
        }
    }

    public boolean isMainlandIp(String ip) {
        try {
            Optional<JSONObject> optional = this.getLocationByIp(ip);
            if (optional.isPresent()) {

                JSONObject regionData = optional.get();
                log.debug(" IP : {} >> Region : {} ", ip, regionData.toJSONString());

                String proCode = regionData.getString("proCode");
                if (!StrKit.hasText(proCode) || SPECIAL_PROVINCE_SET.contains(proCode)) {
                    return false;
                }

            }
        } catch (Exception e) {
            log.error("IP : {} >> Country/Region Parser Error：{}", ip, e.getMessage());
        }
        return true;
    }

}
