package io.ddd4j.boot.cmpt.mqtt.mica.bridge.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 存储由桥接扫描器发现的 {@link MqttClientSubscribe} 定义。
 */
public class MicaMqttClientSubscribeDefinitionRegistry {

    private final List<MicaMqttClientSubscribeDefinition> definitions = new ArrayList<>();

    /**
     * 登记一条原生订阅定义。
     */
    public void register(MicaMqttClientSubscribeDefinition definition) {
        definitions.add(definition);
    }

    /**
     * 返回已登记定义（只读副本）。
     */
    public List<MicaMqttClientSubscribeDefinition> definitions() {
        return Collections.unmodifiableList(definitions);
    }
}
