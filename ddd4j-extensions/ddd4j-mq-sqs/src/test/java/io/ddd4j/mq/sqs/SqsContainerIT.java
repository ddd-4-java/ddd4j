package io.ddd4j.mq.sqs;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import io.ddd4j.mq.config.Ddd4jMQPropertiesConfiguration;
import io.ddd4j.mq.sqs.autoconfigure.Ddd4jSqsMQAutoConfiguration;
import io.ddd4j.mq.test.AbstractMqContainerIT;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * AWS SQS 发布路径 Testcontainers 冒烟集成测试（纯 Spring Framework，无 Boot）。
 * <p>
 * 使用 ElasticMQ 兼容端点；IT 内 {@code @Primary} 覆盖 {@link AmazonSQS}。
 * 公共骨架（发布者注入、Docker 前置、冒烟发布断言）见 {@link AbstractMqContainerIT}。
 * </p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        Ddd4jMQPropertiesConfiguration.class,
        Ddd4jSqsMQAutoConfiguration.class,
        SqsContainerIT.ElasticMqSqsConfiguration.class
})
@EnabledIf("io.ddd4j.mq.test.AbstractMqContainerIT#isDockerAvailable")
class SqsContainerIT extends AbstractMqContainerIT {

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
        registerCommonMqProperties(registry, "sqs", SMOKE_NAMESPACE);
        registry.add("ddd4j.mq.sqs.region", () -> AWS_REGION);
        registry.add("ddd4j.mq.sqs.queue-url", () -> queueUrl);
    }

    @Override
    protected void verifyBrokerClient() {
        assertNotNull(amazonSqs);
    }

    @Override
    protected String smokeTopic() {
        // SQS 冒烟 topic 即预创建队列的 queue URL
        return queueUrl;
    }

    /**
     * 覆盖默认 AmazonSQS，将客户端指向 ElasticMQ 端点。
     */
    @Configuration(proxyBeanMethods = false)
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
}
