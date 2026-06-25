package io.ddd4j.boot.mq.acknowledgment;

import io.ddd4j.boot.mq.registry.MQBrokerType;

/**
 * 当前 Broker 不支持所请求的确认操作时抛出。
 */
public class UnsupportedAckOperationException extends UnsupportedOperationException {

    private final MQBrokerType brokerType;
    private final String operation;

    public UnsupportedAckOperationException(String message) {
        super(message);
        this.brokerType = null;
        this.operation = null;
    }

    public UnsupportedAckOperationException(MQBrokerType brokerType, String operation) {
        super("Broker " + brokerType + " does not support acknowledgment operation: " + operation);
        this.brokerType = brokerType;
        this.operation = operation;
    }

    public MQBrokerType getBrokerType() {
        return brokerType;
    }

    public String getOperation() {
        return operation;
    }
}
