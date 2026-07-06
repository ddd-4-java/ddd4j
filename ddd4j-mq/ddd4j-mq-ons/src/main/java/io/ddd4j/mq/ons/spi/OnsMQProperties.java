package io.ddd4j.mq.ons.spi;

import com.aliyun.openservices.ons.api.PropertyKeyConst;

import java.util.Objects;
import java.util.Properties;

/**
 * 阿里云 ONS 适配器配置（纯 Java，零 Spring 依赖）。
 *
 * <p>需要阿里云 AccessKey、Topic、ProducerId、ConsumerId 等。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class OnsMQProperties {

    private String nameSrvAddr = "http://onsaddr-internet.aliyun.com:80/rocketmq/nsaddr4client-internet";
    private String accessKey;
    private String secretKey;
    private String producerId;
    private String consumerId;
    private String topic;
    /**
     * 默认 tag 表达式（监听侧若 ListenerDefinition.tags 为空则用此值）。
     */
    private String defaultTag = "*";
    private String namespace;
    /**
     * Long polling 大小（KB）。
     */
    private int consumeMessageBatchMaxSize = 32;
    private int consumeThreadCount = 20;
    private int maxReconsumeTimes = 3;

    public String getNameSrvAddr() {
        return nameSrvAddr;
    }

    public void setNameSrvAddr(String nameSrvAddr) {
        this.nameSrvAddr = nameSrvAddr;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getProducerId() {
        return producerId;
    }

    public void setProducerId(String producerId) {
        this.producerId = producerId;
    }

    public String getConsumerId() {
        return consumerId;
    }

    public void setConsumerId(String consumerId) {
        this.consumerId = consumerId;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getDefaultTag() {
        return defaultTag;
    }

    public void setDefaultTag(String defaultTag) {
        this.defaultTag = defaultTag;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public int getConsumeMessageBatchMaxSize() {
        return consumeMessageBatchMaxSize;
    }

    public void setConsumeMessageBatchMaxSize(int consumeMessageBatchMaxSize) {
        this.consumeMessageBatchMaxSize = consumeMessageBatchMaxSize;
    }

    public int getConsumeThreadCount() {
        return consumeThreadCount;
    }

    public void setConsumeThreadCount(int consumeThreadCount) {
        this.consumeThreadCount = consumeThreadCount;
    }

    public int getMaxReconsumeTimes() {
        return maxReconsumeTimes;
    }

    public void setMaxReconsumeTimes(int maxReconsumeTimes) {
        this.maxReconsumeTimes = maxReconsumeTimes;
    }

    public Properties sessionProperties(String groupName) {
        Properties p = new Properties();
        p.setProperty(PropertyKeyConst.AccessKey, accessKey);
        p.setProperty(PropertyKeyConst.SecretKey, secretKey);
        p.setProperty(PropertyKeyConst.NAMESRV_ADDR, nameSrvAddr);
        p.setProperty(PropertyKeyConst.GROUP_ID, Objects.isNull(groupName) ? "DEFAULT_GROUP" : groupName);
        if (Objects.nonNull(namespace) && !io.ddd4j.kit.lang.StrKit.isBlank(namespace)) {
            p.setProperty(PropertyKeyConst.INSTANCE_ID, namespace);
        }
        return p;
    }

    public String subscriptionExpression(String tag) {
        return (Objects.isNull(tag) || io.ddd4j.kit.lang.StrKit.isBlank(tag)) ? defaultTag : tag;
    }
}
