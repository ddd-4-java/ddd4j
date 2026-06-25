package io.ddd4j.mq.ons.consumer;

import com.aliyun.openservices.ons.api.Action;
import com.aliyun.openservices.ons.api.Consumer;
import com.aliyun.openservices.ons.api.Message;
import com.aliyun.openservices.ons.api.ONSFactory;
import com.aliyun.openservices.ons.api.PropertyKeyConst;
import io.ddd4j.mq.acknowledgment.MessageAcknowledgment;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.consume.MQConsumerHandler;
import io.ddd4j.mq.contract.MQMessage;
import io.ddd4j.mq.registry.MQListenerDefinition;
import io.ddd4j.mq.registry.MQListenerEndpointNaming;
import io.ddd4j.mq.ons.acknowledgment.OnsMessageAcknowledgment;
import io.ddd4j.mq.ons.acknowledgment.OnsMessageAcknowledgmentFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 将 {@code @MQEventListener} 动态注册为阿里云 ONS {@link Consumer} 端点。
 */
@Slf4j
@RequiredArgsConstructor
public class OnsMQConsumerEndpointRegistrar implements AutoCloseable {

    private final Properties onsConsumerProperties;
    private final Ddd4jMQProperties properties;
    private final List<MQListenerDefinition> registeredDefinitions = new CopyOnWriteArrayList<>();
    private final List<Consumer> consumers = new CopyOnWriteArrayList<>();

    /**
     * 注册单个监听器定义。
     */
    public void register(MQListenerDefinition definition, MQConsumerHandler handler) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(handler, "handler");

        String topic = MQListenerEndpointNaming.physicalTopic(properties, definition);
        String tag = resolveTag(definition.getTags());

        Properties consumerProps = new Properties();
        consumerProps.putAll(onsConsumerProperties);
        consumerProps.setProperty(PropertyKeyConst.GROUP_ID, definition.getGroup());

        Consumer consumer = ONSFactory.createConsumer(consumerProps);
        consumer.subscribe(topic, tag, (message, context) -> onMessage(message, definition, handler));
        consumer.start();
        consumers.add(consumer);
        registeredDefinitions.add(definition);

        log.info("Registered ONS listener: topic={}, tag={}, group={}, ackMode={}",
                topic, tag, definition.getGroup(), properties.getConsumer().getAckMode());
    }

    /**
     * 批量注册监听器。
     */
    public void registerAll(List<MQListenerDefinition> definitions, MQConsumerHandler handler) {
        if (definitions == null || definitions.isEmpty()) {
            log.debug("No @MQEventListener definitions found for ONS");
            return;
        }
        for (MQListenerDefinition definition : definitions) {
            register(definition, handler);
        }
        log.info("ONS consumer registrar initialized with {} listener(s)", registeredDefinitions.size());
    }

    @Override
    public void close() {
        for (Consumer consumer : consumers) {
            try {
                consumer.shutdown();
            } catch (Exception ex) {
                log.warn("Failed to shutdown ONS consumer", ex);
            }
        }
        consumers.clear();
    }

    /**
     * 返回已登记的监听器定义。
     */
    public List<MQListenerDefinition> registeredDefinitions() {
        return List.copyOf(registeredDefinitions);
    }

    /**
     * ONS 消息监听回调：将 handler 的 ack 语义映射为 {@link Action}。
     */
    private Action onMessage(Message onsMessage, MQListenerDefinition definition, MQConsumerHandler handler) {
        AtomicReference<Action> action = new AtomicReference<>(Action.CommitMessage);
        try {
            String payloadText = new String(onsMessage.getBody(), StandardCharsets.UTF_8);
            Map<String, Object> headers = new HashMap<>();
            headers.put("ons.topic", onsMessage.getTopic());
            headers.put("ons.tag", onsMessage.getTag());

            OnsMessageAcknowledgment ack = OnsMessageAcknowledgmentFactory.fromOnsMessage(onsMessage)
                    .orElseThrow(() -> new IllegalStateException("Failed to build ONS acknowledgment"));

            OnsMessageAcknowledgment trackedAck = new OnsMessageAcknowledgment(
                    onsMessage.getMsgID(),
                    onsMessage.getKey(),
                    onsMessage.getReconsumeTimes(),
                    () -> {
                        action.set(Action.CommitMessage);
                        return Action.CommitMessage;
                    },
                    () -> {
                        action.set(Action.ReconsumeLater);
                        return Action.ReconsumeLater;
                    });

            MQMessage<String> mqMessage = MQMessage.of(
                    payloadText,
                    headers,
                    onsMessage.getMsgID(),
                    onsMessage.getKey(),
                    onsMessage);

            MessageAcknowledgment messageAck = trackedAck;
            handler.handle(mqMessage, messageAck);
            if (!properties.getConsumer().isManualAck() && !trackedAck.isAcknowledged()) {
                trackedAck.ack();
            }
            return action.get();
        } catch (Exception ex) {
            log.error("ONS consumer failed: bean={}, method={}",
                    beanLabel(definition), definition.getMethod().getName(), ex);
            return Action.ReconsumeLater;
        }
    }

    private String resolveTag(String tags) {
        String tag = MQListenerEndpointNaming.resolveTag(tags);
        return tag == null ? "*" : tag;
    }

    private String beanLabel(MQListenerDefinition definition) {
        if (definition.getBean() != null) {
            return definition.getBean().getClass().getSimpleName();
        }
        if (definition.getBeanName() != null) {
            return definition.getBeanName();
        }
        return definition.getMethod().getDeclaringClass().getSimpleName();
    }
}
