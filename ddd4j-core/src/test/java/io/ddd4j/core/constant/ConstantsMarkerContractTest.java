package io.ddd4j.core.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证 DDD4J 常量类与标记注解的稳定契约。
 */
class ConstantsMarkerContractTest {

    /**
     * 验证<code>Constants.ACCESS_MARKER</code> 等于 <code>"io.ddd4j.access"</code>，<code>Constants.AUTHZ_MARKER</code> 等于 <code>"io.ddd4j.auth"</code>，<code>Constants.BIZ_MARKER</code> 等于 <code>"io.ddd4j.biz"</code>。
     */
    @Test
    void publicLogMarkerNamesUseTheDdd4jNamespace() {
        assertEquals("io.ddd4j.access", Constants.ACCESS_MARKER);
        assertEquals("io.ddd4j.auth", Constants.AUTHZ_MARKER);
        assertEquals("io.ddd4j.biz", Constants.BIZ_MARKER);
    }
}
