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

import io.ddd4j.kit.lang.StrKit;

import java.util.*;

/**
 * Country/region descriptor backed by ISO country codes.
 *
 * <p>This class intentionally avoids the former Spring Boot starter enum while
 * keeping the methods used by existing region templates.
 */
public final class RegionEnum implements Comparable<RegionEnum> {

    /**
     * 中国地区。
     */
    public static final RegionEnum CN;
    /**
     * 中国香港地区。
     */
    public static final RegionEnum HK;
    /**
     * 中国澳门地区。
     */
    public static final RegionEnum MO;
    /**
     * 中国台湾地区。
     */
    public static final RegionEnum TW;
    /**
     * 未知地区地区。
     */
    public static final RegionEnum UK;
    /**
     * 按地区枚举名称索引的地区定义。
     */
    private static final Map<String, RegionEnum> BY_NAME = new LinkedHashMap<>();
    /**
     * 按 ISO 两字母代码索引的地区定义。
     */
    private static final Map<String, RegionEnum> BY_CODE2 = new LinkedHashMap<>();
    /**
     * 按 ISO 三字母代码索引的地区定义。
     */
    private static final Map<String, RegionEnum> BY_CODE3 = new LinkedHashMap<>();
    /**
     * 按中文地区名称索引的地区定义。
     */
    private static final Map<String, RegionEnum> BY_CNAME = new LinkedHashMap<>();

    static {
        for (String code2 : Locale.getISOCountries()) {
            Locale locale = new Locale.Builder().setRegion(code2).build();
            register(code2, safeIso3(locale), locale.getDisplayCountry(Locale.ENGLISH),
                    locale.getDisplayCountry(Locale.SIMPLIFIED_CHINESE));
        }
        CN = register("CN", "CHN", "China", "中国");
        HK = register("HK", "HKG", "Hong Kong", "中国香港");
        MO = register("MO", "MAC", "Macao", "中国澳门");
        TW = register("TW", "TWN", "Taiwan", "中国台湾");
        UK = register("UK", "UKN", "Unknown", "未知");
    }

    /**
     * 用于唯一标识地区定义的枚举名称。
     */
    private final String name;
    /**
     * ISO 两字母国家或地区代码。
     */
    private final String code2;
    /**
     * ISO 三字母国家或地区代码。
     */
    private final String code3;
    /**
     * 国家或地区的 ISO 显示名称。
     */
    private final String isoName;
    /**
     * 本地化的中文国家或地区名称。
     */
    private final String cname;

    /**
     * 创建包含标准地区代码和显示名称的地区定义。
     *
     * @param name    地区定义名称
     * @param code2   ISO 两字母代码
     * @param code3   ISO 三字母代码
     * @param isoName ISO 显示名称
     * @param cname   中文显示名称
     */
    private RegionEnum(String name, String code2, String code3, String isoName, String cname) {
        this.name = name;
        this.code2 = code2;
        this.code3 = code3;
        this.isoName = isoName;
        this.cname = cname;
    }

    /**
     * 创建地区定义并写入名称和各类地区代码索引。
     *
     * @param code2   ISO 两字母代码
     * @param code3   ISO 三字母代码
     * @param isoName ISO 显示名称
     * @param cname   中文显示名称
     * @return 新登记的地区定义
     */
    private static RegionEnum register(String code2, String code3, String isoName, String cname) {
        RegionEnum region = new RegionEnum(code2, code2, code3, isoName, cname);
        BY_NAME.put(code2, region);
        BY_CODE2.put(code2, region);
        BY_CODE3.put(code3, region);
        BY_CNAME.put(cname, region);
        return region;
    }

    /**
     * 读取三字母代码，无法解析时使用 Locale 的原区域代码。
     *
     * @param locale 描述地区的 Locale
     * @return 该地区的三字母代码或原区域代码
     */
    private static String safeIso3(Locale locale) {
        try {
            return locale.getISO3Country();
        } catch (Exception e) {
            return locale.getCountry();
        }
    }

