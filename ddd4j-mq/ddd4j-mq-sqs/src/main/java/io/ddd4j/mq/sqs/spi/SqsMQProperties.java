package io.ddd4j.mq.sqs.spi;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;

import java.net.URI;
import java.util.Objects;

/**
 * AWS SQS 适配器配置（纯 Java，零 Spring 依赖）。
 *
 * <p>SQS 没有 topic/tag 概念：{@code Destination.topic} 直接被解释为 queueUrl。
 * 多 queueUrl 场景下，业务可在发布/消费端各自注入 {@code Map<String,String>} 进行路由。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class SqsMQProperties {

    private String region = "us-east-1";
    /**
     * 可选：自定义端点（LocalStack、LocalStack community）。
     */
    private String endpointOverride;
    private String accessKey;
    private String secretKey;
    /**
     * 单次 long poll 等待时长（秒）。
     */
    private int waitTimeSeconds = 20;
    /**
     * 接收批大小（最大 10）。
     */
    private int maxNumberOfMessages = 10;
    /**
     * Visibility timeout（消息从队列隐藏的最大时间）。
     */
    private int visibilityTimeoutSeconds = 30;
    /**
     * 错误 nack 时是否重置 visibility 让消息立即被另一消费者接收。
     */
    private boolean requeueOnNack = true;
    /**
     * Long poll 期间轮询间隔（毫秒）。
     */
    private long pollIntervalMs = 200L;

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getEndpointOverride() {
        return endpointOverride;
    }

    public void setEndpointOverride(String endpointOverride) {
        this.endpointOverride = endpointOverride;
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

    public int getWaitTimeSeconds() {
        return waitTimeSeconds;
    }

    public void setWaitTimeSeconds(int waitTimeSeconds) {
        this.waitTimeSeconds = waitTimeSeconds;
    }

    public int getMaxNumberOfMessages() {
        return maxNumberOfMessages;
    }

    public void setMaxNumberOfMessages(int maxNumberOfMessages) {
        this.maxNumberOfMessages = maxNumberOfMessages;
    }

    public int getVisibilityTimeoutSeconds() {
        return visibilityTimeoutSeconds;
    }

    public void setVisibilityTimeoutSeconds(int visibilityTimeoutSeconds) {
        this.visibilityTimeoutSeconds = visibilityTimeoutSeconds;
    }

    public boolean isRequeueOnNack() {
        return requeueOnNack;
    }

    public void setRequeueOnNack(boolean requeueOnNack) {
        this.requeueOnNack = requeueOnNack;
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public AwsCredentialsProvider credentialsProvider() {
        if (Objects.nonNull(accessKey) && !io.ddd4j.kit.lang.StrKit.isBlank(accessKey) && Objects.nonNull(secretKey) && !io.ddd4j.kit.lang.StrKit.isBlank(secretKey)) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        }
        return DefaultCredentialsProvider.create();
    }

    public SqsClient client() {
        SqsClientBuilder b = SqsClient.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider());
        if (Objects.nonNull(endpointOverride) && !io.ddd4j.kit.lang.StrKit.isBlank(endpointOverride)) {
            b.endpointOverride(URI.create(endpointOverride));
        }
        return b.build();
    }
}
