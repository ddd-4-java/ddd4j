package io.ddd4j.core.constant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConstantsMarkerContractTest {

    @Test
    void markerNamesUseDdd4jNamespaceAcrossLines() {
        assertThat(Constants.ACCESS_MARKER).isEqualTo("io.ddd4j.access");
        assertThat(Constants.AUTHZ_MARKER).isEqualTo("io.ddd4j.auth");
        assertThat(Constants.BIZ_MARKER).isEqualTo("io.ddd4j.biz");
    }
}
