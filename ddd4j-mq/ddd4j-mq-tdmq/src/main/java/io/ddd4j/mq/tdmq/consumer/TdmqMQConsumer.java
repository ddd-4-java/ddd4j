package io.ddd4j.mq.tdmq.consumer;

import io.ddd4j.mq.consume.MQEventConsumer;
import io.ddd4j.mq.message.Acknowledgment;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.util.TagMatcher;
import io.ddd4j.mq.tdmq.ack.TdmqAcknowledgment;
import io.ddd4j.mq.tdmq.client.TdmqClient;
import io.ddd4j.mq.tdmq.client.TdmqSubscription;
import io.ddd4j.mq.tdmq.spi.TdmqMQProperties;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 腾讯云 TDMQ 消费者实现（纯 Java，零 Spring 依赖）。
 *
 * <p>实现 {@link MQEventConsumer}，在 {@link #subscribe(MQListener, MQEventCallback)} 中通过 {@link TdmqClient} 建立订阅，
 * 收到消息后做 tag 过滤、提取 payload 字符串、构建 {@link TdmqAcknowledgment}，
 * 通过 {@link MQEventCallback} 交给 core 统一处理。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j
public class TdmqMQConsumer implements MQEventConsumer {

    private final TdmqClient tdmqClient;
    private final MQProperties mqProperties;
    private final TdmqMQProperties tdmqProperties;

    /**
     * 构造 TDMQ 消费者。
     *
     * @param tdmqClient     TDMQ 客户端
     * @param mqProperties   MQ 全局配置
     * @param tdmqProperties TDMQ 配置属性
     */
    public TdmqMQConsumer(TdmqClient tdmqClient, MQProperties mqProperties, TdmqMQProperties tdmqProperties) {
        this.tdmqClient = Objects.requireNonNull(tdmqClient, "tdmqClient");
        this.mqProperties = Objects.requireNonNull(mqProperties, "mqProperties");
        this.tdmqProperties = Objects.requireNonNull(tdmqProperties, "tdmqProperties");
    }

    @Override
    public void subscribe(MQListener listener, MQEventCallback onEvent) {
        if (!tdmqProperties.isAutoStartConsumers()) {
            log.info("TDMQ listener registration skipped because autoStartConsumers=false");
            return;
        }
        String topic = resolveTopic(listener);
        String tagExpression = listener.getTags();
        String group = resolveGroup(listener);
        TdmqSubscription subscription = tdmqClient.subscribe(topic, tagExpression, group,
                (messageId, correlationId, payload, ackCallback) ->
                        onMessage(messageId, correlationId, payload, ackCallback, listener, onEvent));
        log.info("Registered TDMQ listener: topic={}, tags={}, group={}, clientReady={}",
                topic, tagExpression, group, tdmqClient.isReady());
    }

    private void onMessage(String messageId,
                           String correlationId,
                           byte[] payload,
                           Consumer<Boolean> ackCallback,
                           MQListener listener,
                           MQEventCallback onEvent) {
        try {
            String payloadText = Objects.isNull(payload) ? "" : new String(payload, StandardCharsets.UTF_8);
            String tag = listener.getTags();
            if (!TagMatcher.match(tag, listener.getTags())) {
                return;
            }
            long deliveryTag = Objects.nonNull(correlationId) ? correlationId.hashCode() : 0L;
            Acknowledgment ack = new TdmqAcknowledgment(messageId, correlationId, deliveryTag, ackCallback);
            onEvent.onEvent(payloadText, messageId, null, tag, ack);
            if (!mqProperties.getConsumer().isManualAck() && !ack.isAcknowledged()) {
                ack.ack();
            }
        } catch (Throwable ex) {
            log.error("TDMQ consumer failed: bean={}, method={}",
                    beanLabel(listener), listener.getMethod().getName(), ex);
            ackCallback.accept(tdmqProperties.isRequeueOnError());
        }
    }

    private String resolveGroup(MQListener listener) {
        if (Objects.nonNull(listener.getGroup()) && !io.ddd4j.kit.lang.StrKit.isBlank(listener.getGroup())) {
            return listener.getGroup();
        }
        return tdmqProperties.getDefaultGroup();
    }

    private String resolveTopic(MQListener listener) {
        String namespace = io.ddd4j.kit.lang.StrKit.isNotBlank(listener.getNamespace())
                ? listener.getNamespace()
                : mqProperties.getNamespace();
        if (io.ddd4j.kit.lang.StrKit.isBlank(namespace)) {
            return listener.getTopic();
        }
        return namespace + "." + listener.getTopic();
    }

    private String beanLabel(MQListener listener) {
        if (Objects.nonNull(listener.getBean())) {
            return listener.getBean().getClass().getSimpleName();
        }
        return Objects.nonNull(listener.getMethod()) ? listener.getMethod().getDeclaringClass().getSimpleName() : "unknown";
    }
}
