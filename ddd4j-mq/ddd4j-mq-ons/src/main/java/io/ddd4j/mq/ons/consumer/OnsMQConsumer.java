package io.ddd4j.mq.ons.consumer;

import com.aliyun.openservices.ons.api.Action;
import com.aliyun.openservices.ons.api.Consumer;
import com.aliyun.openservices.ons.api.ConsumeContext;
import com.aliyun.openservices.ons.api.Message;
import com.aliyun.openservices.ons.api.ONSFactory;
import io.ddd4j.mq.consume.MQEventConsumer;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.util.TagMatcher;
import io.ddd4j.mq.ons.ack.OnsAcknowledgment;
import io.ddd4j.mq.ons.spi.OnsMQProperties;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 阿里云 ONS 消费者实现（纯 Java，零 Spring 依赖）。
 *
 * <p>实现 {@link MQEventConsumer}，在 {@link #subscribe(MQListener, MQEventCallback)} 中建立 ONS 消费者，
 * 收到消息后做 tag 过滤、提取 payload 字符串、构建 {@link OnsAcknowledgment}，
 * 通过 {@link MQEventCallback} 交给 core 统一处理；最终根据 {@link OnsAcknowledgment#action()} 返回 ONS {@link Action}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class OnsMQConsumer implements MQEventConsumer {

    private final OnsMQProperties properties;

    /**
     * 构造 ONS 消费者。
     *
     * @param properties ONS 配置属性
     */
    public OnsMQConsumer(OnsMQProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public void subscribe(MQListener listener, MQEventCallback onEvent) {
        String group = Objects.isNull(listener.getGroup()) || io.ddd4j.kit.lang.StrKit.isBlank(listener.getGroup())
                ? properties.getConsumerId() : listener.getGroup();
        if (Objects.isNull(group)) {
            throw new IllegalStateException("OnsMQConsumer requires consumerId or @MQEventListener(group=...)");
        }
        String topic = Objects.isNull(listener.getTopic()) ? properties.getTopic() : listener.getTopic();
        if (Objects.isNull(topic)) {
            throw new IllegalStateException("OnsMQConsumer requires topic");
        }
        String tag = TagMatcher.findIncludes(listener.getTags()).stream().findFirst().orElse(null);
        Consumer consumer = ONSFactory.createConsumer(properties.sessionProperties(group));
        consumer.subscribe(topic, properties.subscriptionExpression(tag),
                (msg, ctx) -> handleMessage(msg, ctx, listener, onEvent));
        consumer.start();
    }

    private Action handleMessage(Message message, ConsumeContext context, MQListener listener, MQEventCallback onEvent) {
        try {
            if (!TagMatcher.match(message.getTag(), listener.getTags())) {
                return Action.CommitMessage;
            }
            String payload = new String(message.getBody(), StandardCharsets.UTF_8);
            OnsAcknowledgment ack = new OnsAcknowledgment(context, message);
            onEvent.onEvent(payload, message.getMsgID(), null, message.getTag(), ack);
            return ack.action();
        } catch (Throwable ex) {
            return Action.ReconsumeLater;
        }
    }
}
