package io.ddd4j.data.external.region;

/**
 * Region search fallback constants.
 */
public final class XdbSearcher {

    public static final String NOT_MATCH = "0|0|0|内网IP|内网IP";
    public static final RegionAddress NOT_MATCH_REGION_ADDRESS = new RegionAddress(NOT_MATCH.split("\\|"));

    private XdbSearcher() {
    }
}
