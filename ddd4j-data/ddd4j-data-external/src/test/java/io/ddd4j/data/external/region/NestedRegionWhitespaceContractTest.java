package io.ddd4j.data.external.region;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 三线地区解析输入空白处理契约。 */
class NestedRegionWhitespaceContractTest {

    @Test
    void unicodeWhitespaceIsRemovedBeforeIpLookup() {
        AtomicReference<String> lookupIp = new AtomicReference<>();
        IpRegionTemplate lookup = new IpRegionTemplate() {
            @Override
            public String getRegion(String ip) {
                lookupIp.set(ip);
                return "resolved";
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
        NestedRegionTemplate template = new NestedRegionTemplate(RegionCache.none(), lookup, null);

        assertEquals("resolved", template.getLocationByIp("\u20038.8.8.8\u2003"));
        assertEquals("8.8.8.8", lookupIp.get());
    }
}
