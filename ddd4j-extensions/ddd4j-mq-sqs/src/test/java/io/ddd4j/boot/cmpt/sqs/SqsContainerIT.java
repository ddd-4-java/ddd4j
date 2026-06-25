package io.ddd4j.boot.cmpt.sqs;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import io.ddd4j.boot.cmpt.sqs.autoconfigure.Ddd4jSqsMQAutoConfiguration;
import io.ddd4j.boot.core.contract.MQEvent;
import io.ddd4j.boot.mq.contract.MQDestination;
import io.ddd4j.boot.mq.publish.MQEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * AWS SQS 发布路径 Testcontainers 冒烟集成测试（{@code aws-java-sdk-sqs}）。
 * <p>
 * 使用 {@link GenericContainer} + {@code softwaremill/elasticmq-native}（SQS 兼容 API，镜像小于 LocalStack）；
 * IT 内 {@code @Primary} 覆盖 {@link AmazonSQS} 指向 ElasticMQ 端点。
 */
@SpringBootTest(classes = SqsContainerIT.TestApplication.class)
@EnabledIf("io.ddd4j.boot.cmpt.sqs.SqsContainerIT#isDockerAvailable")
class SqsContainerIT {

    private static final String AWS_REGION = "us-east-1";
    private static final String QUEUE_NAME = "ddd4j-smoke-queue";
    private static final int ELASTICMQ_PORT = 9324;

    private static final GenericContainer<?> ELASTICMQ = new GenericContainer<>(
            DockerImageName.parse("softwaremill/elasticmq-native:1.6.8"))
            .withExposedPorts(ELASTICMQ_PORT)
            .waitingFor(Wait.forListeningPort());

    private static String queueUrl;
    private static String sqsEndpoint;

    private static volatile boolean containersStarted;

    @Autowired
    private MQEventPublisher mqEventPublisher;

    @Autowired
    private AmazonSQS amazonSqs;

    /**
     * 在 Spring 上下文启动前拉起 ElasticMQ 并预创建 SQS 队列。
     */
    private static synchronized void ensureContainersStarted() {
        if (containersStarted) {
            return;
        }
        ELASTICMQ.start();
        sqsEndpoint = "http://" + ELASTICMQ.getHost() + ":" + ELASTICMQ.getMappedPort(ELASTICMQ_PORT);
        AmazonSQS bootstrapClient = AmazonSQSClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(sqsEndpoint, AWS_REGION))
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("x", "x")))
                .build();
        queueUrl = bootstrapClient.createQueue(QUEUE_NAME).getQueueUrl();
        containersStarted = true;
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        ensureContainersStarted();
        registry.add("ddd4j.mq.enabled", () -> "true");
        registry.add("ddd4j.mq.broker", () -> "sqs");
        registry.add("ddd4j.mq.namespace", () -> "it");
        registry.add("ddd4j.mq.sqs.region", () -> AWS_REGION);
        registry.add("ddd4j.mq.sqs.queue-url", () -> queueUrl);
    }

    /**
     * Docker 是否可用（Testcontainers 前置条件）。
     */
    static boolean isDockerAvailable() {
        try {
            DockerClientFactory.instance().client();
            return true;
        } catch (Throwable ex) {
            return false;
        }
    }

    @Test
    void publishShouldNotThrow() {
        assertNotNull(mqEventPublisher);
        assertNotNull(amazonSqs);

        DemoPublishEvent event = new DemoPublishEvent();
        event.setTopic(queueUrl);
        event.setTag("ping");
        event.setTenantId("tenant-it");

        assertDoesNotThrow(() -> mqEventPublisher.publish(
                event,
                MQDestination.of(queueUrl, "ping", "it")));
    }

    @SpringBootApplication
    @Import({
            io.ddd4j.boot.mq.config.Ddd4jMQAutoConfiguration.class,
            Ddd4jSqsMQAutoConfiguration.class,
            SqsContainerIT.ElasticMqSqsConfiguration.class
    })
    static class TestApplication {
    }

    /**
     * 覆盖默认 AmazonSQS，将客户端指向 ElasticMQ 端点。
     */
    static class ElasticMqSqsConfiguration {

        /**
         * 注册指向 ElasticMQ 的 SQS 客户端。
         */
        @Bean
        @Primary
        AmazonSQS elasticMqAmazonSqs() {
            ensureContainersStarted();
            return AmazonSQSClientBuilder.standard()
                    .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(sqsEndpoint, AWS_REGION))
                    .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("x", "x")))
                    .build();
        }
    }

    static class DemoPublishEvent extends MQEvent {
    }
}
