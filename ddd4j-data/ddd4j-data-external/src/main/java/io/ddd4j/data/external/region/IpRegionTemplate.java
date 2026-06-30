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

    static IpRegionTemplate none() {
        return NONE;
    }

    String getRegion(String ip);

    RegionAddress getRegionAddress(String ip);

    RegionEnum getRegionByIp(String ip);
}
