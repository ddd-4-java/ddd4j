package io.ddd4j.mq.pulsar.spi;

import org.apache.pulsar.client.api.ClientBuilder;
import org.apache.pulsar.client.api.PulsarClient;

import java.util.Objects;

/**
 * Apache Pulsar 适配器配置（纯 Java，零 Spring 依赖）。
 *
 * <p>版本通过 {@code pulsar-bom} 与 {@code ${pulsar.version}} 对齐。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class PulsarMQProperties {

    private String serviceUrl = "pulsar://localhost:6650";
    private String tenant = "public";
    private String namespace = "default";
    private String authToken;
    private long operationTimeoutMs = 30_000L;
    private int ioThreads = 1;
    private int listenerThreads = 1;
    /**
     * Subscription type: Exclusive / Shared / Failover / Key_Shared.
     */
    private String subscriptionType = "Shared";
    /**
     * Subscription name (per consumer).
     */
    private String subscriptionName = "ddd4j-mq-subscription";
    /**
     * Negative ack redelivery delay (ms).
     */
    private long negativeAckRedeliveryDelayMs = 1_000L;

    public String getServiceUrl() {
        return serviceUrl;
    }

    public void setServiceUrl(String serviceUrl) {
        this.serviceUrl = serviceUrl;
    }

    public String getTenant() {
        return tenant;
    }

    public void setTenant(String tenant) {
        this.tenant = tenant;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    public long getOperationTimeoutMs() {
        return operationTimeoutMs;
    }

    public void setOperationTimeoutMs(long operationTimeoutMs) {
        this.operationTimeoutMs = operationTimeoutMs;
    }

    public int getIoThreads() {
        return ioThreads;
    }

    public void setIoThreads(int ioThreads) {
        this.ioThreads = ioThreads;
    }

    public int getListenerThreads() {
        return listenerThreads;
    }

    public void setListenerThreads(int listenerThreads) {
        this.listenerThreads = listenerThreads;
    }

    public String getSubscriptionType() {
        return subscriptionType;
    }

    public void setSubscriptionType(String subscriptionType) {
        this.subscriptionType = subscriptionType;
    }

    public String getSubscriptionName() {
        return subscriptionName;
    }

    public void setSubscriptionName(String subscriptionName) {
        this.subscriptionName = subscriptionName;
    }

    public long getNegativeAckRedeliveryDelayMs() {
        return negativeAckRedeliveryDelayMs;
    }

    public void setNegativeAckRedeliveryDelayMs(long negativeAckRedeliveryDelayMs) {
        this.negativeAckRedeliveryDelayMs = negativeAckRedeliveryDelayMs;
    }

    public PulsarClient client() throws Exception {
        ClientBuilder b = PulsarClient.builder()
                .serviceUrl(serviceUrl)
                .operationTimeout(Math.toIntExact(operationTimeoutMs), java.util.concurrent.TimeUnit.MILLISECONDS)
                .ioThreads(ioThreads)
                .listenerThreads(listenerThreads);
        if (Objects.nonNull(authToken) && !io.ddd4j.kit.lang.StrKit.isBlank(authToken)) {
            b.authentication(org.apache.pulsar.client.api.AuthenticationFactory.token(authToken));
        }
        return b.build();
    }

    /**
     * Physical topic: {@code tenant/namespace/topic[:tag]}
     */
    public String physicalTopic(String topic, String tag) {
        Objects.requireNonNull(topic, "topic");
        return tenant + "/" + namespace + "/" + (Objects.isNull(tag) || io.ddd4j.kit.lang.StrKit.isBlank(tag) ? topic : topic + ":" + tag);
    }
}
