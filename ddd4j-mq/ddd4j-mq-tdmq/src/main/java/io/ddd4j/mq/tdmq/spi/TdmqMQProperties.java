package io.ddd4j.mq.tdmq.spi;

import lombok.Data;

/**
 * TDMQ adapter 配置。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
public class TdmqMQProperties {

    private String serviceUrl;
    private String tenant;
    private String namespace;
    private String accessKey;
    private String secretKey;
    private String defaultGroup = "ddd4j-tdmq";
    private boolean autoStartConsumers = true;
    private boolean requeueOnError = true;
}
