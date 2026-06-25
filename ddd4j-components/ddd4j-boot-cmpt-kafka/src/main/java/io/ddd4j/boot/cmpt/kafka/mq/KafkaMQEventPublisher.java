package io.ddd4j.boot.cmpt.kafka.mq;

import io.ddd4j.boot.core.contract.MQEvent;
import io.ddd4j.boot.mq.config.Ddd4jMQProperties;
import io.ddd4j.boot.mq.contract.MQDestination;
import io.ddd4j.boot.core.utils.JsonKit;
import io.ddd4j.boot.mq.serialization.MQEventSerialization;
import io.ddd4j.boot.mq.publish.MQEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * 基于 {@link KafkaTemplate} 的领域事件发布实现。
 */
@Slf4j
public class KafkaMQEventPublisher implements MQEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final MQEventSerialization serialization;
    private final Ddd4jMQProperties properties;

    /**
     * 构建 Kafka 事件发布器。
     *
     * @param kafkaTemplate Kafka 模板
     * @param serialization 序列化器
     * @param properties    MQ 配置
     */
    public KafkaMQEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            MQEventSerialization serialization,
            Ddd4jMQProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.serialization = serialization;
        this.properties = properties;
    }

    /**
     * 发布领域事件到 Kafka topic。
     *
     * @param event       领域事件
     * @param destination 目的地
     * @param <T>         事件类型
     */
    @Override
    public <T extends MQEvent> void publish(T event, MQDestination destination) {
        String payload = JsonKit.toJson(event);
        String topic = resolveTopic(event, destination);
        String key = StringUtils.defaultIfBlank(event.getTag(), event.getTenantId());
        log.debug("Publish MQ event to Kafka topic [{}], key [{}]", topic, key);
        if (StringUtils.isNotBlank(key)) {
            kafkaTemplate.send(topic, key, payload);
        } else {
            kafkaTemplate.send(topic, payload);
        }
    }

    /**
     * 解析物理 topic：namespace + concat + topic，与 legacy KafkaClient 对齐。
     *
     * @param event       领域事件
     * @param destination 目的地
     * @return Kafka topic
     */
    private String resolveTopic(MQEvent event, MQDestination destination) {
        String namespace = StringUtils.defaultIfBlank(destination.namespace(), properties.getNamespace());
        if (StringUtils.isNotBlank(event.getNamespace())) {
            namespace = event.getNamespace();
        }
        String concat = StringUtils.defaultIfBlank(event.getConcat(), "_");
        String topic = destination.topic();
        if (StringUtils.isBlank(topic)) {
            topic = StringUtils.defaultIfBlank(event.getTopic(), properties.getDefaultTopic());
        }
        if (StringUtils.isNotBlank(namespace)) {
            return namespace + concat + topic;
        }
        return topic;
    }
}
