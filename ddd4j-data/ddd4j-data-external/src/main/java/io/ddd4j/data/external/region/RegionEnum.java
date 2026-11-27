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

    /** \u4e2d\u56fd\u5730\u533a\u3002 */
    public static final RegionEnum CN;
    /** \u4e2d\u56fd\u9999\u6e2f\u5730\u533a\u3002 */
    public static final RegionEnum HK;
    /** \u4e2d\u56fd\u6fb3\u95e8\u5730\u533a\u3002 */
    public static final RegionEnum MO;
    /** \u4e2d\u56fd\u53f0\u6e7e\u5730\u533a\u3002 */
    public static final RegionEnum TW;
    /** \u672a\u77e5\u5730\u533a\u5730\u533a\u3002 */
    public static final RegionEnum UK;
    /** \u6309\u5730\u533a\u679a\u4e3e\u540d\u79f0\u7d22\u5f15\u7684\u5730\u533a\u5b9a\u4e49\u3002 */
    private static final Map<String, RegionEnum> BY_NAME = new LinkedHashMap<>();
    /** \u6309 ISO \u4e24\u5b57\u6bcd\u4ee3\u7801\u7d22\u5f15\u7684\u5730\u533a\u5b9a\u4e49\u3002 */
    private static final Map<String, RegionEnum> BY_CODE2 = new LinkedHashMap<>();
    /** \u6309 ISO \u4e09\u5b57\u6bcd\u4ee3\u7801\u7d22\u5f15\u7684\u5730\u533a\u5b9a\u4e49\u3002 */
    private static final Map<String, RegionEnum> BY_CODE3 = new LinkedHashMap<>();
    /** \u6309\u4e2d\u6587\u5730\u533a\u540d\u79f0\u7d22\u5f15\u7684\u5730\u533a\u5b9a\u4e49\u3002 */
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

    /** \u7528\u4e8e\u552f\u4e00\u6807\u8bc6\u5730\u533a\u5b9a\u4e49\u7684\u679a\u4e3e\u540d\u79f0\u3002 */
    private final String name;
    /** ISO \u4e24\u5b57\u6bcd\u56fd\u5bb6\u6216\u5730\u533a\u4ee3\u7801\u3002 */
    private final String code2;
    /** ISO \u4e09\u5b57\u6bcd\u56fd\u5bb6\u6216\u5730\u533a\u4ee3\u7801\u3002 */
    private final String code3;
    /** \u56fd\u5bb6\u6216\u5730\u533a\u7684 ISO \u663e\u793a\u540d\u79f0\u3002 */
    private final String isoName;
    /** \u672c\u5730\u5316\u7684\u4e2d\u6587\u56fd\u5bb6\u6216\u5730\u533a\u540d\u79f0\u3002 */
    private final String cname;

    /**
     * \u521b\u5efa\u5305\u542b\u6807\u51c6\u5730\u533a\u4ee3\u7801\u548c\u663e\u793a\u540d\u79f0\u7684\u5730\u533a\u5b9a\u4e49\u3002
     * @param name \u5730\u533a\u5b9a\u4e49\u540d\u79f0
     * @param code2 ISO \u4e24\u5b57\u6bcd\u4ee3\u7801
     * @param code3 ISO \u4e09\u5b57\u6bcd\u4ee3\u7801
     * @param isoName ISO \u663e\u793a\u540d\u79f0
     * @param cname \u4e2d\u6587\u663e\u793a\u540d\u79f0
     */
    private RegionEnum(String name, String code2, String code3, String isoName, String cname) {
        this.name = name;
        this.code2 = code2;
        this.code3 = code3;
        this.isoName = isoName;
        this.cname = cname;
    }

    /**
     * \u521b\u5efa\u5730\u533a\u5b9a\u4e49\u5e76\u5199\u5165\u540d\u79f0\u548c\u5404\u7c7b\u5730\u533a\u4ee3\u7801\u7d22\u5f15\u3002
     * @param code2 ISO \u4e24\u5b57\u6bcd\u4ee3\u7801
     * @param code3 ISO \u4e09\u5b57\u6bcd\u4ee3\u7801
     * @param isoName ISO \u663e\u793a\u540d\u79f0
     * @param cname \u4e2d\u6587\u663e\u793a\u540d\u79f0
     * @return \u65b0\u767b\u8bb0\u7684\u5730\u533a\u5b9a\u4e49
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
     * \u8bfb\u53d6\u4e09\u5b57\u6bcd\u4ee3\u7801\uff0c\u65e0\u6cd5\u89e3\u6790\u65f6\u4f7f\u7528 Locale \u7684\u539f\u533a\u57df\u4ee3\u7801\u3002
     * @param locale \u63cf\u8ff0\u5730\u533a\u7684 Locale
     * @return \u8be5\u5730\u533a\u7684\u4e09\u5b57\u6bcd\u4ee3\u7801\u6216\u539f\u533a\u57df\u4ee3\u7801
     */
    private static String safeIso3(Locale locale) {
        try {
            return locale.getISO3Country();
        } catch (Exception e) {
            return locale.getCountry();
        }
    }

    /**
     * \u8fd4\u56de\u5df2\u6ce8\u518c\u7684\u6240\u6709\u5730\u533a\u3002
     * @return \u5730\u533a\u6570\u7ec4
     */
    public static RegionEnum[] values() {
        Collection<RegionEnum> values = BY_NAME.values();
        return values.toArray(new RegionEnum[0]);
    }

    /**
     * \u6309\u540d\u79f0\u67e5\u627e\u5730\u533a\u3002
     * @param name \u5730\u533a\u540d\u79f0
     * @return \u5339\u914d\u7684\u5730\u533a
     */
    public static RegionEnum valueOf(String name) {
        return get(BY_NAME, name);
    }

    /**
     * \u6309\u4e8c\u4f4d\u4ee3\u7801\u67e5\u627e\u5730\u533a\u3002
     * @param code2 \u4e8c\u4f4d\u5730\u533a\u4ee3\u7801
     * @return \u5339\u914d\u7684\u5730\u533a
     */
    public static RegionEnum getByCode2(String code2) {
        return get(BY_CODE2, code2);
    }

    /**
     * \u6309\u4e09\u4f4d\u4ee3\u7801\u67e5\u627e\u5730\u533a\u3002
     * @param code3 \u4e09\u4f4d\u5730\u533a\u4ee3\u7801
     * @return \u5339\u914d\u7684\u5730\u533a
     */
    public static RegionEnum getByCode3(String code3) {
        return get(BY_CODE3, code3);
    }

    /**
     * \u6309\u4e2d\u6587\u540d\u79f0\u67e5\u627e\u5730\u533a\u3002
     * @param cname \u5730\u533a\u4e2d\u6587\u540d\u79f0
     * @return \u5339\u914d\u7684\u5730\u533a
     */
    public static RegionEnum getByCnName(String cname) {
        return get(BY_CNAME, cname);
    }

    /**
     * \u4ece\u5730\u5740\u4fe1\u606f\u67e5\u627e\u5730\u533a\u3002
     * @param address \u5730\u533a\u5730\u5740
     * @return \u5339\u914d\u7684\u5730\u533a
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
     * \u5224\u65ad\u5730\u533a\u662f\u5426\u6709\u6548\u3002
     * @param region \u5f85\u5224\u65ad\u7684\u5730\u533a
     * @return \u5730\u533a\u6709\u6548\u65f6\u4e3a true
     */
    public static boolean isValidRegion(RegionEnum region) {
        return Objects.nonNull(region) && region.isValidRegion();
    }

    /**
     * \u53bb\u9664\u9996\u5c3e\u7a7a\u767d\u5e76\u4ee5\u5927\u5199\u952e\u67e5\u627e\u5730\u533a\uff0c\u7a7a\u503c\u6216\u672a\u547d\u4e2d\u65f6\u4f7f\u7528\u82f1\u56fd\u5b9a\u4e49\u3002
     * @param map \u5f53\u524d\u8c03\u7528\u4f7f\u7528\u7684\u5730\u533a\u7d22\u5f15
     * @param key \u5f85\u67e5\u8be2\u7684\u5730\u533a\u540d\u79f0\u6216\u4ee3\u7801
     * @return \u5339\u914d\u5230\u7684\u5730\u533a\u5b9a\u4e49\u6216\u9ed8\u8ba4\u82f1\u56fd\u5b9a\u4e49
     */
    private static RegionEnum get(Map<String, RegionEnum> map, String key) {
        if (!StrKit.hasText(key)) {
            return UK;
        }
        return map.getOrDefault(key.trim().toUpperCase(Locale.ROOT), UK);
    }

    /**
     * \u5224\u65ad\u5f53\u524d\u5730\u533a\u662f\u5426\u6709\u6548\u3002
     * @return \u5f53\u524d\u5730\u533a\u4e0d\u662f\u672a\u77e5\u5730\u533a\u65f6\u4e3a true
     */
    public boolean isValidRegion() {
        return this != UK;
    }

    /**
     * \u5224\u65ad\u5f53\u524d\u5730\u533a\u662f\u5426\u5c5e\u4e8e\u4e2d\u56fd\u3002
     * @return \u5c5e\u4e8e\u4e2d\u56fd\u65f6\u4e3a true
     */
    public boolean isChinaRegion() {
        return this == CN || this == HK || this == MO || this == TW;
    }

    /**
     * \u5224\u65ad\u5f53\u524d\u5730\u533a\u662f\u5426\u5c5e\u4e8e\u4e2d\u56fd\u5927\u9646\u3002
     * @return \u5c5e\u4e8e\u4e2d\u56fd\u5927\u9646\u65f6\u4e3a true
     */
    public boolean isChinaMainland() {
        return this == CN;
    }

    /**
     * \u8fd4\u56de\u5730\u533a\u540d\u79f0\u3002
     * @return \u5730\u533a\u540d\u79f0
     */
    public String name() {
        return name;
    }

    /**
     * \u8fd4\u56de\u4e8c\u4f4d\u5730\u533a\u4ee3\u7801\u3002
     * @return \u4e8c\u4f4d\u5730\u533a\u4ee3\u7801
     */
    public String getCode2() {
        return code2;
    }

    /**
     * \u8fd4\u56de\u4e09\u4f4d\u5730\u533a\u4ee3\u7801\u3002
     * @return \u4e09\u4f4d\u5730\u533a\u4ee3\u7801
     */
    public String getCode3() {
        return code3;
    }

    /**
     * \u8fd4\u56de ISO \u82f1\u6587\u540d\u79f0\u3002
     * @return ISO \u82f1\u6587\u540d\u79f0
     */
    public String getIsoName() {
        return isoName;
    }

    /**
     * \u8fd4\u56de\u5730\u533a\u4e2d\u6587\u540d\u79f0\u3002
     * @return \u5730\u533a\u4e2d\u6587\u540d\u79f0
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
