package io.ddd4j.data.external.region;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 地区地址信息
 * <p>封装国家、省份、城市、区域和 ISP 信息</p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
@NoArgsConstructor
public class RegionAddress {

    /** 国家 */
    private String country;
    /** 省份 */
    private String province;
    /** 城市 */
    private String city;
    /** 区域 */
    private String area;
    /** 互联网服务提供商 */
    private String isp;

    /**
     * 从字符串数组构造地区地址
     *
     * @param region 包含 [国家, 区域, 省份, 城市, ISP] 的字符串数组
     */
    public RegionAddress(String[] region) {
        this(region[0], region[2], region[3], region[1], region[4]);
    }

    /**
     * 构造函数
     *
     * @param country  国家
     * @param province 省份
     * @param city     城市
     * @param area     区域
     * @param isp      互联网服务提供商
     */
    public RegionAddress(String country, String province, String city, String area, String isp) {
        this.country = country;
        this.province = province;
        this.city = city;
        this.area = area;
        this.isp = isp;
    }
}
