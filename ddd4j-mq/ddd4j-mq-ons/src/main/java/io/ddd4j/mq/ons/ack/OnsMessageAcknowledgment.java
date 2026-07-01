package io.ddd4j.mq.ons.ack;

import java.util.Objects;

import com.aliyun.openservices.ons.api.Action;
import com.aliyun.openservices.ons.api.ConsumeContext;
import com.aliyun.openservices.ons.api.Message;
import io.ddd4j.mq.ack.MessageAcknowledgment;
import io.ddd4j.mq.ack.UnsupportedAckOperationException;
import io.ddd4j.mq.registry.MQBrokerType;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Alibaba ONS manual acknowledgment mapping.
 * ONS consumes are acknowledged by the {@link Action} returned from the listener.
 */
public class OnsMessageAcknowledgment implements MessageAcknowledgment {

    public static final String HEADER_ONS_MESSAGE = "ddd4j.ons.message";
    public static final String HEADER_ONS_CONTEXT = "ddd4j.ons.context";

    private final ConsumeContext context;
    private final Message message;
    private final String messageId;
    private final String key;
    private final long offset;
    private final AtomicBoolean acknowledged = new AtomicBoolean(false);
    private volatile Action action = Action.CommitMessage;

    public OnsMessageAcknowledgment(ConsumeContext context, Message message) {
        this.context = context;
        this.message = message;
        this.messageId = message.getMsgID();
        this.key = message.getKey();
        this.offset = message.getOffset();
    }

    @Override
    public long deliveryTag() {
        return offset;
    }

    @Override
    public String messageId() {
        return messageId;
    }

    @Override
    public String correlationId() {
        return key;
    }

    @Override
    public boolean isOpen() {
        return Objects.nonNull(context);
    }

    @Override
    public boolean isAcknowledged() {
        return acknowledged.get();
    }

    @Override
    public MQBrokerType brokerType() {
        return MQBrokerType.ONS;
    }

    @Override
    public void ack() {
        ack(false);
    }

    @Override
    public void ack(boolean multiple) {
        runOnce(() -> action = Action.CommitMessage);
    }

    @Override
    public void nack(boolean requeue) {
        nack(false, requeue);
    }

    @Override
    public void nack(boolean multiple, boolean requeue) {
        runOnce(() -> {
            if (requeue) {
                action = Action.ReconsumeLater;
            } else {
                action = Action.CommitMessage; // no native reject-without-commit path
            }
        });
    }

    @Override
    public void reject(boolean requeue) {
        nack(false, requeue);
    }

    @Override
    public void recover(boolean requeue) {
        nack(false, requeue);
    }

    @Override
    public <T> Optional<T> unwrap(Class<T> type) {
        if (Objects.isNull(type)) {
            return Optional.empty();
        }
        if (type.isInstance(context)) {
            return Optional.of(type.cast(context));
        }
        if (type.isInstance(message)) {
            return Optional.of(type.cast(message));
        }
        if (type.isInstance(this)) {
            return Optional.of(type.cast(this));
        }
        return Optional.empty();
    }

    private void runOnce(IoOperation op) {
        if (!acknowledged.compareAndSet(false, true)) {
            throw new UnsupportedAckOperationException("ONS message already ack'd, id=" + messageId);
        }
        op.run();
    }

    public Action action() {
        return action;
    }

    @FunctionalInterface
    private interface IoOperation {
        void run();
    }
}
