/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
package io.ddd4j.data.external.region;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Objects;


/**
 * 嵌套的地区解析模板
 */
@Slf4j
public class NestedRegionTemplate {

    private RegionCache regionCache;
    private IpRegionTemplate ipRegionTemplate;
    private PconlineRegionTemplate pconlineRegionTemplate;

    public NestedRegionTemplate(RegionCache regionCache, IpRegionTemplate ipRegionTemplate,
                                PconlineRegionTemplate pconlineRegionTemplate) {
        this.regionCache = Objects.isNull(regionCache) ? RegionCache.none() : regionCache;
        this.ipRegionTemplate = Objects.isNull(ipRegionTemplate) ? IpRegionTemplate.none() : ipRegionTemplate;
        this.pconlineRegionTemplate = pconlineRegionTemplate;
    }

    private static String trimWhitespace(String value) {
        return Objects.isNull(value) ? null : value.strip();
    }

    public RegionEnum getRegion(String regionCode, String ipAddress) {
        RegionEnum regionEnum = this.getRegionByCode(regionCode);
        if (!regionEnum.isValidRegion()) {
            regionEnum = this.getRegionByIp(ipAddress);
        }
        log.info("Get Final Region : {} By regionCode : {}, ipAddress : {}, is Valid : {} ", regionEnum.name(), regionCode, ipAddress, regionEnum.isValidRegion());
        return regionEnum;
    }

    public RegionEnum getRegionByCode(String regionCode) {
        RegionEnum regionEnum = RegionEnum.getByCode2(regionCode);
        log.debug("Get Region : {} By regionCode : {}, is Valid : {} ", regionEnum.name(), regionCode, regionEnum.isValidRegion());
        if (!regionEnum.isValidRegion()) {
            regionEnum = RegionEnum.getByCode3(regionCode);
            log.debug("Get Region : {} By regionCode : {}, is Valid : {} ", regionEnum.name(), regionCode, regionEnum.isValidRegion());
        }
        return regionEnum;
    }

    public RegionEnum getRegionByIp(String ipAddress) {
        try {
            // 1、去除参数两头空白
            ipAddress = trimWhitespace(ipAddress);
            if (IpAddressKit.internalIp(ipAddress)) {
                return RegionEnum.UK;
            }
            // 2、优先从本地缓存获取数据
            String redisKey = RegionCacheKeys.ipRegion(ipAddress);
            String regionCode = regionCache.getString(redisKey);
            if (StringUtils.hasText(regionCode)) {
                return RegionEnum.getByCode2(regionCode);
            }
            // 3、尝试使用ip2region的ip库进行IP解析
            RegionEnum regionEnum = getIp2RegionTemplate().getRegionByIp(ipAddress);
            log.debug("Get Region : {} By ipAddress: {} From Ip2Region, is Valid : {} ", regionEnum.name(), ipAddress, regionEnum.isValidRegion());
            if (!regionEnum.isValidRegion()) {
                try {
                    // 3、尝试使用太平洋网络的ip库进行IP解析
                    regionEnum = getPconlineRegionTemplate().getRegionByIp(ipAddress);
                    log.debug("Get Region {} By ipAddress: {} From Pconline, is Valid : {} ", regionEnum.name(), ipAddress, regionEnum.isValidRegion());
                } catch (Exception e) {
                    log.error("太平洋网络IP地址查询失败！{}", e.getMessage());
                }
            }
            if (regionEnum.isValidRegion()) {
                regionCache.set(redisKey, regionEnum.getCode2(), Duration.ofMinutes(30));
                return regionEnum;
            }
            regionCache.set(redisKey, RegionEnum.UK.getCode2(), Duration.ofMinutes(30));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return RegionEnum.UK;
    }

    public String getLocationByIp(String ipAddress) {
        try {
            // 1、去除参数两头空白
            ipAddress = trimWhitespace(ipAddress);
            if (IpAddressKit.internalIp(ipAddress)) {
                return XdbSearcher.NOT_MATCH;
            }
            // 2、优先从本地缓存获取数据
            String redisKey = RegionCacheKeys.ipLocation(ipAddress);
            String regionAddress = regionCache.getString(redisKey);
            if (StringUtils.hasText(regionAddress)) {
                return regionAddress;
            }
            // 3、尝试使用ip2region的ip库进行IP解析
            regionAddress = getIp2RegionTemplate().getRegion(ipAddress);
            log.debug("Get Location : {} By ipAddress: {} From Ip2Region ", regionAddress, ipAddress);
            regionCache.set(redisKey, regionAddress, Duration.ofMinutes(30));
            return regionAddress;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return XdbSearcher.NOT_MATCH;
    }

    public RegionAddress getRegionAddress(String ipAddress) {
        try {
            // 1、去除参数两头空白
            ipAddress = trimWhitespace(ipAddress);
            if (IpAddressKit.internalIp(ipAddress)) {
                return XdbSearcher.NOT_MATCH_REGION_ADDRESS;
            }
            // 2、尝试使用ip2region的ip库进行IP解析
            RegionAddress regionAddress = getIp2RegionTemplate().getRegionAddress(ipAddress);
            if (XdbSearcher.NOT_MATCH_REGION_ADDRESS.equals(regionAddress)) {
                try {
                    // 3、尝试使用太平洋网络的ip库进行IP解析
                    regionAddress = getPconlineRegionTemplate().getRegionAddress(ipAddress);
                } catch (Exception e) {
                    log.error("太平洋网络IP地址查询失败！{}", e.getMessage());
                }
            }
            return regionAddress;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return XdbSearcher.NOT_MATCH_REGION_ADDRESS;
    }

    public boolean isMainlandIp(String regionCode, String ipAddress) {
        try {
            RegionEnum regionEnum = this.getRegionByCode(regionCode);
            if (!regionEnum.isValidRegion()) {
                return this.isMainlandIp(ipAddress);
            }
            return regionEnum.isChinaMainland();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return Boolean.FALSE;
    }

    public boolean isMainlandIp(String ipAddress) {
        // 1、去除参数两头空白
        ipAddress = trimWhitespace(ipAddress);
        if (IpAddressKit.internalIp(ipAddress)) {
            return true;
        }
        // 2、尝试使用ip2region的ip库进行IP解析
        RegionEnum regionEnum = getIp2RegionTemplate().getRegionByIp(ipAddress);
        if (!regionEnum.isValidRegion()) {
            try {
                // 3、尝试使用太平洋网络的ip库进行IP解析
                regionEnum = getPconlineRegionTemplate().getRegionByIp(ipAddress);
            } catch (Exception e) {
                log.error("太平洋网络IP地址查询失败！{}", e.getMessage());
            }
        }
        return regionEnum.isChinaMainland();
    }

    public IpRegionTemplate getIp2RegionTemplate() {
        return ipRegionTemplate;
    }

    public PconlineRegionTemplate getPconlineRegionTemplate() {
        return pconlineRegionTemplate;
    }

}
