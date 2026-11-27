package io.ddd4j.core.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** \u9a8c\u8bc1 DDD4J \u5e38\u91cf\u7c7b\u4e0e\u6807\u8bb0\u6ce8\u89e3\u7684\u7a33\u5b9a\u5951\u7ea6\u3002 */
class ConstantsMarkerContractTest {

    /** \u9a8c\u8bc1<code>Constants.ACCESS_MARKER</code> \u7b49\u4e8e <code>"io.ddd4j.access"</code>\uff0c<code>Constants.AUTHZ_MARKER</code> \u7b49\u4e8e <code>"io.ddd4j.auth"</code>\uff0c<code>Constants.BIZ_MARKER</code> \u7b49\u4e8e <code>"io.ddd4j.biz"</code>\u3002 */
    @Test
    void publicLogMarkerNamesUseTheDdd4jNamespace() {
        assertEquals("io.ddd4j.access", Constants.ACCESS_MARKER);
        assertEquals("io.ddd4j.auth", Constants.AUTHZ_MARKER);
        assertEquals("io.ddd4j.biz", Constants.BIZ_MARKER);
    }
}
