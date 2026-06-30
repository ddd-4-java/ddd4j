/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
package io.ddd4j.data.external.region;

import com.alibaba.fastjson2.JSONObject;
import com.github.hiwepy.ip2region.spring.boot.ext.RegionAddress;
import com.github.hiwepy.ip2region.spring.boot.ext.RegionEnum;
import com.github.hiwepy.ip2region.spring.boot.ext.XdbSearcher;
import com.github.hiwepy.ip2region.spring.boot.util.IpUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisKey;
import org.springframework.data.redis.core.RedisOperationTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * IP地址解析
 * http://whois.pconline.com.cn/
 * https://blog.csdn.net/m0_73978383/article/details/149198389
 */
@Slf4j
public class PconlineRegionTemplate {

    private static final String GET_COUNTRY_BY_IP_URL = "https://whois.pconline.com.cn/ipJson.jsp?json=true&ip=%s";
    // 810000 香港， 820000 澳门 ，710000 台湾， 999999国外
    private static final String[] SPECIAL_PROVINCE = new String[]{"810000", "820000", "710000", "999999"};
    private static final String CHINA = "中国";
    private static final String[] SPECIAL_REGION = new String[]{"香港", "澳门", "台湾"};
    private static Map<String, RegionEnum> SPECIAL_REGION_MAP;
    private static Set<String> SPECIAL_PROVINCE_SET;

    static {
        SPECIAL_REGION_MAP = new HashMap<>();
        SPECIAL_REGION_MAP.put(SPECIAL_REGION[0], RegionEnum.HK);
        SPECIAL_REGION_MAP.put(SPECIAL_REGION[1], RegionEnum.MO);
        SPECIAL_REGION_MAP.put(SPECIAL_REGION[2], RegionEnum.TW);
        SPECIAL_PROVINCE_SET = Arrays.stream(SPECIAL_PROVINCE).collect(Collectors.toSet());
    }

    private final RestClient restClient;
    private RedisOperationTemplate redisOperation;

    public PconlineRegionTemplate(RestClient restClient) {
        this.restClient = restClient;
    }

    public PconlineRegionTemplate(RestClient restClient, RedisOperationTemplate redisOperation) {
        this.restClient = restClient;
        this.redisOperation = redisOperation;
    }

    public static void main(String[] args) throws IOException {

        PconlineRegionTemplate template = new PconlineRegionTemplate(RestClient.create());

        Optional<JSONObject> mapLL2 = template.getLocationByIp("13.228.204.118"); // lng：116.86380647644208  lat：38.297615350325717
        mapLL2.ifPresent(location -> log.info("Location: {}", location.toJSONString()));
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
        if (!IpUtils.isIpv4(ip)) {
            throw new IllegalArgumentException("Invalid IPv4 address");
        }
        // 2、优先从本地缓存获取数据
        String redisKey = RedisKey.IP_LOCATION_PCONLINE_INFO.getKey(ip);
        if (Objects.nonNull(redisOperation)) {
            String redisValue = redisOperation.getString(redisKey);
            if (Objects.nonNull(redisValue)) {
                log.info(" IP : {} >> Location From Redis Cache : {} ", ip, redisValue);
                JSONObject jsonObject = JSONObject.parseObject(redisValue);
                return Optional.ofNullable(jsonObject);
            }
        }
        // 3、调用三方接口解析IP信息
        try {

            Map<String, String> queryParams = new HashMap<>();
            queryParams.put("json", "true");
            queryParams.put("ip", ip);
            //String url = String.format(GET_COUNTRY_BY_IP_URL, ip);
            ResponseEntity<String> response = restClient.get()
                    .uri(GET_COUNTRY_BY_IP_URL, queryParams)
                    .retrieve()
                    .toEntity(String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                String bodyString = response.getBody();
                log.info(" IP : {} >> Location : {} ", ip, bodyString);
                if (StringUtils.hasText(bodyString)) {
                    JSONObject jsonObject = JSONObject.parseObject(bodyString);
                    String addr = jsonObject.getString("addr");
                    if (StringUtils.hasText(addr)) {
                        if (Objects.nonNull(redisOperation)) {
                            redisOperation.set(redisKey, bodyString, Duration.ofMinutes(30));
                        }
                        return Optional.of(jsonObject);
                    }
                }
            }
            log.error("IP : {} >> Location Query Error. Response Code >> {}, Body >> {}", response.getStatusCode().value(), response.getBody());
        } catch (Exception e) {
            log.error("IP : {} >> Location Query Error：{}", e.getMessage());
        }
        return Optional.empty();
    }

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

    public RegionEnum getRegionByIp(String ip) {
        try {
            if (!IpUtils.isIpv4(ip)) {
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
                if (!StringUtils.hasText(proCode) || SPECIAL_PROVINCE_SET.contains(proCode)) {
                    return false;
                }

            }
        } catch (Exception e) {
            log.error("IP : {} >> Country/Region Parser Error：{}", ip, e.getMessage());
        }
        return true;
    }

    private static String trimWhitespace(String value) {
        return value == null ? null : value.strip();
    }

}
