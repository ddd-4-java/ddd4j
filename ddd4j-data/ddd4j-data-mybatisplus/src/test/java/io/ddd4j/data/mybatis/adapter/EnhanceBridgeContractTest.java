package io.ddd4j.data.mybatis.adapter;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class EnhanceBridgeContractTest {
    @Test
    void encryptionPortMustBeConsumableByUpstreamPlugin() throws Exception {
        assertThat(Class.forName("com.baomidou.mybatisplus.enhance.crypto.handler.EncryptedFieldHandler")
                .isAssignableFrom(io.ddd4j.data.mybatis.plugins.handler.EncryptedFieldHandler.class)).isTrue();
    }

    @Test
    void upstreamObservationMustReachDdd4jContext() throws Exception {
        Class<?> sinkType = Class.forName("com.baomidou.mybatisplus.enhance.observation.SqlObservationSink");
        assertThat(sinkType.isInstance(new io.ddd4j.data.mybatis.plugins.observation.Ddd4jSqlObservationSink())).isTrue();
    }
}