    /**
     * 返回已注册的所有地区。
     *
     * @return 地区数组
     */
    public static RegionEnum[] values() {
        Collection<RegionEnum> values = BY_NAME.values();
        return values.toArray(new RegionEnum[0]);
    }

    /**
     * 按名称查找地区。
     *
     * @param name 地区名称
     * @return 匹配的地区
     */
    public static RegionEnum valueOf(String name) {
        return get(BY_NAME, name);
    }

    /**
     * 按二位代码查找地区。
     *
     * @param code2 二位地区代码
     * @return 匹配的地区
     */
    public static RegionEnum getByCode2(String code2) {
        return get(BY_CODE2, code2);
    }

    /**
     * 按三位代码查找地区。
     *
     * @param code3 三位地区代码
     * @return 匹配的地区
     */
    public static RegionEnum getByCode3(String code3) {
        return get(BY_CODE3, code3);
    }

    /**
     * 按中文名称查找地区。
     *
     * @param cname 地区中文名称
     * @return 匹配的地区
     */
    public static RegionEnum getByCnName(String cname) {
        return get(BY_CNAME, cname);
    }

    /**
     * 从地址信息查找地区。
     *
     * @param address 地区地址
     * @return 匹配的地区
     */
    public static RegionEnum getByRegionAddress(RegionAddress address) {
        if (Objects.isNull(address)) {
            return UK;
        }
        RegionEnum region = getByCnName(address.getCountry());
        if (region.isValidRegion()) {
            return region;
        }
        for (RegionEnum value : values()) {
            if (StrKit.hasText(address.getCountry()) && address.getCountry().contains(value.getCname())) {
                return value;
            }
        }
        return UK;
    }

    /**
     * 判断地区是否有效。
     *
     * @param region 待判断的地区
     * @return 地区有效时为 true
     */
    public static boolean isValidRegion(RegionEnum region) {
        return Objects.nonNull(region) && region.isValidRegion();
    }

    /**
     * 去除首尾空白并以大写键查找地区，空值或未命中时使用英国定义。
     *
     * @param map 当前调用使用的地区索引
     * @param key 待查询的地区名称或代码
     * @return 匹配到的地区定义或默认英国定义
     */
    private static RegionEnum get(Map<String, RegionEnum> map, String key) {
        if (!StrKit.hasText(key)) {
            return UK;
        }
        return map.getOrDefault(key.trim().toUpperCase(Locale.ROOT), UK);
    }

    /**
     * 判断当前地区是否有效。
     *
     * @return 当前地区不是未知地区时为 true
     */
    public boolean isValidRegion() {
        return this != UK;
    }

    /**
     * 判断当前地区是否属于中国。
     *
     * @return 属于中国时为 true
     */
    public boolean isChinaRegion() {
        return this == CN || this == HK || this == MO || this == TW;
    }

    /**
     * 判断当前地区是否属于中国大陆。
     *
     * @return 属于中国大陆时为 true
     */
    public boolean isChinaMainland() {
        return this == CN;
    }

    /**
     * 返回地区名称。
     *
     * @return 地区名称
     */
    public String name() {
        return name;
    }

    /**
     * 返回二位地区代码。
     *
     * @return 二位地区代码
     */
    public String getCode2() {
        return code2;
    }

    /**
     * 返回三位地区代码。
     *
     * @return 三位地区代码
     */
    public String getCode3() {
        return code3;
    }

    /**
     * 返回 ISO 英文名称。
     *
     * @return ISO 英文名称
     */
    public String getIsoName() {
        return isoName;
    }

    /**
     * 返回地区中文名称。
     *
     * @return 地区中文名称
     */
    public String getCname() {
        return cname;
    }

    /**
     * 比较当前对象与输入对象的顺序。
     *
     * <p>此实现调用 <code>name.compareTo(other.name)</code>。</p>
     *
     * @param other 与当前对象比较的对象
     * @return 实际返回<code>name.compareTo(other.name)</code>
     */
    @Override
    public int compareTo(RegionEnum other) {
        return name.compareTo(other.name);
    }
}
