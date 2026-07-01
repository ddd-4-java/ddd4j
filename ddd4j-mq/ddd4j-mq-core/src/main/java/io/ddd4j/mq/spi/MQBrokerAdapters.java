package io.ddd4j.mq.spi;

import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.publish.MQEventPublisher;
import io.ddd4j.mq.registry.MQBrokerType;

import java.util.List;
import java.util.Objects;

/**
 * {@link MQBrokerAdapter} 解析工具。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public final class MQBrokerAdapters {

    private MQBrokerAdapters() {
    }

    /**
     * 按配置选择并创建 {@link MQEventPublisher}。
     *
     * @param adapters 已注册的 Broker 适配器
     * @param props    MQ 配置
     * @return 发布器实现
     */
    public static MQEventPublisher createPublisher(List<MQBrokerAdapter> adapters, Ddd4jMQProperties props) {
        Objects.requireNonNull(props, "props");
        MQBrokerType configured = props.brokerType();
        if (configured == MQBrokerType.NONE) {
            throw new IllegalStateException("ddd4j.mq.broker is not configured or unsupported: " + props.getBroker());
        }
        if (Objects.isNull(adapters) || adapters.isEmpty()) {
            throw new IllegalStateException(
                    "No MQBrokerAdapter bean found for broker=" + configured
                            + ". Add ddd4j-cmpt-* module matching ddd4j.mq.broker.");
        }
        return adapters.stream()
                .filter(adapter -> adapter.supports(configured))
                .findFirst()
                .map(adapter -> adapter.createPublisher(props))
                .orElseThrow(() -> new IllegalStateException(
                        "No MQBrokerAdapter supports broker=" + configured + ", registered=" + adapters.size()));
    }

    /**
     * 按配置选择 {@link MQBrokerAdapter}。
     *
     * @param adapters 已注册的适配器
     * @param props    MQ 配置
     * @return 匹配的适配器
     */
    public static MQBrokerAdapter selectAdapter(List<MQBrokerAdapter> adapters, Ddd4jMQProperties props) {
        Objects.requireNonNull(props, "props");
        MQBrokerType configured = props.brokerType();
        if (configured == MQBrokerType.NONE) {
            throw new IllegalStateException("ddd4j.mq.broker is not configured or unsupported: " + props.getBroker());
        }
        if (Objects.isNull(adapters) || adapters.isEmpty()) {
            throw new IllegalStateException("No MQBrokerAdapter bean found for broker=" + configured);
        }
        return adapters.stream()
                .filter(adapter -> adapter.supports(configured))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No MQBrokerAdapter supports broker=" + configured + ", registered=" + adapters.size()));
    }

    /**
     * {@link #selectAdapter(List, Ddd4jMQProperties)} 别名。
     */
    public static MQBrokerAdapter resolveAdapter(List<MQBrokerAdapter> adapters, Ddd4jMQProperties props) {
        return selectAdapter(adapters, props);
    }
}
