package io.ddd4j.data.external.region;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Basic country/region address information.
 */
@Data
@NoArgsConstructor
public class RegionAddress {

    private String country;
    private String province;
    private String city;
    private String area;
    private String isp;

    public RegionAddress(String[] region) {
        this(region[0], region[2], region[3], region[1], region[4]);
    }

    public RegionAddress(String country, String province, String city, String area, String isp) {
        this.country = country;
        this.province = province;
        this.city = city;
        this.area = area;
        this.isp = isp;
    }
}
