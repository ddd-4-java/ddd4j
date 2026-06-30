package io.ddd4j.data.external.region;

import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Country/region descriptor backed by ISO country codes.
 *
 * <p>This class intentionally avoids the former Spring Boot starter enum while
 * keeping the methods used by existing region templates.
 */
public final class RegionEnum implements Comparable<RegionEnum> {

    private static final Map<String, RegionEnum> BY_NAME = new LinkedHashMap<>();
    private static final Map<String, RegionEnum> BY_CODE2 = new LinkedHashMap<>();
    private static final Map<String, RegionEnum> BY_CODE3 = new LinkedHashMap<>();
    private static final Map<String, RegionEnum> BY_CNAME = new LinkedHashMap<>();

    public static final RegionEnum CN;
    public static final RegionEnum HK;
    public static final RegionEnum MO;
    public static final RegionEnum TW;
    public static final RegionEnum UK;

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

    private final String name;
    private final String code2;
    private final String code3;
    private final String isoName;
    private final String cname;

    private RegionEnum(String name, String code2, String code3, String isoName, String cname) {
        this.name = name;
        this.code2 = code2;
        this.code3 = code3;
        this.isoName = isoName;
        this.cname = cname;
    }

    private static RegionEnum register(String code2, String code3, String isoName, String cname) {
        RegionEnum region = new RegionEnum(code2, code2, code3, isoName, cname);
        BY_NAME.put(code2, region);
        BY_CODE2.put(code2, region);
        BY_CODE3.put(code3, region);
        BY_CNAME.put(cname, region);
        return region;
    }

    private static String safeIso3(Locale locale) {
        try {
            return locale.getISO3Country();
        } catch (Exception e) {
            return locale.getCountry();
        }
    }

    public static RegionEnum[] values() {
        Collection<RegionEnum> values = BY_NAME.values();
        return values.toArray(new RegionEnum[0]);
    }

    public static RegionEnum valueOf(String name) {
        return get(BY_NAME, name);
    }

    public static RegionEnum getByCode2(String code2) {
        return get(BY_CODE2, code2);
    }

    public static RegionEnum getByCode3(String code3) {
        return get(BY_CODE3, code3);
    }

    public static RegionEnum getByCnName(String cname) {
        return get(BY_CNAME, cname);
    }

    public static RegionEnum getByRegionAddress(RegionAddress address) {
        if (Objects.isNull(address)) {
            return UK;
        }
        RegionEnum region = getByCnName(address.getCountry());
        if (region.isValidRegion()) {
            return region;
        }
        for (RegionEnum value : values()) {
            if (StringUtils.hasText(address.getCountry()) && address.getCountry().contains(value.getCname())) {
                return value;
            }
        }
        return UK;
    }

    public static boolean isValidRegion(RegionEnum region) {
        return Objects.nonNull(region) && region.isValidRegion();
    }

    private static RegionEnum get(Map<String, RegionEnum> map, String key) {
        if (!StringUtils.hasText(key)) {
            return UK;
        }
        return map.getOrDefault(key.trim().toUpperCase(Locale.ROOT), UK);
    }

    public boolean isValidRegion() {
        return this != UK;
    }

    public boolean isChinaRegion() {
        return this == CN || this == HK || this == MO || this == TW;
    }

    public boolean isChinaMainland() {
        return this == CN;
    }

    public String name() {
        return name;
    }

    public String getCode2() {
        return code2;
    }

    public String getCode3() {
        return code3;
    }

    public String getIsoName() {
        return isoName;
    }

    public String getCname() {
        return cname;
    }

    @Override
    public int compareTo(RegionEnum other) {
        return name.compareTo(other.name);
    }
}
