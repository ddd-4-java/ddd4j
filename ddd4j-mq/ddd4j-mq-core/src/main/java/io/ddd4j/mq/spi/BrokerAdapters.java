package io.ddd4j.mq.spi;

import io.ddd4j.mq.config.MQProperties;
import io.ddd4j.mq.config.BrokerType;

import java.util.List;
import java.util.Objects;

/**
 * {@link BrokerAdapter} 解析工具。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public final class BrokerAdapters {

    private BrokerAdapters() {
    }

    /**
     * 按配置选择 {@link BrokerAdapter}。
     *
     * @param adapters 已注册的适配器
     * @param props    MQ 配置
     * @return 匹配的适配器
     */
    public static BrokerAdapter selectAdapter(List<BrokerAdapter> adapters, MQProperties props) {
        Objects.requireNonNull(props, "props");
        BrokerType configured = props.brokerType();
        if (configured == BrokerType.NONE) {
            throw new IllegalStateException("ddd4j.mq.broker is not configured or unsupported: " + props.getBroker());
        }
        if (Objects.isNull(adapters) || adapters.isEmpty()) {
            throw new IllegalStateException("No BrokerAdapter bean found for broker=" + configured);
        }
        return adapters.stream()
                .filter(adapter -> adapter.supports(configured))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No BrokerAdapter supports broker=" + configured + ", registered=" + adapters.size()));
    }
}
