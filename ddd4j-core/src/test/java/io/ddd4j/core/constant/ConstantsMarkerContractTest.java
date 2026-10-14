package io.ddd4j.core.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConstantsMarkerContractTest {

    @Test
    void publicLogMarkerNamesUseTheDdd4jNamespace() {
        assertEquals("io.ddd4j.access", Constants.ACCESS_MARKER);
        assertEquals("io.ddd4j.auth", Constants.AUTHZ_MARKER);
        assertEquals("io.ddd4j.biz", Constants.BIZ_MARKER);
    }
}
