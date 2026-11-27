package io.ddd4j.data.external.region;

/**
 * Region search fallback constants.
 */
public final class XdbSearcher {

    /** \u672a\u5339\u914d\u5730\u533a\u65f6\u7684\u539f\u59cb\u6570\u636e\u3002 */
    public static final String NOT_MATCH = "0|0|0|内网IP|内网IP";
    /** \u672a\u5339\u914d\u5730\u533a\u65f6\u7684\u7ed3\u6784\u5316\u5730\u5740\u3002 */
    public static final RegionAddress NOT_MATCH_REGION_ADDRESS = new RegionAddress(NOT_MATCH.split("\\|"));

    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>XdbSearcher</code> \u5b9e\u4f8b\u3002 */
    private XdbSearcher() {
    }
}
