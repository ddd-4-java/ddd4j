package io.ddd4j.mq.consume;

import io.ddd4j.mq.config.BrokerType;
import lombok.Getter;

/**
 * 当前 Broker 不支持所请求的确认操作时抛出。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Getter
public class UnsupportedAckOperationException extends UnsupportedOperationException {

    private final BrokerType brokerType;
    private final String operation;

    public UnsupportedAckOperationException(String message) {
        super(message);
        this.brokerType = null;
        this.operation = null;
    }

    public UnsupportedAckOperationException(BrokerType brokerType, String operation) {
        super("Broker " + brokerType + " does not support acknowledgment operation: " + operation);
        this.brokerType = brokerType;
        this.operation = operation;
    }

}
