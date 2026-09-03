package io.ddd4j.mq.mqtt.mica.bridge.consumer;

import io.ddd4j.mq.mqtt.mica.bridge.registry.MicaMqttClientSubscribeDefinition;
import io.ddd4j.mq.mqtt.mica.bridge.registry.MicaMqttClientSubscribeDefinitionRegistry;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.core.annotation.MqttClientSubscribe;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MicaMqttClientSubscribeScanner} 单元测试。
 */
class MicaMqttClientSubscribeScannerTest {

    @Test
    void scanShouldDiscoverAnnotatedMethods() {
        MicaMqttClientSubscribeDefinitionRegistry registry = new MicaMqttClientSubscribeDefinitionRegistry();
        MicaMqttClientSubscribeRegistrar registrar = mock(MicaMqttClientSubscribeRegistrar.class);
        when(registrar.resolveTopicFilters(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MicaMqttClientSubscribeScanner scanner = new MicaMqttClientSubscribeScanner(registry, registrar);
        DemoSubscribeListener listener = new DemoSubscribeListener();
        scanner.postProcessAfterInitialization(listener, "demoSubscribeListener");

        List<MicaMqttClientSubscribeDefinition> definitions = scanner.scan();

        assertFalse(definitions.isEmpty());
        assertEquals(2, definitions.size());

        MicaMqttClientSubscribeDefinition qos0 = definitions.stream()
                .filter(def -> "subQos0".equals(def.getMethod().getName()))
                .findFirst()
                .orElseThrow();
        assertEquals("/test/#", qos0.getTopicFilters()[0]);
        assertEquals(MqttQoS.QOS0, qos0.getQos());

        MicaMqttClientSubscribeDefinition qos1 = definitions.stream()
                .filter(def -> "subQos1".equals(def.getMethod().getName()))
                .findFirst()
                .orElseThrow();
        assertEquals("/qos1/#", qos1.getTopicFilters()[0]);
        assertEquals(MqttQoS.QOS1, qos1.getQos());

        verify(registrar, times(2)).register(any(MicaMqttClientSubscribeDefinition.class));
    }

    static class DemoSubscribeListener {

        @MqttClientSubscribe("/test/#")
        public void subQos0(String topic, byte[] payload) {
            // test hook
        }

        @MqttClientSubscribe(value = "/qos1/#", qos = MqttQoS.QOS1)
        public void subQos1(String topic, byte[] payload) {
            new String(payload, StandardCharsets.UTF_8);
        }
    }
}
